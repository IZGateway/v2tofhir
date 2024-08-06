package com.ainq.fhir.utils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.ServiceConfigurationError;

import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.dstu2.model.Patient;
import org.hl7.fhir.exceptions.FHIRException;
import org.hl7.fhir.instance.model.api.IBase;
import org.hl7.fhir.instance.model.api.IBaseExtension;
import org.hl7.fhir.instance.model.api.IBaseReference;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Element;
import org.hl7.fhir.r4.model.MessageHeader;
import org.hl7.fhir.r4.model.Parameters;
import org.hl7.fhir.r4.model.Parameters.ParametersParameterComponent;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Type;

import ca.uhn.fhir.util.ExtensionUtil;
import lombok.extern.slf4j.Slf4j;

/**
 * Property is a facade over the various version specific model instances
 * of Property.
 */
@Slf4j
public class Property {
	/**
	 * Get the named property from the object.
	 * @param b	The Element to get values from
	 * @param propertyName	The name of property to get the values from.
	 * @return A list of values on the element.
	 * @throws FHIRException When a FHIRException occurs in the HAPI framework
	 */
	public static Property getProperty(IBase b, String propertyName) throws FHIRException { // NOSONAR ? intentional
		if ("resolve()".equals(propertyName)) {
			if (b instanceof IBaseReference ref) {
				IBaseResource res = (IBaseResource)ref.getUserData("Resource");
				return res == null ? null : new Property(res);
			} else {
				throw new IllegalArgumentException("Cannot resolve() a " + b.getClass().getSimpleName());
			}
		} else if (Character.isUpperCase(propertyName.charAt(0)) && b instanceof IBaseReference ref) {
			// Using XXX.reference.ResourceName is short-hand for resolve().ResourceName
			// when b is a reference.
			IBaseResource res = (IBaseResource)ref.getUserData("Resource");
			return (res != null && res.fhirType().equals(propertyName)) ? new Property(res) : null;
		}
		if (propertyName.contains("-")) {
			// The actual property is for an extension from us-core
			String extensionName = (propertyName.startsWith("us-core-") ?
					PathUtils.US_CORE_EXT_PREFIX : PathUtils.FHIR_EXT_PREFIX) + propertyName;
			if (ExtensionUtil.hasExtension(b, extensionName)) {
				return new Property(ExtensionUtil.getExtensionByUrl(b, extensionName));
			} else {
				return null;
			}
		}
		
		String[] theName = new String[] { propertyName };
		String type = PathUtils.getType(b, theName );
		propertyName = theName[0];

		switch (b.getClass().getPackageName()) {
		case "org.hl7.fhir.r4.model":
			if (b instanceof Element e) {
				return new Property(e, propertyName, type);
			} else if (b instanceof Resource r) {
				if (b instanceof Parameters params) {
					for (ParametersParameterComponent param : params.getParameter()) {
						if (StringUtils.equals(param.getName(), propertyName)) {
							return new Property(propertyName, param.getValue());
						}
					}
					return null;
				}
				return new Property(r, propertyName, type);
			}
			break;
		case "org.hl7.fhir.r4b.model":
			if (b instanceof org.hl7.fhir.r4b.model.Element e) {
				return new Property(e, propertyName, type);
			} else if (b instanceof org.hl7.fhir.r4b.model.Resource r) {
				return new Property(r, propertyName, type);
			}
			break;
		case "org.hl7.fhir.r5.model":
			if (b instanceof org.hl7.fhir.r5.model.Element e) {
				return new Property(e, propertyName, type);
			} else if (b instanceof org.hl7.fhir.r5.model.Resource r) {
				return new Property(r, propertyName, type);
			}
			break;
		case "org.hl7.fhir.dstu3.model":
			if (b instanceof org.hl7.fhir.dstu3.model.Element e) {
				return new Property(e, propertyName, type);
			} else if (b instanceof org.hl7.fhir.dstu3.model.Resource r) {
				return new Property(r, propertyName, type);
			}
			break;
		case "org.hl7.fhir.dstu2.model":
			if (b instanceof org.hl7.fhir.dstu2.model.Element e) {
				return new Property(e, propertyName, type);
			} else if (b instanceof org.hl7.fhir.dstu2.model.Resource r) {
				return new Property(r, propertyName, type);
			}
			break;
		default:
			throw new FHIRException("Unsupported package " + b.getClass().getPackageName());
		}
		throw new FHIRException("Not an Element");
	}
	
