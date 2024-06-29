package test.gov.cdc.izgateway.v2tofhir;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.hl7.fhir.r4.model.Coding;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import gov.cdc.izgw.v2tofhir.converter.Mapping;
import gov.cdc.izgw.v2tofhir.converter.Systems;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class InfrastructureTests extends TestBase {
	@ParameterizedTest
	@MethodSource("getCodeSystemAliases")
	void testCodeSystemAliases(List<String> aliases) {
		String mapsTo = aliases.get(0);
		for (String alias: aliases) {
			Coding coding = new Coding(alias, null, null);
			if (coding.getUserData("originalSystem") != null) {
				// It was mapped
				assertEquals(mapsTo, coding.getSystem());
			}
		}
	}
	static List<List<String>> getCodeSystemAliases() {
		return Systems.getCodeSystemAliases();
	}
}
