package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceConfigurationError;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.ExpressionNode;
import org.hl7.fhir.r4.model.PrimitiveType;
import org.hl7.fhir.r4.model.Type;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.fhirpath.IFhirPath;
import ca.uhn.fhir.fhirpath.IFhirPath.IParsedExpression;
import gov.cdc.izgw.v2tofhir.utils.TextUtils;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

/**
 * A class to hold HL7 Message test data stored in an external resource (file or stream).
 * 
 * @author Audacious Inquiry
 *
 */
@Slf4j
@Data
public class TestData {
	/** Expression returned for an empty string */
	public static final IParsedExpression NO_EXPR = new IParsedExpression() {};

	private static final IFhirPath engine = FhirContext.forR4().newFhirPath();
	
	/** The test message or segment */
	private String testData = null;
	/** Assertions about the conversion performed on the test message or segment
	 * written as a FhirPath expression.
	 */
	private final List<String> assertions = new ArrayList<>();
	private final List<IParsedExpression> expressions = new ArrayList<>();
	private final List<String> names = new ArrayList<>();
	/** 
	 * Convert to a string
	 * <pre>
	 * testData
	 * \@[
	 *  assertion
	 *  assertion
	 * ]
	 * </pre>
	 * @return this as a string. 
	 */
	public String toString() {
		StringBuilder b = new StringBuilder();
		b.append(testData).append('\n').append("@[\n");
		assertions.forEach(s -> b.append(' ').append(s).append("\n"));
		b.append("]\n");
		return b.toString();
	}
	
	/**
	 * Determine if the TestData object is empty.
	 * 
	 * @return true if this object has no data or assertions.
	 */
	public boolean isEmpty() {
		return (testData == null || testData.length() == 0) && assertions.isEmpty();
	}
	
	/**
	 * 
	 * Compile and store the expression associated with testData, or return any existing
	 * compiled expression.
	 * 
	 * @param position	The position of the assertion in test data
	 * @return	An expression that can be evaluated.
	 * @throws Exception If an exception occurs during the parse.
	 */
	public IParsedExpression getExpression(int position) throws Exception {
		String path = getAdjustedFhirPath(position);
		List<IParsedExpression> expressions = getExpressions();
		IParsedExpression expr = expressions.size() <= position ? null : expressions.get(position);
		if (expr == null) {
			if (StringUtils.isEmpty(path)) {
				// Skip empty assertions.
				expr = NO_EXPR;
			}
			expr = engine.parse(path);  // can throw any exception
			while (expressions.size() <= position) {
				expressions.add(null);
			}
			getExpressions().set(position, expr);
		}
		return expr;
	}
	
	/**
	 * Get the comment for an assertion.
	 * @param position The position of the assertion
	 * @return	The comment associated with the assertion
	 */
	public String getComment(int position) {
		String assertion = getAssertions().get(position);
		String comment = StringUtils.substringAfterLast(assertion, "// ").trim();
		return StringUtils.isEmpty(comment) ? assertion : comment;
	}
	
	/**
	 * Get the name for the assertion at the given position
	 * 
	 * Assertions are named based on the current segment name and id (e.g., ERR|1, MSH)
	 * and have the 1-based assertion following it.  Thus OBX|23@3 is the 3rd assertion
	 * after ERR|23.
	 * 
	 * @param position The position
	 * @return	The name of the assertion
	 */
	public String getName(int position) {
		return getNames().get(position);
	}
	
	/**
	 * Get the FHIRPath expression for an assertion.
	 * @param position The position of the assertion
	 * @return	The FHIRPath associated with the assertion
	 */
	public String getFhirPath(int position) {
		return StringUtils.substringBeforeLast(getAssertions().get(position), "// ").trim();
	}
	/**
	 * Rewrites FhirPath assertions to exact expression to evaluation from shortened forms.
	 * 
	 * Assertions in test files can be written using simplified FhirPath expressions when 
	 * the first component represents either the Bundle or a Resource in the bundle.
	 * 
	 * Thus: @MessageHeader is the same as @%context.entry.resource.ofType(MessageHeader) saving
	 * about 30 unnecessary characters.
	 * 
	 * @param position	The assertion to adjust
	 * @return	The adjusted FHIRPath
	 */
	public String getAdjustedFhirPath(int position) {
		String fhirPath = getFhirPath(position);

		// No shortcut needed.
		if (fhirPath.startsWith("%")) {
			return fhirPath;
		}
		
		// Simplify expressions by creating shortcuts for
		// commonly used expressions.
		
		String resourceName = StringUtils.substringBefore(fhirPath, ".");
		String rest = null;
		if (resourceName.contains("[")) {
			// Handle cases of OperationOutcome[1], etc.
			resourceName = StringUtils.substringBefore(resourceName, "[");
			rest = "[" + StringUtils.substringAfter(fhirPath, "[");
		} else {
			rest = "." + StringUtils.substringAfter(fhirPath, ".");
		}
		if ("Bundle".equals(resourceName)) {
			return "%context" + rest; 
		}
		return "%context.entry.resource.ofType(" + resourceName + ")" + rest;
	}
	
