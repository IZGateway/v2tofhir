package com.ainq.fhir.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceConfigurationError;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseExtension;
import org.hl7.fhir.instance.model.api.IBaseHasExtensions;
import lombok.extern.slf4j.Slf4j;

/**
 * Parse a path from a FHIR Object and return the object.
 * 
 * Paths in this class use the Restricted FHIRPath syntax with a few minor simplifications:
 * 
 * extension('extension-url') can be written without the full url when the extension is
 * a FHIR standard extension (comes from  http://hl7.org/fhir/StructureDefinition/), or from
 * FHIR US Core (http://hl7.org/fhir/us/core/StructureDefinition/). Also, the quotes in the 
 * extension name aren't necessary.
 * Also, one can use the extension short name directly:
 * 	Patient.us-core-race works as a path expression.
 *  
 * @author Audacious Inquiry
 * @see <a href="https://hl7.org/fhir/r4/fhirpath.html#simple">FHIRPath Restricted Subset</a>
 */
@Slf4j
public class PathUtils {
	/** The extension prefix for standard FHIR extensions */
	public static final String FHIR_EXT_PREFIX = "http://hl7.org/fhir/StructureDefinition/";
	/** The extension prefix for standard US Core extensions */
	public static final String US_CORE_EXT_PREFIX = "http://hl7.org/fhir/us/core/StructureDefinition/";

	private PathUtils() {
		log.debug("{} loaded", PathUtils.class.getName());
	}
	/**
	 * Controls behavior for get method, enabling new objects to be created.
	 */
	public enum Action {
		/** Get any existing property at the path, don't create intermediates */
		GET_FIRST,
		/** Get the last property at the path */
		GET_LAST,
		/** Create the property at the path if it doesn't exist (implies GET_LAST at intermediate stages) */
		CREATE_IF_MISSING
	}
	
	/**
	 * Get the values at the specified restricted FHIRPath 
	 * @param b	The base object to get the 
	 * @param path The FHIRPath
	 * @return	The values
	 */
	public static  IBase get(IBase b, String path) {
		return get(b, path, Action.GET_LAST);
	}
	
	static final String[] FHIR_TYPES = {			
			/* Primitives */
			"Boolean", "Code", "Id", "Instant", "Integer", "Date", "DateTime", "Decimal", 
			"PositiveInt", "String", "Time", "UnsignedInt", "Uri", "Url",
			/* General Purpose */
			"Address", "Age", "Annotation", "Attachment", "Coding", "CodeableConcept", "ContactPoint",
			"Count", "Distance", "Duration", "Identifier", "Money", "MoneyQuantity", "Period", 
			"Quantity", "Range", "Ratio", "RatioRange", "Signature", "SimpleQuantity", "Timing",
			/* Special Cases */
			"Expression", "Reference"
		};

	/** 
	 * Endings on a property name that refine it to a specific type.  These names
	 * are generally the name of a FHIR Type.
	 * 
	 * NOTE: .*? must use a reluctant qualifier to find the longest value for
	 * type because DateTime ends with Time
	 */
	private static final Pattern VARIES_PATTERN = Pattern.compile(
		"^.*?(" + StringUtils.joinWith("|", (Object[])FHIR_TYPES) + ")$"
	);
	/**
	 * Exceptions to the above pattern.  These are few.  So far only
	 * the following are false matches: 
	 * Patient.birthDate, 
	 * Immunization.doseQuantity,
	 * Immunization.expirationDate,
	 * ServiceRequest.locationCode,
	 * Immunization.reasonCode 
	 * Observation.referenceRange, 
	 * Immunization.vaccineCode 
	 */
	private static final Set<String> VARIES_EXCEPTIONS = 
		new LinkedHashSet<>(Arrays.asList(
			"birthDate", "doseQuantity", "expirationDate",
			"locationCode", "reasonCode", "referenceRange", "vaccineCode")
		);
	/**
	 * Get all elements at the specified path.
	 * @param b	The base
	 * @param path	The path
	 * @return	The list of elements at that location.
	 */
	public static List<IBase> getAll(IBase b, String path) {
		
		List<IBase> l = null;
		
		// Split a restricted FHIRPath into its constituent parts.
		// FUTURE: Handle cases like extension("http://hl7.org/ ...
		String propertyName = StringUtils.substringBefore(path, ".");
		String rest = StringUtils.substringAfter(path, ".");
		do {
			// Pass a reference to propertyName for functions which need to adjust it.
			String[] theName = { propertyName };
			int index = getIndex(theName);
			
			String extension = getExtension(theName);
			String type = getType(b, theName);
			
			try {
				l = getValues(b, theName[0], extension);
			} catch (Exception ex) {
				log.error("{} extracting {}.{}",
					ex.getClass().getSimpleName(),
					b.fhirType(),
					theName[0] + (StringUtils.isNotEmpty(extension) ? "." + extension : ""),
					ex
				);
				throw ex;
			}
			filterListByType(type, l);
			
			adjustListForIndex(l, index, null);
			
			propertyName = StringUtils.substringBefore(rest, ".");
			rest = StringUtils.substringAfter(rest, ".");
			
			if (l.isEmpty()) {
				return Collections.emptyList();
			}
			b = l.get(l.size() - 1); 
		} while (StringUtils.isNotEmpty(propertyName));
		
		return l;
	}
	
