package gov.cdc.izgw.v2tofhir.segment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ServiceConfigurationError;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseResource;

import com.ainq.fhir.utils.PathUtils;
import com.ainq.fhir.utils.Property;

import ca.uhn.hl7v2.model.Composite;
import ca.uhn.hl7v2.model.DataTypeException;
import ca.uhn.hl7v2.model.Segment;
import ca.uhn.hl7v2.model.Type;
import ca.uhn.hl7v2.model.v251.datatype.ST;
import gov.cdc.izgw.v2tofhir.annotation.ComesFrom;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.DatatypeConverter;
import gov.cdc.izgw.v2tofhir.utils.ParserUtils;
import lombok.extern.slf4j.Slf4j;

/**
 * A handler for processing field conversions in a Segment of a V2 message.
 *
 * @author Audacious Inquiry
 */
@Slf4j
public class FieldHandler implements Comparable<FieldHandler> {
	/** Set VERIFY_METHODS to true to check method initialization */
	private static final boolean VERIFY_METHODS = false;
	/** Set GET_PROPERTY to true to get the property during initialization */
	private static final boolean GET_PROPERTY = false;
	private final ComesFrom from;
	private final Produces produces;
	private final Method method;
	@SuppressWarnings("unused")
	private final Property prop;
	private final Class<? extends IBase> theClass;
	private final Class<? extends Type> theType;
	/**
	 * @param method	The method to which the ComesFrom annotation applies.
	 * @param from		The annotation
	 * @param p	The class for the parser
	 */
	public FieldHandler(Method method, ComesFrom from, AbstractStructureParser p) {
		this.method = method;
		this.from = from;
		this.produces = p.getProduces();
		
		if (GET_PROPERTY) {
			/**
			 * This still has a few small problems to fix on more complex
			 * paths using [] and resolve() features.
			 */
			try {
				prop = PathUtils.getProperty(
					getResourceClass(produces, from.path(), p), 
					from.path()
				);
			} catch (IllegalArgumentException e) {
				log.error(e.getMessage(), e);
				throw new ServiceConfigurationError(e.getMessage(), e);
			}
		} else {
			prop = null;
		}
		
		Class<?>[] params = method.getParameterTypes();

		if (IBase.class.isAssignableFrom(params[0])) {
			@SuppressWarnings("unchecked")
			Class<? extends IBase> param = (Class<? extends IBase>) params[0];
			theClass = param;
			theType = null;
		} else if (Type.class.isAssignableFrom(params[0])) {
			@SuppressWarnings("unchecked")
			// Direct handling of type in Parser (e.g., for OBX Segments where Type of OBX-5 is not known
			// until OBX-2 is parsed.
			Class<? extends Type> param = (Class<? extends Type>) params[0];
			theClass = null;
			theType = param;
		} else {
			throw new ServiceConfigurationError("Method does not accept a FHIR type: " + method);
		}
		
		// Verify setValue works at initialization on a dummy
		if (VERIFY_METHODS) {
			IBase dummyObject = null;
			try {
				dummyObject = theClass.getDeclaredConstructor().newInstance();
				// Test the method call
				setValue(p, dummyObject);
			} catch (InstantiationException | IllegalAccessException | IllegalArgumentException
					| InvocationTargetException | NoSuchMethodException | SecurityException e) {
				throw new ServiceConfigurationError("Cannot create " + theClass.getSimpleName() + " for " + method);
			}
		}
	}

	private Class<? extends IBaseResource> getResourceClass(Produces produces, String path, StructureParser p) {
		Class<? extends IBaseResource> resourceClass = produces.resource();
		String resourceName = StringUtils.substringBefore(path, ".");
		// remove any array index
		resourceName = resourceName.contains("[") ? StringUtils.substringBefore(resourceName, "[") : resourceName; 
		if (!resourceClass.getSimpleName().equals(resourceName)) {
			for (Class<? extends IBaseResource> extraClass: produces.extra()) {
				if (extraClass.getSimpleName().equals(resourceName)) {
					resourceClass = extraClass;
					break;
				}
			}
			if (resourceClass == produces.resource()) {
				throw new IllegalArgumentException(
					"Resource " + resourceName + " is not in @Produces.resource or @Produces.extra for " + p.getClass().getSimpleName()
				);
			}
		}
		return resourceClass;
	}

	/**
	 * Convert fields within a segment to components in a resource
	 * 
	 * @param p	The structure parser to do the handling for
	 * @param segment	The segment to process
	 * @param r	The resource to update
	 */
	public void handle(StructureParser p, Segment segment, IBaseResource r) {
		if (from.fixed().length() != 0) {
			setFixedValue(p);
		} else if (from.field() != 0) {
			setFromFieldAndComponent(p, segment);
		} else {
			// we have neither a fixed value nor a field and component,
			// this is a configuration error.
			log.error("ComesFrom missing fixed or field value");
		}
	}

