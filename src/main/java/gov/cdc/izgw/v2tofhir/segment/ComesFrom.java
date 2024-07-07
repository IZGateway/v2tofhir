package gov.cdc.izgw.v2tofhir.segment;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation is used to document V2 to FHIR segment conversions.
 * 
 * In the future, it may be used to automate such conversions.
 * 
 * @author Audacious Inquiry
 */

@Repeatable(ComesFrom.List.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented 
public @interface ComesFrom {
	/**
	 * The FHIR Path of the content in the bundle
	 * @return	The FHIR Path expression that extracts the content from the Bundle
	 */
	String path();

	/**
	 * The terser paths of the objects that construct the object referenced in path
	 * @return	HAPI V2 terser path expressions indicating where the data came from
	 */
	String[] source() default "";
	
	/**
	 * A comment about the conversion
	 * @return The comment
	 */
	String comment() default "";
	
	/**
	 * The name of the concept map to use when mapping between code systems.
	 */
	String map() default "";
	
	/**
	 * The name of the HL7 Table used with this field.
	 */
	String table() default "";
	
	/**
	 * The name of the specialized method to use to map values
	 */
	String method() default "";
	
	/**
	 * A fixed value
	 * @return	The fixed value.
	 */
	String fixed() default "";
    /**
     * A list of mapping from FHIR types to HL7 V2 Terser paths
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Documented
    @interface List {
        ComesFrom[] value();
    }
}

