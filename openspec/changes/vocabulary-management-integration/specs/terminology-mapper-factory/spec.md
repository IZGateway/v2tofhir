## ADDED Requirements

### Requirement: Environment-variable-driven mapper selection
`TerminologyMapperFactory` in `gov.cdc.izgw.v2tofhir.terminology` SHALL read the environment variable `V2TOFHIR_TERMINOLOGY_MAPPER` at first use. If the variable is set, the factory SHALL attempt to load the named class and instantiate it via its public no-argument constructor. If the variable is not set, the factory SHALL use `DefaultTerminologyMapper`.

#### Scenario: Custom class loaded when env var is set
- **WHEN** `V2TOFHIR_TERMINOLOGY_MAPPER=com.example.MyTerminologyMapper` is set
- **AND** `MyTerminologyMapper` is on the classpath and has a public no-arg constructor
- **THEN** `TerminologyMapperFactory.get()` SHALL return an instance of `MyTerminologyMapper`

#### Scenario: Default used when env var is absent
- **WHEN** `V2TOFHIR_TERMINOLOGY_MAPPER` is not set
- **THEN** `TerminologyMapperFactory.get()` SHALL return an instance of `DefaultTerminologyMapper`

---

### Requirement: Fallback on invalid or unloadable class
If `V2TOFHIR_TERMINOLOGY_MAPPER` names a class that cannot be found, cannot be instantiated (no public no-arg constructor, throws during construction, etc.), or does not implement `TerminologyMapper`, the factory SHALL log a warning describing the failure and fall back to `DefaultTerminologyMapper`. The application SHALL continue to function normally.

#### Scenario: Class not found — fallback
- **WHEN** `V2TOFHIR_TERMINOLOGY_MAPPER=com.example.DoesNotExist`
- **THEN** `TerminologyMapperFactory.get()` SHALL log a warning
- **AND** return a `DefaultTerminologyMapper` instance

#### Scenario: Class does not implement TerminologyMapper — fallback
- **WHEN** `V2TOFHIR_TERMINOLOGY_MAPPER` names a class that exists but does not implement `TerminologyMapper`
- **THEN** `TerminologyMapperFactory.get()` SHALL log a warning
- **AND** return a `DefaultTerminologyMapper` instance

#### Scenario: Constructor throws — fallback
- **WHEN** `V2TOFHIR_TERMINOLOGY_MAPPER` names a class whose no-arg constructor throws a runtime exception
- **THEN** `TerminologyMapperFactory.get()` SHALL log a warning including the exception message
- **AND** return a `DefaultTerminologyMapper` instance

---

### Requirement: Single instance per JVM (lazy initialization)
`TerminologyMapperFactory` SHALL resolve and cache the `TerminologyMapper` instance on first call to `get()`. Subsequent calls SHALL return the same cached instance without re-reading the environment variable or repeating class loading.

#### Scenario: Same instance returned on repeated calls
- **WHEN** `TerminologyMapperFactory.get()` is called multiple times
- **THEN** every call SHALL return the same object reference (i.e., `get() == get()`)

#### Scenario: Environment variable not re-read after first call
- **WHEN** `TerminologyMapperFactory.get()` has been called once
- **AND** the environment variable value changes (simulated in tests by resetting the cache)
- **THEN** the cached instance SHALL continue to be returned on subsequent calls without re-reading the variable

---

### Requirement: Loaded class must implement TerminologyMapper
The class named by `V2TOFHIR_TERMINOLOGY_MAPPER` SHALL be required to implement `TerminologyMapper`. If it implements only a sub-interface, the factory SHALL reject it (log warning, fall back to default).

#### Scenario: Sub-interface only — rejected
- **WHEN** `V2TOFHIR_TERMINOLOGY_MAPPER` names a class that implements only `NamingSystemResolver` and not `TerminologyMapper`
- **THEN** the factory SHALL log a warning stating the class does not implement `TerminologyMapper`
- **AND** fall back to `DefaultTerminologyMapper`
