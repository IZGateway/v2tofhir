## ADDED Requirements

### Requirement: Parse request Content-Type with parameters
The system SHALL parse the incoming HTTP `Content-Type` header when reading a FHIR
resource body, correctly handling media-type parameters (such as `;charset=UTF-8`) so
that a valid `type/subtype` is always recovered. Reading a resource body MUST NOT fail
because the `Content-Type` header carries parameters.

#### Scenario: Content-Type includes a charset parameter
- **WHEN** a FHIR resource body is read with `Content-Type: application/fhir+json;charset=UTF-8`
- **THEN** the header is parsed without error, the JSON FHIR parser is selected, and the body is read into the target resource

#### Scenario: Content-Type has no parameters
- **WHEN** a FHIR resource body is read with `Content-Type: application/fhir+xml`
- **THEN** the header is parsed without error, the XML FHIR parser is selected, and the body is read into the target resource

### Requirement: Simplify media type to type and subtype
When simplifying a parsed media type for parser selection, the system SHALL preserve both
the primary type and the subtype (e.g. `application/fhir+json`). The simplification MUST
NOT discard the subtype or duplicate the primary type.

#### Scenario: Subtype is preserved during simplification
- **WHEN** a `Content-Type` of `application/fhir+json;charset=UTF-8` is simplified for parser selection
- **THEN** the resulting media type is `application/fhir+json`, not `application/application`

### Requirement: Select parser by media type
The system SHALL select the FHIR parser that matches the simplified media type, mapping
XML media types to the XML parser, YAML media types to the YAML parser, and defaulting to
the JSON parser for JSON or unrecognized FHIR media types.

#### Scenario: XML media type selects the XML parser
- **WHEN** the simplified media type is `application/fhir+xml`
- **THEN** the XML FHIR parser is used to read the body

#### Scenario: Unrecognized FHIR media type defaults to JSON
- **WHEN** the simplified media type is a FHIR media type not explicitly mapped to XML or YAML
- **THEN** the JSON FHIR parser is used to read the body

### Requirement: Infer media type when Content-Type is absent or unparseable
When the request has no `Content-Type` header (blank or missing), or the header value
cannot be parsed as a media type, the system SHALL infer the media type by inspecting the
leading content of the request body and select the matching parser rather than failing.

#### Scenario: Missing Content-Type falls back to content sniffing
- **WHEN** a FHIR resource body is read with no `Content-Type` header
- **THEN** the media type is inferred from the body's leading characters and the corresponding FHIR parser is selected

#### Scenario: Unparseable Content-Type falls back to content sniffing
- **WHEN** a FHIR resource body is read with a `Content-Type` header that is not a valid media type
- **THEN** no error is propagated, the media type is inferred from the body's leading characters, and the corresponding FHIR parser is selected
