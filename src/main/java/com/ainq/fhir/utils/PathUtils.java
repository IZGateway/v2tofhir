package com.ainq.fhir.utils;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
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
 * extension('<extension-url>') can be written without the full url when the extension is
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
	 * @param path
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
	 */
	private static final Pattern VARIES_PATTERN = Pattern.compile(
		"^.*(" + StringUtils.joinWith("|", (Object[])FHIR_TYPES) + ")$"
	);
	
	/**
	 * Get the values at the specified restricted FHIRPath 
	 * @param b		The context for the path
	 * @param path	The path
	 * @param action Which value to get or add
	 * @return	The requested class.
	 * @throws FHIRException when the property does not exist
	 * @throws NumberFormatException when an index is not valid (< 0 or not a valid number)
	 */
	public static IBase get(IBase b, String path, Action action) {
		if (action == null) {
			action = Action.GET_LAST;
		}
		
		// Split a restricted FHIRPath into its constituent parts.
		// FUTURE: Handle cases like extension("http://hl7.org/ ...
		String propertyName = StringUtils.substringBefore(path, ".");
		String rest = StringUtils.substringAfter(path, ".");
		IBase result = null;
		do {
			// Pass a reference to propertyName for functions which need to adjust it.
			String[] theName = { propertyName };
			int index = getIndex(theName);
			
			String extension = getExtension(theName);
			String type = getType(theName);
			
			List<IBase> l = getValues(b, theName[0], extension);
	
			filterListByType(type, l);
			
			Supplier<IBase> adder = () -> Property.makeProperty(b, theName[0]);
			if (theName[0].length() == 0 && type != null) {
				// When type is non-null and the property name is empty
				// we just need to create a new object of the specified type.
				// NOTE: This object won't be attached to ANYTHING b/c we don't
				// know what list to attach it to.
				adder = () -> createElement(b, type);
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
				// Remove items in the list that aren't of the proper type.
				if (!type.equals(it.next().fhirType())) {
					it.remove();
				}
			}
		}
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
		if (index == null) {
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
		} else {
			return null;
		}
		
		// Translate commonly used extension names.
		if (extension.startsWith("us-core")) {
			return US_CORE_EXT_PREFIX + extension;
		} 
		
		if (!extension.startsWith("http:")) {
			return FHIR_EXT_PREFIX + extension;
		}
		return extension;
	}
	
	private static String getType(String[] propertyName) {
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
		} else {
			Matcher m = VARIES_PATTERN.matcher(propertyName[0]);
			if (m.matches()) {
				type = m.group(1);
			}
		}
		if (type != null) {
			propertyName[0] = propertyName[0].substring(0, propertyName[0].length() - type.length());
		}
		return type;
	}

	/** 
	 * Get the type specifier at the end of a property name
	 * @param propertyName	The property name or path 
	 * @return The type specifier at the end of a property name or path
	 */
	public static String getTypeSpecifier(String propertyName) {
		// Some odd ducks need special handling
		if (propertyName.equals("locationCode")) {
			return null;
		}
		for (String ending: FHIR_TYPES) {
			if (propertyName.endsWith(ending)) {
				return ending;
			}
		}
		return null;
	}
	
}