	/**
	 * Get the named property from the object.
	 * 
	 * NOTE: This function is incomplete as it does not call setProperty for singleton 
	 * properties.  However what it does is sufficient for current needs.
	 * 
	 * @param b	The Element to get values from
	 * @param propertyName	The name of property to get the values from.
	 * @return A list of values on the element.
	 * @throws FHIRException When a the HAPI FHIR package is not present 
	 */
	public static IBase makeProperty(IBase b, String propertyName) throws FHIRException { // NOSONAR ? intentional
		switch (b.getClass().getPackageName()) {
		case "org.hl7.fhir.r4.model":
			if (b instanceof Element e) {
				return e.makeProperty(propertyName.hashCode(), propertyName);
			} else if (b instanceof Resource r) {
				return r.makeProperty(propertyName.hashCode(), propertyName);
			}
			break;
		case "org.hl7.fhir.r4b.model":
			if (b instanceof org.hl7.fhir.r4b.model.Element e) {
				return e.makeProperty(propertyName.hashCode(), propertyName);
			} else if (b instanceof org.hl7.fhir.r4b.model.Resource r) {
				return r.makeProperty(propertyName.hashCode(), propertyName);
			}
			break;
		case "org.hl7.fhir.r5.model":
			if (b instanceof org.hl7.fhir.r5.model.Element e) {
				return e.makeProperty(propertyName.hashCode(), propertyName);
			} else if (b instanceof org.hl7.fhir.r4.model.Resource r) {
				return r.makeProperty(propertyName.hashCode(), propertyName);
			}
			break;
		case "org.hl7.fhir.dstu3.model":
			if (b instanceof org.hl7.fhir.dstu3.model.Element e) {
				return e.makeProperty(propertyName.hashCode(), propertyName);
			} else if (b instanceof org.hl7.fhir.dstu3.model.Resource r) {
				return r.makeProperty(propertyName.hashCode(), propertyName);
			}
			break;
		case "org.hl7.fhir.dstu2.model":
			if (b instanceof org.hl7.fhir.dstu2.model.Element e) {
				org.hl7.fhir.dstu2.model.Property p = e.getChildByName(propertyName);
				// This is a ton of ick.
				try {
					if (p.getMaxCardinality() > 1) {
						return e.addChild(propertyName);
					} else {
						e.setProperty(propertyName, null);
						return null;
					}
				} catch (FHIRException ex) {
					// Cannot add the specified child, assume it is a singleton
				}
			} else if (b instanceof org.hl7.fhir.dstu2.model.Resource r) {
				return r.addChild(propertyName);
			}
			break;
		default:
			throw new FHIRException("Unsupported package " + b.getClass().getPackageName());
		}
		throw new FHIRException("Not an Element");
	}
	/**
	 * Get values for a property of a FHIR Element
	 * @param b	The FHIR item to get a property of
	 * @param propertyName	The name of the property
	 * @return	The new property
	 */
	@SuppressWarnings("unchecked")
	public static List<IBase> getValues(IBase b, String propertyName) {
		 Property prop = getProperty(b, propertyName);
		 return prop == null ? Collections.emptyList() : (List<IBase>) prop.getValues();
	}
	private final Object property;
	private final Method getName;
	private final Method getTypeCode;
	private final Method getDefinition;
	private final Method getMinCardinality;
	private final Method getMaxCardinality;
	private final Method getValues;
	private String name;
	private String typeCode;
	private String definition;
	private int minCardinality = -1;
	private int maxCardinality = -1;
	private List<? super IBase> values = null;
	
	private Property(Object property, String type) {
		if (property == null) {
			throw new NullPointerException("Property cannot be null");
		}
		this.property = property;
		Class<?> clazz = property.getClass();
		String method = null;
		try {
			getName = clazz.getDeclaredMethod(method = "getName");  // NOSONAR assignment OK
			getTypeCode = clazz.getDeclaredMethod(method = "getTypeCode");	// NOSONAR assignment OK
			getDefinition = clazz.getDeclaredMethod(method = "getDefinition");	// NOSONAR assignment OK
			getMinCardinality = clazz.getDeclaredMethod(method = "getMinCardinality");	// NOSONAR assignment OK
			getMaxCardinality = clazz.getDeclaredMethod(method = "getMaxCardinality");	// NOSONAR assignment OK
			getValues = clazz.getDeclaredMethod(method = "getValues");	// NOSONAR assignment OK
			if (type != null) {
				typeCode = type;
			}
		} catch (NoSuchMethodException | SecurityException e) {
			String msg = "Cannot access Propery." + method + "() method"; 
			log.error("{} : {}", msg, e.getMessage(), e);
			throw new ServiceConfigurationError(msg, e);
		}
	}
	