	/**
	 * Get the values at the specified restricted FHIRPath 
	 * @param b		The context for the path
	 * @param path	The path
	 * @param action Which value to get or add
	 * @return	The requested class.
	 * @throws FHIRException when the property does not exist
	 * @throws NumberFormatException when an index is not valid (&lt; 0 or not a valid number)
	 */
	public static IBase get(IBase b, String path, Action action) {
		if (action == null) {
			action = Action.GET_LAST;
		}
		
		// Split a restricted FHIRPath into its constituent parts.
		// FUTURE: Handle cases like extension("http://hl7.org/ ...
		String propertyName = StringUtils.substringBefore(path, ".");
		String rest = StringUtils.substringAfter(path, ".");
		IBase result = b;
		do {
			// Pass a reference to propertyName for functions which need to adjust it.
			String[] theName = { propertyName };
			int index = getIndex(theName);
			
			String extension = getExtension(theName);
			String type = getType(b, theName);
			
			List<IBase> l = getValues(result, theName[0], extension);
	
			filterListByType(type, l);
			
			IBase result2 = result;
			Supplier<IBase> adder = () -> Property.makeProperty(result2, theName[0]);
			if (theName[0].length() == 0 && type != null) {
				// When type is non-null and the property name is empty
				// we just need to create a new object of the specified type.
				// NOTE: This object won't be attached to ANYTHING b/c we don't
				// know what list to attach it to.
				adder = () -> createElement(result2, type);
			}
				
			adjustListForIndex(l, index, 
					Action.CREATE_IF_MISSING.equals(action) ? adder : null
				);
			propertyName = StringUtils.substringBefore(rest, ".");
			rest = StringUtils.substringAfter(rest, ".");
			
			if (l.isEmpty()) {
				return null;
			}
			if (Action.CREATE_IF_MISSING.equals(action) || Action.GET_FIRST.equals(action)) {
				result = l.get(0);
			} else { // Action == GET_LAST
				result = l.get(l.size() - 1); 
			}
		} while (StringUtils.isNotEmpty(propertyName));
		
		return result;
	}

