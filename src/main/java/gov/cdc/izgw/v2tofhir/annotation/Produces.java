package gov.cdc.izgw.v2tofhir.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.hl7.fhir.instance.model.api.IBaseResource;

/**
 * This annotation documents the resources produced by a StructureParser
 * @author Audacious Inquiry
 *
 */

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented 

public @interface Produces {
	/** 
	 * Identifies the producing segment
	 * @return the producing segment
	 */
	String segment();
	
	/**
	 * Identifies the primary resource produced by the parser.
	 */
	Class<? extends IBaseResource> resource();

	/**
	 * Identifies any extra resources produced by the parser.
	 */
	Class<? extends IBaseResource>[] extra() default {};

}
