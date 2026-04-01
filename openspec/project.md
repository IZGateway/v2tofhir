# v2tofhir Project

## Project Overview

**Name:** HL7 Version 2 to FHIR Conversion  
**Artifact ID:** v2tofhir  
**Group ID:** gov.cdc.izgw  
**Version:** 2.1.0-SNAPSHOT  
**Organization:** Audacious Inquiry  
**Inception:** 2022  

### Purpose

Core library for the IZ Gateway Transformation Service. Converts HL7 V2 messages (primarily V2.5.1 immunization VXU_V04) to FHIR R4 bundles. Supports flexible input: full messages, individual segments, structures, or groups.

**Design principle (Postel's Law):** Conversion methods return maximum valid data rather than throwing exceptions. Methods return `null` on invalid/empty data.

## Tech Stack

- **Language:** Java 21
- **HAPI V2:** 2.5.1 (supports v2.1–v2.8.1)
- **HAPI FHIR:** 8.0.0 (R4 structures, validation, FHIRPath)
- **Spring Boot:** via izgw-bom parent
- **Lombok:** @Data, @Slf4j throughout
- **Jackson:** JSON/YAML (jackson-databind, jackson-dataformat-yaml)
- **OpenCSV:** loads concept map CSV files from `src/main/resources/coding/`
- **Commons:** lang3, text, io, compress, beanutils, validator

## Build & Test Commands

```bash
mvn clean package          # Build JAR
mvn test                   # Run all tests (**/*Tests.java pattern)
mvn test -Dtest=ClassName  # Run specific test class
mvn jacoco:report          # Coverage report (after mvn test)
```

## Project Structure

```
v2tofhir/
├── src/main/java/gov/cdc/izgw/v2tofhir/
│   ├── annotation/     # Custom annotations
│   ├── converter/      # DatatypeConverter, MessageParser
│   ├── datatype/       # DatatypeParser<T> implementations
│   ├── segment/        # AbstractSegmentParser + per-segment parsers
│   └── utils/          # Systems, Mapping, Units, Codes, ISO3166, RaceAndEthnicity
├── src/main/resources/
│   └── coding/         # 80+ HL7 concept map CSV files, cvx.txt, mvx.txt
└── src/test/
```

## Key Terminology Classes

| Class | Purpose |
|-------|---------|
| `Systems` | OID↔URI registry via `NamingSystem` resources; `toUri()`, `toOid()`, `getSystemNames()`, `addNamingSystem()` |
| `Mapping` | Loads 80+ CSV ConceptMaps; `mapCode()`, `mapTableNameToSystem()`, `v2Table()`, `getDisplay()` |
| `Units` | V2 ANSI/ISO unit codes → UCUM `Coding` |
| `ISO3166` | Country code/name lookups |
| `RaceAndEthnicity` | CDC race and ethnicity codes → US Core extensions |
| `Codes` | Pre-built `CodeableConcept`/`Coding` constants |
| `DatatypeConverter` | `convertCodeableConcept()`, `convertCoding()` using Mapping + Systems |

## Coding Conventions

- All segment parsers extend `AbstractSegmentParser`
- Datatype parsers implement `DatatypeParser<T>`
- Terminology is accessed via static utility classes (`Systems`, `Mapping`, etc.) — no Spring injection
- `@Slf4j` for logging, `@Data` for data classes
- `@author Audacious Inquiry` on all classes
- Multiple segments can contribute to a single FHIR resource (e.g., ORC + RXA + RXR → MedicationAdministration)