	/**
	 * Evaluate an assertion against a particular context
	 * @param b	The context object
	 * @param position	The expression to evaluate
	 * @throws Exception	If an exception occurs during parsing or evaluation
	 */
	public void evaluate(IBase b, int position) throws Exception {
		String comment = getComment(position);
		IParsedExpression expr = getExpression(position);
		if (expr == NO_EXPR) {
			return;
		}
		List<IBase> result = engine.evaluate(b, expr, IBase.class);
		// log.info("Value: {} = {}", this.getInnerAsString(expr), result)
		String testValue = " (" + getTestValue(b, position) + ")";
		assertNotNull(result, comment + testValue);
		assertFalse(result.isEmpty(), comment + testValue);
		if (result.size() == 1) {
			IBase base = result.get(0);
			assertFalse(base.isEmpty());
			if (base instanceof BooleanType bool) {
				if (!bool.getValue()) {
					testValue = getTestValue(b, position);
				}
				assertTrue(bool.getValue(), comment + testValue);
			} else if (base instanceof PrimitiveType<?> primitive) {
				String str = primitive.asStringValue();
				assertNotNull(str, comment);
				assertTrue(!str.isEmpty(), comment);
			}
		} else {
			// Should certainly be true.
			assertTrue(result.size() > 1);
		}
	}

	/**
	 * If a FhirPath expression is of BooleanType, then the last 
	 * part of the expression is of the form X eqop value. Get that
	 * part of the expression.
	 * 
	 * @param fhirPath
	 * @return
	 * @throws Exception 
	 */
	private String getTestValue(IBase base, int position) throws Exception {
		String fhirPath = getAdjustedFhirPath(position);
		String inner = null;
		if (fhirPath.contains(".exists(")) {
			inner = StringUtils.substringBeforeLast(fhirPath, ".exists(");
		} else {
			inner = getInnerAsString(getExpression(position));
		}
		if (inner.endsWith(".count()")) {
			inner = StringUtils.left(inner, inner.length()-8);
		}
		List<IBase> l = engine.evaluate(base, inner, IBase.class);
		StringBuilder b = new StringBuilder();
		if (l == null) {
			b.append("null");
		} else if (l.isEmpty()) {
			b.append("<empty>");
		} else if (l.size() > 0) {
		    if (l.size() > 1) {
		    	b.append("[");
		    }
			for (IBase item: l) {
				if (item instanceof PrimitiveType<?> pt) {
					b.append(pt.asStringValue()).append(", ");
				} else if (item instanceof Type t) {
					b.append(TextUtils.toString(t)).append(", ");
				} else {
					b.append(item.fhirType()).append("[").append(item).append("], ");
				}
			}
			b.setLength(b.length()-2);
			if (l.size() > 1) {
				b.append("]");
			}
		}
		String value = b.toString();
		log.info("{} = {}", inner, value);
		return value;
	}

	private String getInnerAsString(IParsedExpression exp) {
		// Using knowledge about FHIRPath internals to construct an expression
		// containing the inner node.
		try {
			Field f = exp.getClass().getDeclaredField("myParsedExpression");
			f.setAccessible(true);
			ExpressionNode node = (ExpressionNode) f.get(exp);
			
			return node.getInner().toString();
		} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException | SecurityException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return "'Cannot determine tested value'";
	}

	/**
	 * Evaluate all assertions against a FHIR resource or datatype
	 * 
	 * NOTE: This fails when the first assertion fails. Use this version
	 * in production testing.
	 * 
	 * @param b	The resource or datatype to test against
	 * @throws AssertionError if any evaluation fails.
	 */
	public void evaluateAgainst(IBase b) {
		int len = getAssertions().size();
		for (int i = 0; i < len; i++) {
			try {
				evaluate(b, i);
			} catch (AssertionError | Exception e) {
				String message = String.format(" Assertion %s (%s) failed: %s", getName(i), getFhirPath(i), e.getMessage());
				throw new AssertionError(message, e);
			}
		}
	}
	
