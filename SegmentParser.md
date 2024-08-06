# Creating a Segment Parser
For this, we will walk through an example with a version of the QAKParser which is a pretty simple parser for a Segment with only a few fields.

```
package gov.cdc.izgw.v2tofhir.segment;

/* Imports Go Here */
```

## Add Produces documentation
Add your @Produces annotation to indicate which segment this parser handles, and what primary resource
it produces:

```
@Produces(segment="QAK", resource=OperationOutcome.class)
@Slf4j
```
## Extend from AbstractSegmentParser
Extend from AbstractSegmentParser to take advantage of built in Message and Segment parsing capabilities.

```
public class QAKParser extends AbstractSegmentParser {
```
## Create variables to store your state
Define a private member variable for the resource you will create during a single parse of a segment.
NOTE: A segment parser is stateful, and the resource being created is part of its state.
```
	private OperationOutcome oo;
```

## Create a place to manage your field handler list
Create a static list to store the field handlers for each field of the segment. This array will be populated
by scanning the parser class for @ComesFrom annotations on methods.  For each @ComesFrom annotation
on a public method in the class (NOTE: Methods must be public to be visible to the MessageParser),
a FieldHandler will be created for the method.

```
	private static List<FieldHandler> fieldHandlers = new ArrayList<>();
```

## Initialize your class
Do whatever other initialization you need to for the class

```
	/** The coding system to use for query tags in metadata */
	public static final String QUERY_TAG_SYSTEM = "QueryTag";
	static {
		log.debug("{} loaded", QAKParser.class.getName());
	}
```
## Write the Constructor
Write the constructor like this:
```
	/**
	 * Construct a new QAKParser
	 * 
	 * @param messageParser	The messageParser to construct this parser for.
	 */
	public QAKParser(MessageParser messageParser) {
```
Tell the super class what kind of segment you are handling.
```
		super(messageParser, "QAK");
		if (fieldHandlers.isEmpty()) {
			initFieldHandlers(this, fieldHandlers);
		}
	}
```

## Add Required Methods
Add the getFieldHandlers() method like this:
```
	@Override
	public List<FieldHandler> getFieldHandlers() {
		return fieldHandlers;
	}
```
## Setup your parser to parse a segment
The setup method is called when starting to parse a new segment.
Your parser has the opportunity at this time to inspect any work that
has already been done, and if it need do nothing, simply return null
to indicate that it won't be contributing to this message parse.
```
	@Override
	public IBaseResource setup() {
```
Create the primary resource the parser will generate while reading the segment information
```
		// QAK Creates its own OperationOutcome resource.
		oo = createResource(OperationOutcome.class);
		return oo;
	}

```
## Write a field handler method for each field.
Handle each field with a method to add some FHIR Data type to your primary resource
or create any other resources needed based on the parse.
```
	/**
	 * Sets OperationOutcome[1].meta.tag to the Query Tag in QAK-1
	 * @param queryTag	The query tag
	 */
```
This method handles Field 1 of the QAK Segment (a.k.a., QAK-1), and stuffs it into OperationOutcome[1].meta.tag.
The method name should explain what it is doing.  The ONLY argument to this method should be a FHIR Datatype.  It could also be 
a Location, Organization, RelatedPerson or Practitioner resource since some V2 datatypes are very similar to those resources.
NOTE: If you accept a resource from the Datatype Converter in this method, you must call addResource() on it.
```
	@ComesFrom(path="OperationOutcome[1].meta.tag", field = 1, comment="Sets meta.tag.system to QueryTag, and code to value of QAK-1")
	public void setMetaTag(StringType queryTag) {
		oo.getMeta().addTag(new Coding(QUERY_TAG_SYSTEM, queryTag.getValue(), null));
	}

```
That's it.  QAK-1 will populate the meta.tag field of the second OperationOutcome resource with a Coding build from the query tag in QAK-1
and a constant coding system.  Note, a single field handler method can be annotated multiple times with @ComesFrom to indicate different
ways in which to add data to the resource being generated. The PIDParser class uses the same method to add multiple different fields
to the Patient Resource with one method.

## A little more complex field handler
To do something a little bit more complex (but not much more) see the field handler method for QAK-2 below

```
	/**
	 * Set OperationOutcome.issue.details, issue.code, and issue.severity
	 * 
	 * Sets issue.details to details
	 * Maps issue.code and issue.severity from details
	 * 
	 * @param details	The coding found in QAK-2 from table 0208
	 */
```
This is for QAK-2, and will get a Coding from the parser.  The coding will have a system value 
based on the value of the table (0208) parameter in @ComesFrom.  The table parameter tells the 
Message Parser what table values are expected to come from when creating CodeType, Coding, and CodeableConcept
resources.

NOTE: This field also documents other FHIRPaths that may be updated as a result of its
operations.  The path and also parameters in @ComesFrom help document the Field handler, and also
are used to generate testing annotations on methods.
```
	@ComesFrom(path="OperationOutcome[1].issue.details", field = 2, table = "0208", 
			   also={ "OperationOutcome[1].issue.code", 
					  "OperationOutcome[1].issue.severity"})
	public void setIssueDetails(Coding details) {
```
The first and simplest step is to just record the details of the QAK in a new issue within the OperationOutcome being
created by this parser
```
		OperationOutcomeIssueComponent issue = oo.addIssue().setDetails(new CodeableConcept().addCoding(details));
```
## Map coded fields to FHIR Enumeration types where necessary
Next, the codes from details.code are mapping into FHIR specific enumeration types to convert AE, AR, NF and OK 
values into FHIR specific codes for an issue to report on the error type, and severity of the error.

You will often need to map V2 codes to FHIR Enumerations for certains kinds of resource specific fields, such as Observation.status, or Patient.gender.
```
		if (details.hasCode()) {
			switch (details.getCode().toUpperCase()) {
			case "AE":
				issue.setCode(IssueType.INVALID);
				issue.setSeverity(IssueSeverity.ERROR);
				break;
			case "AR":
				issue.setCode(IssueType.PROCESSING);
				issue.setSeverity(IssueSeverity.FATAL);
				break;
			case "NF", "OK":
			default:
				issue.setCode(IssueType.INFORMATIONAL);
				issue.setSeverity(IssueSeverity.INFORMATION);
				break;
			}
		} else {
```
## Handle Missing Data
Be sure to handle cases where no information has been provided so that
the resources you generate can conform to resource profile requirements
```
			issue.setCode(IssueType.UNKNOWN);
			issue.setSeverity(IssueSeverity.INFORMATION);
		}
	}
}
```
That's it, a complete parser.

## Field Handlers are Supposed to be Easy to Write
As you can see, some field handlers are very short, and based on the 
@ComesFrom annotations, can almost write themselves.  See a field handler
from the OBXParser.  As you can see, there's three lines of code, the remaining
six lines serve as documentation or whitespace.
```
	/**
	 * Set the reference range
	 * @param referenceRange	the reference range
	 */
	@ComesFrom(path = "Observation.referenceRange.text", field = 7, comment = "Reference Range")	
	public void setReferenceRange(StringType referenceRange) {
		observation.addReferenceRange().setTextElement(referenceRange); 
	}
```
