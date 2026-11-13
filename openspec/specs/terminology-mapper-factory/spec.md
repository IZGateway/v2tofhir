# Terminology Mapper Factory

## Purpose

Defines `TerminologyMapperFactory`, the single JVM-wide resolution point for the active
`TerminologyMapper` implementation. Reads `V2TOFHIR_TERMINOLOGY_MAPPER` from the environment
at first use; falls back to `DefaultTerminologyMapper` on any error.

---

## Requirements

### Requirement: Environment-variable-driven mapper selection
`TerminologyMapperFactory` in `gov.cdc.izgw.v2tofhir.terminology` SHALL read the environment variable `V2TOFHIR_TERMINOLOGY_MAPPER` at first use. If the variable is set, the factory SHALL attempt to load the named class and instantiate it via its public no-argument constructor. If the variable is not set, the factory SHALL use `DefaultTerminologyMapper`.

#### Scenario: Custom class loaded when env var is set
- **WHEN** `V2TOFHIR_TERMINOLOGY_MAPPER=com.example.MyTerminologyMapper` is set
- **AND** `MyTerminologyMapper` is on the classpath with a public no-arg constructor
- **THEN** `TerminologyMapperFactory.get()` SHALL return an instance of `MyTerminologyMapper`

#### Scenario: Default used when env var is absent
- **WHEN** `V2TOFHIR_TERMINOLOGY_MAPPER` is not set
- **THEN** `TerminologyMapperFactory.get()` SHALL return an instance of `DefaultTerminologyMapper`

---

### Requirement: Fallback on invalid or unloadable class
If `V2TOFHIR_TERMINOLOGY_MAPPER` names a class that cannot be found, cannot be instantiated, or does not implement `TerminologyMapper`, the factory SHALL log a warning and fall back to `DefaultTerminologyMapper`. The application SHALL continue to function normally.

#### Scenario: Class not found — fallback
- **WHEN** `V2TOFHIR_TERMINOLOGY_MAPPER=com.example.DoesNotExist`
- **THEN** `TerminologyMapperFactory.get()` SHALL log a warning and return a `DefaultTerminologyMapper` instance

#### Scenario: Class does not implement TerminologyMapper — fallback
- **WHEN** the named class exists but does not implement `TerminologyMapper`
- **THEN** `TerminologyMapperFactory.get()` SHALL log a warning and return a `DefaultTerminologyMapper` instance

#### Scenario: Constructor throws — fallback
- **WHEN** the named class's no-arg constructor throws a runtime exception
- **THEN** `TerminologyMapperFactory.get()` SHALL log a warning including the exception message and return a `DefaultTerminologyMapper` instance

---

### Requirement: Single instance per JVM (lazy initialization)
`TerminologyMapperFactory` SHALL resolve and cache the `TerminologyMapper` instance on first call to `get()`. Subsequent calls SHALL return the same cached instance without re-reading the environment variable.

#### Scenario: Same instance returned on repeated calls
- **WHEN** `TerminologyMapperFactory.get()` is called multiple times
- **THEN** every call SHALL return the same object reference

#### Scenario: Environment variable not re-read after first call
- **WHEN** `TerminologyMapperFactory.get()` has been called once
- **THEN** the cached instance SHALL continue to be returned on subsequent calls

---

### Requirement: Loaded class must implement TerminologyMapper
The class named by `V2TOFHIR_TERMINOLOGY_MAPPER` SHALL implement the full `TerminologyMapper` interface. If it implements only a sub-interface, the factory SHALL reject it and fall back.

#### Scenario: Sub-interface only — rejected
- **WHEN** `V2TOFHIR_TERMINOLOGY_MAPPER` names a class implementing only `NamingSystemResolver`
- **THEN** the factory SHALL log a warning and fall back to `DefaultTerminologyMapper`