	private Property(IBaseExtension<?, ?> extension) {
		property = extension;
		name = StringUtils.substringAfter(extension.getUrl(), "StructureDefinition/");
		typeCode = "Extension";
		definition = "Optional Extension Element - found in all resources.";
		minCardinality = 0;
		maxCardinality = Integer.MAX_VALUE;
		values = Collections.singletonList(extension.getValue());
		getName = null;
		getTypeCode = null;
		getDefinition = null;
		getMinCardinality = null;
		getMaxCardinality = null;
		getValues = null;
	}
	
	/**
	 * Create a Property for a resource
	 * @param res	The resource
	 */
	public Property(IBaseResource res) {
		if (res == null) {
			throw new NullPointerException("res cannot be null");
		}
		property = res;
		name = "resolve()";
		typeCode = res.fhirType();
		definition = res.fhirType();
		minCardinality = 0;
		maxCardinality = Integer.MAX_VALUE;
		values = Collections.singletonList(res);
		getName = null;
		getTypeCode = null;
		getDefinition = null;
		getMinCardinality = null;
		getMaxCardinality = null;
		getValues = null;
	}
	
	/**
	 * Create a property for a known property with the given name.
	 * @param propertyName	The property
	 * @param value	The value of the property
	 */
	public Property(String propertyName, Type value) {
		property = value;
		name = propertyName;
		typeCode = value.fhirType();
		definition = value.fhirType();
		minCardinality = 0;
		maxCardinality = 1;
		values = Collections.singletonList(value);
		getName = null;
		getTypeCode = null;
		getDefinition = null;
		getMinCardinality = null;
		getMaxCardinality = null;
		getValues = null;
	}


	
	/**
	 * Create a new Property accessing an R4 Property
	 * @param property The R4 Property
	 * @param type The specific subtype or null (for properties like Patient.deceased)
	 */
	public Property(org.hl7.fhir.r4.model.Property property, String type) {
		this((Object)property, type);
	}

	/**
	 * Create a new Property accessing an R4b Property
	 * @param property The R4b Property
	 * @param type The specific subtype or null (for properties like Patient.deceased)
	 */
	public Property(org.hl7.fhir.r4b.model.Property property, String type) {
		this((Object)property, type);
	}

	/**
	 * Create a new Property accessing an R5 Property
	 * @param property The R5 Property
	 * @param type The specific subtype or null (for properties like Patient.deceased)
	 */
	public Property(org.hl7.fhir.r5.model.Property property, String type) {
		this((Object)property, type);
	}

	/**
	 * Create a new Property accessing a DSTU3 Property
	 * @param property The DSTU3 Property
	 * @param type The specific subtype or null (for properties like Patient.deceased)
	 */
	public Property(org.hl7.fhir.dstu3.model.Property property, String type) {
		this((Object)property, type);
	}

	/**
	 * Create a new Property accessing a DSTU2 Property
	 * @param property The DSTU2 Property
	 * @param type The specific subtype or null (for properties like Patient.deceased)
	 */
	public Property(org.hl7.fhir.dstu2.model.Property property, String type) {
		this((Object)property, type);
	}
	
	/** 
	 * Create a property of an R4 Resource
	 * @param r	The R4 Resource
	 * @param propertyName The property name
	 * @param type	The type for the property
	 */
	public Property(Resource r, String propertyName, String type) {
		this(getChildByName(r, propertyName), type);
	}

	/** 
	 * Create a property of an R4 Element
	 * @param e	The R4 Element
	 * @param propertyName The property name
	 * @param type	The type for the property
	 */
	public Property(Element e, String propertyName, String type) {
		this(e.getChildByName(propertyName), type);
	}

	private static Object getChildByName(Resource r, String propertyName) {
		if ("eventCoding".equals(propertyName) && r instanceof MessageHeader mh) {
			return new org.hl7.fhir.r4.model.Property(
				propertyName, "Coding", 
				"Code that identifies the event this message represents and connects it with its definition.", 
				0, 1, mh.getEventCoding()); 
		}
		return r.getChildByName(propertyName);
	}

	/** 
	 * Create a property of an R4B Element
	 * @param e	The R4B Element
	 * @param propertyName The property name
	 * @param type	The type for the property
	 */
	public Property(org.hl7.fhir.r4b.model.Element e, String propertyName, String type) {
		this(e.getChildByName(propertyName), type);
	}

	/** 
	 * Create a property of an R4B Resource
	 * @param r	The R4B Resource
	 * @param propertyName The property name
	 * @param type	The type for the property
	 */
	public Property(org.hl7.fhir.r4b.model.Resource r, String propertyName, String type) {
		this(r.getChildByName(propertyName), type);
	}