	/**
	 * Evaluate all assertions against a FHIR resource or datatype
	 * This fails when any assertion fails, and returns the cause of the first failure.
	 * The message reports the 1-based index of the failing assertions.
	 * Use this version in development testing.
	 * @param b	The resource or datatype to test against
	 * @throws AssertionError if any evaluation fails.
	 */
	public void evaluateAllAgainst(IBase b) {
		int len = getAssertions().size();
		Throwable cause = null;
		StringBuilder message = new StringBuilder();
		String name = null; 
		for (int i = 0; i < len; i++) {
			try {
				name = getName(i);
				evaluate(b, i);
			} catch (AssertionError | Exception e) {
				message.append(String.format("Assertion %s failed: %s%n", name, e.getMessage()));
				if (cause == null) {
					cause = e;
				}
			}
		}
		if (cause != null) {
			String msg = message.toString();
			log.error("\n" + msg);
			throw new AssertionError("\n" + msg, cause);
		}
	}
	
	static List<TestData> load(String name, boolean isMessageFile) {
		TestData data = new TestData();
		List<TestData> list = new ArrayList<>();
		try (
			ContinuationReader br = 
				new ContinuationReader(new InputStreamReader(getResource(name), StandardCharsets.UTF_8));
		) {
			StringBuilder b = new StringBuilder();
			String line = null;
			String segmentName = null;
			int assertionNumber = 0;
			while (true) {
				line = br.readLine();
				if (line == null || line.isEmpty()) {
					if (isMessageFile && !b.isEmpty()) { // There's a message waiting
						String message = b.toString();
						b.setLength(0);
						data.setTestData(message);	// Add the message.
						if (!message.startsWith("MSH|")) {
							log.error("{}({}) is not a valid message: {}", name, br.getLine(), StringUtils.left(message, 40));
						}
					}
					
					if (!data.isEmpty()) {
						// If there is test data waiting, add it to the list.
						list.add(data);
						data = new TestData();	// and create a new one to capture waiting data.
					}

					// Reached the end of the data
					if (line == null) {
						return list;
					} else {
						continue;
					}
				}
				
				if (line.startsWith("//") || line.startsWith("#")) {
					continue;	// Ignore comment lines
				}
				
				if (line.startsWith("@")) {
					// Found an assertion line
					assertionNumber++;
					data.getAssertions().add(line.substring(1));
					data.getNames().add(segmentName + "@" + assertionNumber);
					continue;
				} 
				
				if (line.matches("^[A-Z123]{3}\\|.*$")) {
					// It's a segment, it will be like ERR|1| ... if the segment is repeatable
					// so we get everything before the second bar for the name.
					segmentName = getSegmentName(line);
					assertionNumber = 0; // reset assertion numbering.
					if (isMessageFile) {
						b.append(line).append("\r");
					} else if (!data.isEmpty()) {
						// It's a new segment, add any existing test data to the list
						data.setTestData(line + "\r");
						list.add(data);
						data = new TestData();
					} else {
						data.setTestData(line);
					}
				} else {
					log.error("{}({}) is not a valid segment or assertion: {}", name, br.getLine(), line);
				}
			}
		} catch (Exception ioex) {
			log.error("Error loading test file " + name, ioex);
			ioex.printStackTrace();
			throw new ServiceConfigurationError("Cannot load test file " + name);
		}
	}

	private static String getSegmentName(String line) {
		if (StringUtils.startsWithAny(line, "MSH", "QPD")) {
			return line.substring(0, 3);
		}
		int pos = line.indexOf('|', 4);
		if (pos < 0) {
			return line.substring(0, 3);
		}
		if (pos == 5) {
			--pos;
		}
		String segmentName = line.substring(0, pos);
		if (segmentName.endsWith("|")) {
			return line.substring(0, 3);
		}
		if (!StringUtils.isNumeric(segmentName.substring(4))) {
			return line.substring(0, 3);
		}
		return segmentName;
	}

	private static InputStream getResource(String name) throws IOException {
		InputStream s = TestBase.class.getClassLoader().getResourceAsStream(name);
		if (s == null) {
			throw new IOException("Cannot find " + name);
		}
		return s;
	}

}