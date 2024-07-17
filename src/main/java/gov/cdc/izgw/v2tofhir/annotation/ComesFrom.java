package gov.cdc.izgw.v2tofhir.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hl7.fhir.instance.model.api.IBase;

/**
 * This annotation is used to document V2 to FHIR segment conversions.
 * 
 * In the future, it may be used to automate such conversions.
 * 
 * @author Audacious Inquiry
 */

@Repeatable(ComesFrom.List.class)
@Target({ElementType.TYPE, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented 
public @interface ComesFrom {
	/**
	 * The FHIR Path of the content in the bundle
	 * @return	The FHIR Path expression that extracts the content from the Bundle
	 */
	String path();
	/**
	 * The FHIR Paths of other content in the bundle
	 * @return	The FHIR Path expressions that extract the content from the Bundle
	 */
	String[] also() default {};

	/**
	 * The terser paths of the objects that construct the object referenced in path
	 * @return	HAPI V2 terser path expressions indicating where the data came from
	 */
	String[] source() default {};
	
	/**
	 * The 1-based index of the field to extract from.
	 * @return the 1-based index of the field to extract from, or 0 if a field 
	 */
	int field() default 0;
	/**
	 * The 1-based index of the component to extract from.
	 * @return the 1-based index of the component to extract from, or 0 if the component is not used.
	 */
	int component() default 0;
	
	/**
	 * A comment about the conversion
	 * @return The comment
	 */
	String comment() default "";
	
	/**
	 * The name of the concept map to use when mapping between code systems.
	 * @return The name of the concept mape
	 */
	String map() default "";
	
	/**
	 * The name of the HL7 Table used with this field.
	 * @return theh name of the HL7 Table
	 */
	String table() default "";
	
	/**
	 * A fixed value
	 * @return	The fixed value.
	 */
	String fixed() default "";
	
	/**
	 * The type to convert to if other than the type determined from the path
	 * in the FHIR resource. 
	 * 
	 * @return	The type to convert to, or IBase.class to convert to the 
	 * type determined by the path.
	 */
	Class<? extends IBase> type() default IBase.class;
	
	/**
	 * Set the priority of this mapping.
	 * Mappings are processed in field and component order, unless the priority is changed
	 * The higher the priority, the earlier in processing the mapping is performed.
	 * @return The priority of the mapping.
	 */
	int priority() default 0;
    /**
     * A list of mapping from FHIR types to HL7 V2 Terser paths
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ ElementType.TYPE, ElementType.METHOD })
    @Documented
    @interface List {
    	/**
    	 * A list of ComesFrom annotations.
    	 * @return The values
    	 */
        ComesFrom[] value();
    }
}