	private static IBase createElement(IBase b, String type) {
		@SuppressWarnings("unchecked")
		Class<IBase> clazz = (Class<IBase>) b.getClass();
		String theClass = clazz.getPackageName() + "." + type;
		try {
			@SuppressWarnings("unchecked")
			Class<IBase> class2 = (Class<IBase>)clazz.getClassLoader().loadClass(theClass);
			return class2.getDeclaredConstructor().newInstance();
		} catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
				| NoSuchMethodException | SecurityException e) {
			String msg = "Cannot create " + theClass;
			throw new ServiceConfigurationError(msg, e);
		} 
	}

	private static List<IBase> getValues(IBase b, String propertyName, String extension) {
		List<IBase> l = new ArrayList<>();
		if (extension != null && b instanceof IBaseHasExtensions elem) {
			for (IBaseExtension<?, ?> ext : elem.getExtension()) {
				if (extension.equals(ext.getUrl())) {
					l.add(ext);
				}
			}
		} else if (propertyName.length() != 0) {
			l = Property.getValues(b, propertyName);
		} else {
			// If propertName.length is zero, it's a type match on b
			l.add(b);
		}
		return l;
	}

	private static IBase adjustListForIndex(List<IBase> l, int index, Supplier<IBase> adder) {
		IBase result = null;
		if (index < 0) {
			// There was no index
			if (adder != null && l.isEmpty()) {
				// a value should be created, but there are none, so create one
				result = adder.get();
				l.add(result);
				return result;
			}
			return null;
		}


		// Item exists
		if (index < l.size()) {
			// Get the index-th element.
			result = l.get(index);
		} 
		
		if (adder != null) {
			// Create the index-th element.
			while (index >= l.size()) {
				result = adder.get();
				l.add(result);
			}
		}
		
		// Adjust the list to contain ONLY the indexed item
		l.clear();
		if (result != null) {
			l.add(result);
		}
		return result;
	}

	private static void filterListByType(String type, List<IBase> l) {
		if (type != null) {
			Iterator<IBase> it = l.iterator();
			while (it.hasNext()) {
				IBase b = it.next();
				if (!isOfType(b, type)) {
					it.remove();
				}
			}
		}
	}
	
	private static List<String> bases = Arrays.asList("DomainResource", "Resource", "Base", "Object");
	private static boolean isOfType(IBase b, String type) {
		Class<?> theClass = b.getClass();
		do {
			String className = theClass.getSimpleName();
			if (className.equals(type) || className.equals(type + "Type")) {
				return true;
			}
			theClass = theClass.getSuperclass();
		} while (!bases.contains(theClass.getSimpleName()));

		return false;
	}

	/**
	 * Get information about a Property of a FHIR resource or element.
	 * @param clazz	The class of the resource or element to get a property for.
	 * @param originalPath	The path to the property
	 * @return	The property
	 */
	public static Property getProperty(Class<? extends IBase> clazz, String originalPath) {
		String path = originalPath;
		String propertyName = StringUtils.substringAfterLast(originalPath, ".");
		if (propertyName == null || propertyName.length() == 0) {
			throw new IllegalArgumentException("Path invalid " + originalPath);
		}
		path = path.substring(0, path.length() - propertyName.length() - 1);
		IBase r = null;
		IBase base = null;
		try {
			r = clazz.getDeclaredConstructor().newInstance();
			base = get(r, path, Action.CREATE_IF_MISSING);
			return Property.getProperty(base, propertyName);
		} catch (Exception e) {
			throw new IllegalArgumentException("Error accessing propery " + originalPath, e);
		}
	}
	
	private static int getIndex(String[] propertyName) {
		String index = StringUtils.substringBetween(propertyName[0], "[", "]");
		// Handle values with no index and special cases like value[x] 
		if (index == null || "x".equals(index)) {
			return -1;
		}
		propertyName[0] = StringUtils.substringBefore(propertyName[0], "[");
		
		if ("$last".equals(index)) {
			return Integer.MAX_VALUE;
		}
		
		int value = Integer.parseInt(index);
		if (value < 0) {
			throw new IndexOutOfBoundsException(value + " is not a legal index value in " + propertyName);
		}
		return value;
	}
	
	private static String getExtension(String[] propertyName) {
		String extension = null;
		if (propertyName[0].startsWith("extension(")) {
			extension = StringUtils.substringBetween(propertyName[0], "(", ")");
			extension = StringUtils.replaceChars(extension, "\"'", "");
			if (extension == null) {
				throw new FHIRException("Missing extension name: " + propertyName[0]);
			} else if (StringUtils.substringAfter(extension, ")").length() > 0) {
				throw new FHIRException("Invalid extension function: " + propertyName[0]);
			}
			
		} else if (propertyName[0].contains("-")) {
			// save extension name
			extension = propertyName[0];
			// Translate commonly used extension names.
			if (extension.startsWith("us-core")) {
				return US_CORE_EXT_PREFIX + extension;
			} 
			
			if (!extension.startsWith("http:")) {
				return FHIR_EXT_PREFIX + extension;
			}
		} else {
			return null;
		}
		
		return extension;
	}
	
	/**
	 * Get the type associated with the specified property.  
	 * This is used to adjust property names by removing terminal type specifiers 
	 * to correct names like eventCoding (which is actually named event[x] in 
	 * MessageHeader).  The challenge is that some property names (e.g., Patient.birthDate)
	 * also end in a type name, but don't follow that pattern.
	 * 
	 * @param b	The base fhir object having the property
	 * @param propertyName	The name of the property.
	 * @return
	 */
	static String getType(IBase b, String[] propertyName) {
		// Deal with special cases
		if ("Reference".equals(b.fhirType()) && Character.isUpperCase(propertyName[0].charAt(0))) {
			// allow shortcut for Reference.TypeName
			return null;
		}
		
		String type = null;
		if (Character.isUpperCase(propertyName[0].charAt(0))) {
			// A property name such as Bundle, IntegerType, etc, is assumed to be a type.
			type = propertyName[0];
			propertyName[0] = "";
			return type;
		} 
		if (propertyName[0].startsWith("ofType(")) {
			type = StringUtils.substringBetween("ofType(", ")");
			if (StringUtils.substringAfter(propertyName[0], ")").length() != 0 || type == null) {
				throw new FHIRException("Invalid ofType call: " + propertyName);
			}
			propertyName[0] = propertyName[0].substring(0, propertyName[0].length() - type.length());
		} else if (VARIES_EXCEPTIONS.contains(propertyName[0])) {
			// This is an exception to the rule for property names ending
			// in a type.
			return null;
		} else {
			Matcher m = VARIES_PATTERN.matcher(propertyName[0]);
			if (m.matches()) {
				type = m.group(1);
				propertyName[0] = 
					StringUtils.left(
						propertyName[0], propertyName[0].length() - type.length()
					) + "[x]";
			}
		}
		return type;
	}
}