	/** 
	 * Create a property of an R5 Element
	 * @param e	The R5 Element
	 * @param propertyName The property name
	 * @param type	The type for the property
	 */
	public Property(org.hl7.fhir.r5.model.Element e, String propertyName, String type) {
		this(e.getChildByName(propertyName), type);
	}

	/** 
	 * Create a property of an R5 Resource
	 * @param r	The R5 Resource
	 * @param propertyName The property name
	 * @param type	The type for the property
	 */
	public Property(org.hl7.fhir.r5.model.Resource r, String propertyName, String type) {
		this(r.getChildByName(propertyName), type);
	}

	/** 
	 * Create a property of an STU3 Element
	 * @param e	The STU3 Element
	 * @param propertyName The property name
	 * @param type	The type for the property
	 */
	public Property(org.hl7.fhir.dstu3.model.Element e, String propertyName, String type) {
		this(e.getChildByName(propertyName), type);
	}

	/** 
	 * Create a property of an STU3 Resource
	 * @param r	The STU3 Resource
	 * @param propertyName The property name
	 * @param type	The type for the property
	 */
	public Property(org.hl7.fhir.dstu3.model.Resource r, String propertyName, String type) {
		this(r.getChildByName(propertyName), type);
	}

	/** 
	 * Create a property of an STU2 Element
	 * @param e	The STU2 Element
	 * @param propertyName The property name
	 * @param type	The type for the property
	 */
	public Property(org.hl7.fhir.dstu2.model.Element e, String propertyName, String type) {
		this(e.getChildByName(propertyName), type);
	}

	/** 
	 * Create a property of an STU2 Resource
	 * @param r	The STU2 Resource
	 * @param propertyName The property name
	 * @param type	The type for the property
	 */
	public Property(org.hl7.fhir.dstu2.model.Resource r, String propertyName, String type) {
		this(r.getChildByName(propertyName), type);
	}

	/**
	 * Get the name of the property
	 * @return The name of the property
	 */
	public String getName() {
		try {
			if (name == null) {
				name = (String) getName.invoke(property);
			}
			return name;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new FHIRException("Error retriving property name", e);
		}
	}
	
	/**
	 * Get the type of the property
	 * @return The type of the property
	 */
	public String getTypeCode() {
		try {
			if (typeCode == null) {
				typeCode = (String) getTypeCode.invoke(property);
			}
			return typeCode;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new FHIRException("Error retriving property type", e);
		}
	}

	/**
	 * Get the class representing the type for this property
	 * @return The class representing the type
	 * @throws ClassNotFoundException If the class cannot be found.
	 */
	public Class<IBase> getType() throws ClassNotFoundException {
		String type = getTypeCode();
		if (Character.isLowerCase(type.charAt(0))) {
			// Convert a primitive to it's HAPI type name.
			type = Character.toUpperCase(type.charAt(0)) + type.substring(1) + "Type";
		}
		type = property.getClass().getPackageName() + type;
		@SuppressWarnings("unchecked")
		Class<IBase> t = (Class<IBase>) Patient.class.getClassLoader().loadClass(type); 
		return t;
	}

	/**
	 * Get the description of the property
	 * @return The description of the property
	 */
	public String getDefinition() {
		try {
			if (definition == null) {
				definition = (String) getDefinition.invoke(property);
			}
			return definition;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new FHIRException("Error retriving property definition", e);
		}
	}
	
	/**
	 * Get the minimum cardinality of the property
	 * @return The minimum cardinality of the property
	 */
	public int getMinCardinality() {
		try {
			if (minCardinality < 0) {
				minCardinality = (int) getMinCardinality.invoke(property);
			}
			return minCardinality;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new FHIRException("Error retriving property minCardinality", e);
		}
	}
	
	/**
	 * Get the maximum cardinality of the property
	 * @return The maximum cardinality of the property
	 */
	public int getMaxCardinality() {
		try {
			if (maxCardinality < 0) {
				maxCardinality = (int) getMaxCardinality.invoke(property);
			}
			return maxCardinality;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new FHIRException("Error retriving property maxCardinality", e);
		}
	}
	
	/**
	 * Get the values in the property
	 * @return The values in the property
	 */
	@SuppressWarnings("unchecked")
	List<? super IBase> getValues() { // NOSONAR ? intentional
		try {
			if (values == null) {
				values = (List<? super IBase>) getValues.invoke(property);
			}
			return values;
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new FHIRException("Error retriving property values", e);
		}
	}
}