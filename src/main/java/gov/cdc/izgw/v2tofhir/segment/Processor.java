package gov.cdc.izgw.v2tofhir.segment;

import java.util.List;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;

import ca.uhn.hl7v2.model.Message;
import ca.uhn.hl7v2.model.Structure;
import gov.cdc.izgw.v2tofhir.annotation.Produces;
import gov.cdc.izgw.v2tofhir.converter.Context;
import gov.cdc.izgw.v2tofhir.converter.Parser;

/**
 * 
 * A processor for a structure to convert to FHIR.
 * 
 * @author Audacious Inquiry
 *
 * @param <S>	The structure to convert.
 */
public interface Processor<S> {
	/**
	 * This is a do-nothing marker class to indicate Parsers that cannot be found
	 * in the cache of processors.
	 * 
	 * @author Audacious Inquiry
	 *
	 */
	static class NullProcessor implements Processor<Object> {
		@Override
		public void parse(Object structure) {
			// Do Nothing
		}

		@Override
		public boolean isEmpty(Object structure) {
			return true;
		}

		@Override
		public String structure() {
			return "(null)";
		}

		@Override
		public Context<Parser<Message, Structure>> getContext() {
			return null;
		}
	}

	/** A do-nothing processor.  */
	static final Processor<Object> NULL_PROCESSOR = new NullProcessor();
	/** The class of the do-nothing processor.  */
	static final Class<NullProcessor> NULL_PROCESSOR_CLASS = NullProcessor.class;

	/**
	 * Parses an structure into FHIR Resources.
	 * @param structure	The structure to parse
	 * @throws Exception A structure to parse.
	 */
	void parse(S structure) throws Exception;
	
	/**
	 * Returns true if the structure is effectively empty (e.g., no conversion to be performed).
	 * @param structure	The structure to check
	 * @return true if the structure is effectively empty
	 */
	boolean isEmpty(S structure);

	/**
	 * Return what this parser produces.
	 * @return what this parser produces
	 */
	default Produces getProduces() {
		return this.getClass().getAnnotation(Produces.class);
	}

	/**
	 * The name of the segment this parser works on.
	 * @return The name of the segment this parser works on
	 */
	String structure();

	/**
	 * @return the current parsing context
	 */
	Context<Parser<Message, Structure>> getContext();

	/**
	 * Get the bundle that is being prepared.
	 * This method can be used by parsers to get additional information from the Bundle
	 * @return The bundle that is being prepared during the parse.
	 */
	default public Bundle getBundle() {
		return getContext().getBundle();
	}

	/**
	 * Get the last issue in an OperationOutcome, adding one if there are none
	 * @param oo	The OperationOutcome
	 * @return	The issue
	 */
	public static OperationOutcomeIssueComponent getLastIssue(OperationOutcome oo) {
		List<OperationOutcomeIssueComponent> l = oo.getIssue();
		if (l.isEmpty()) {
			return oo.getIssueFirstRep();
		}
		return l.get(l.size() - 1);
	}
}