	private void setFixedValue(StructureParser p) {
		ST st = new ST(null);
		try {
			st.setValue(from.fixed());
			IBase value = DatatypeConverter.convert(theClass, st, from.table()); 
			setValue(p, value);
		} catch (DataTypeException e) {
			// This will never happen.
		}
	}

	private void setFromFieldAndComponent(StructureParser p, Segment segment) {
		Type[] f = ParserUtils.getFields(segment, from.field());
		if (f.length == 0) {
			return;
		}
		for (Type t: f) {
			t = DatatypeConverter.adjustIfVaries(t);
			if (t instanceof Composite comp && from.component() != 0) {
				// Check for component
				Type[] t2 = comp.getComponents();
				if (from.component() > t2.length) {
					continue;
				}
				t = t2[from.component()-1];
			} else if (from.component() > 1) {
				// A component > 1 does not exist in a primitive
				continue;
			}
			if (t == null) {
				continue;
			}
			if (theClass != null) {
				IBase value = DatatypeConverter.convert(theClass, t, from.table());
				if (value != null) {
					setValue(p, value);
				}
			} else if (theType != null) {
				// Let the parser handle the type conversion
				setValue(p, t);
			}
		}
	}

	private IBase setValue(StructureParser p, IBase object) {
		try {
			method.invoke(p, object);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			// This is checked during initialization, so failure happens early if it is going to
			// happen at all, so it's OK to throw an error here.
			log.error("Cannot invoke {}: {}", method, e.getMessage(), e);
			throw new ServiceConfigurationError("Cannot invoke " + method, e);
		}
		return object;
	}
	
	private Type setValue(StructureParser p, Type object) {
		try {
			method.invoke(p, object);
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			// This is checked during initialization, so failure happens early if it is going to
			// happen at all, so it's OK to throw an error here.
			log.error("Cannot invoke {}: {}", method, e.getMessage(), e);
			throw new ServiceConfigurationError("Cannot invoke " + method, e);
		}
		return object;
	}
	
	public String toString() {
		return toString(from) + method.toString() ;
	}
	
	private String toString(ComesFrom from) {
		StringBuilder b = new StringBuilder();
		b.append("\n@ComesFrom(path = \"").append(from.path()).append("\"");
		appendField(b, "field", from.field());
		appendField(b, "component", from.component());
		appendField(b, "fixed", from.fixed());
		appendField(b, "table", from.table());
		appendField(b, "map", from.map());
		appendField(b, "comment", from.comment());
		appendField(b, "also", from.also());
		appendField(b, "priority", from.priority());
		b.append(")\n");
		return b.toString();
	}

	private void appendField(StringBuilder b, String name, String[] also) {
		if (also.length == 0) return;
		b.append(", ").append(name).append(" = { ");
		for (String v: also) {
			b.append("\"").append(v).append("\", ");
		}
		b.setCharAt(b.length()-1, '}');
		b.setCharAt(b.length()-2, ' ');
	}

	private void appendField(StringBuilder b, String name, int value) {
		if (value <= 0) return;
		b.append(", ").append(name).append(" = ").append(value);
	}

	private void appendField(StringBuilder b, String name, String string) {
		if (StringUtils.isBlank(string)) {
			return;
		}
		b.append(", ").append(name).append(" = \"").append(string).append("\"");
	}
	
	/**
	 * @param fh1	The first field handler to compare
	 * @param fh2	The second field handler to compare
	 * @return &lt; 0 if fh1 &lt; fh2, 0 if equal, &gt; 0 if fh1 &gt; fh2
	 */
	public static int compare(FieldHandler fh1, FieldHandler fh2) {
		if (fh1 == fh2)
			return 0;
		if (fh1 == null) 
			return -1;
		if (fh2 == null)
			return 1;
		// Priority comparison is reversed
		int comp = -Integer.compare(fh1.from.priority(), fh2.from.priority());
		if (comp != 0) 
			return comp;
		comp = Integer.compare(fh1.from.field(), fh2.from.field());
		if (comp != 0) 
			return comp;
		comp = Integer.compare(fh1.from.component(), fh2.from.component());
		if (comp != 0) 
			return comp;
		comp = fh1.toString().compareTo(fh2.toString());
		if (comp != 0) 
			return comp;
		return fh1.method.toString().compareTo(fh2.toString());
	}

	@Override
	public int compareTo(FieldHandler that) {
		return compare(this, that);
	}

	@Override
	public boolean equals(Object obj) {
		if (obj instanceof FieldHandler that) {
			return compareTo(that) == 0;
		}
		return false;
	}

	@Override
	public int hashCode() {
		return from.hashCode() ^ method.hashCode();
	}
}