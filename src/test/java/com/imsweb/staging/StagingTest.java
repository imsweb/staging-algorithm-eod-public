/*
 * Copyright (C) 2015 Information Management Services, Inc.
 */
package com.imsweb.staging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import com.imsweb.staging.entities.ColumnDefinition;
import com.imsweb.staging.entities.Input;
import com.imsweb.staging.entities.Mapping;
import com.imsweb.staging.entities.Output;
import com.imsweb.staging.entities.Schema;
import com.imsweb.staging.entities.StagingData.Result;
import com.imsweb.staging.entities.Table;
import com.imsweb.staging.eod.EodSchemaLookup;
import com.imsweb.staging.eod.EodStagingData;
import com.imsweb.staging.eod.EodStagingData.EodInput;
import com.imsweb.staging.eod.EodStagingData.EodOutput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assert.assertEquals;

/**
 * Base class for all algorithm-specific testing
 */
public abstract class StagingTest {

    protected static Staging _STAGING;

    /**
     * Return the algorithm name
     */
    public abstract String getAlgorithm();

    /**
     * Return the algorithm version
     */
    public abstract String getVersion();

    /**
     * Return the staging data provider
     */
    public abstract StagingFileDataProvider getProvider();

    @Test
    public void testInitialization() {
        assertThat(_STAGING.getAlgorithm()).isEqualTo(getAlgorithm());
        assertThat(_STAGING.getVersion()).isEqualTo(getVersion());
    }

    @Test
    public void testInitAllTables() {
        for (String id : _STAGING.getTableIds()) {
            Table table = _STAGING.getTable(id);

            assertThat(table).isNotNull();
            assertThat(table.getAlgorithm()).isNotNull();
            assertThat(table.getVersion()).isNotNull();
            assertThat(table.getName()).isNotNull();
        }
    }

    @Test
    public void testValidCode() {
        Map<String, String> context = new HashMap<>();
        context.put("hist", "7000");
        assertThat(_STAGING.isContextValid("prostate", "hist", context)).isFalse();
        context.put("hist", "8000");
        assertThat(_STAGING.isContextValid("prostate", "hist", context)).isTrue();
        context.put("hist", "8542");
        assertThat(_STAGING.isContextValid("prostate", "hist", context)).isTrue();

        // make sure null is handled
        context.put("hist", null);
        assertThat(_STAGING.isContextValid("prostate", "hist", context)).isFalse();

        // make sure blank is handled
        context.put("hist", "");
        assertThat(_STAGING.isContextValid("prostate", "hist", context)).isFalse();
    }

    @Test
    public void testBasicInputs() {
        // all inputs for all schemas will have null unit and decimal places
        for (String id : _STAGING.getSchemaIds()) {
            Schema schema = _STAGING.getSchema(id);
            for (Input input : schema.getInputs()) {
                assertThat(input.getUnit()).as("No schemas should have units").isNull();
                assertThat(input.getDecimalPlaces()).as("No schemas should have decimal places").isNull();
            }
        }
    }

    @Test
    public void testValidSite() {
        assertThat(_STAGING.isValidSite(null)).isFalse();
        assertThat(_STAGING.isValidSite("")).isFalse();
        assertThat(_STAGING.isValidSite("C21")).isFalse();
        assertThat(_STAGING.isValidSite("C115")).isFalse();

        assertThat(_STAGING.isValidSite("C509")).isTrue();
    }

    @Test
    public void testValidHistology() {
        assertThat(_STAGING.isValidHistology(null)).isFalse();
        assertThat(_STAGING.isValidHistology("")).isFalse();
        assertThat(_STAGING.isValidHistology("810")).isFalse();
        assertThat(_STAGING.isValidHistology("8176")).isFalse();

        assertThat(_STAGING.isValidHistology("8000")).isTrue();
        assertThat(_STAGING.isValidHistology("8201")).isTrue();
    }

    @Test
    public void testAllowedFields() {
        Set<String> descriminators = new HashSet<>();
        descriminators.add(EodStagingData.PRIMARY_SITE_KEY);
        descriminators.add(EodStagingData.HISTOLOGY_KEY);

        for (String id : _STAGING.getSchemaIds()) {
            Schema schema = _STAGING.getSchema(id);
            if (schema.getSchemaDiscriminators() != null)
                descriminators.addAll(schema.getSchemaDiscriminators());
        }

        assertThat(descriminators).containsExactlyInAnyOrder(
                EodStagingData.PRIMARY_SITE_KEY,
                EodStagingData.HISTOLOGY_KEY,
                EodInput.SEX.toString(),
                EodInput.BEHAVIOR.toString(),
                EodInput.DX_YEAR.toString(),
                EodInput.DISCRIMINATOR_1.toString(),
                EodInput.DISCRIMINATOR_2.toString());
    }

    @Test
    public void testGetTable() {
        assertThat(_STAGING.getTable("bad_table_name")).isNull();
    }

    @Test
    public void testCachedSiteAndHistology() {
        StagingDataProvider provider = getProvider();
        assertThat(provider.getValidSites().size() > 0).isTrue();
        assertThat(provider.getValidHistologies().size() > 0).isTrue();

        // site tests
        List<String> validSites = Arrays.asList("C000", "C809");
        List<String> invalidSites = Arrays.asList("C727", "C810");
        for (String site : validSites)
            assertThat(provider.getValidSites().contains(site)).isTrue();
        for (String site : invalidSites)
            assertThat(provider.getValidSites().contains(site)).isFalse();

        // hist tests
        List<String> validHist = Arrays.asList("8000", "8002", "8005", "8290", "9992", "9993");
        List<String> invalidHist = Arrays.asList("8006", "8444");
        for (String hist : validHist)
            assertThat(provider.getValidHistologies().contains(hist))
                    .withFailMessage("The histology '" + hist + "' is not in the valid histology list")
                    .isTrue();
        for (String hist : invalidHist)
            assertThat(provider.getValidHistologies().contains(hist))
                    .withFailMessage("The histology '" + hist + "' is not supposed to be in the valid histology list")
                    .isFalse();
    }

    @Test
    public void testForUnusedTables() {
        Set<String> usedTables = new HashSet<>();
        for (String id : _STAGING.getSchemaIds())
            usedTables.addAll(_STAGING.getSchema(id).getInvolvedTables());

        Set<String> unusedTables = _STAGING.getTableIds().stream().filter(id -> !usedTables.contains(id)).collect(Collectors.toSet());

        if (!unusedTables.isEmpty())
            fail("There are " + unusedTables.size() + " tables that are not used in any schema: " + unusedTables);
    }

    @Test
    public void testInputTables() {
        Set<String> errors = new HashSet<>();

        for (String schemaId : _STAGING.getSchemaIds()) {
            Schema schema = _STAGING.getSchema(schemaId);

            // build a list of input tables that should be excluded
            for (Input input : schema.getInputs()) {
                if (input.getTable() != null) {
                    Set<String> inputKeys = new HashSet<>();
                    Table table = _STAGING.getTable(input.getTable());
                    for (ColumnDefinition def : table.getColumnDefinitions())
                        if (ColumnDefinition.ColumnType.INPUT.equals(def.getType()))
                            inputKeys.add(def.getKey());

                    // make sure the input key matches the an input column
                    if (!inputKeys.contains(input.getKey()))
                        errors.add("Input key " + schemaId + ":" + input.getKey() + " does not match validation table " + table.getId() + ": " + inputKeys);
                }
            }
        }

        assertNoErrors(errors, "input values and their assocated validation tables");
    }

    @Test
    public void verifyInputs() {
        List<String> errors = new ArrayList<>();

        for (String id : _STAGING.getSchemaIds()) {
            Schema schema = _STAGING.getSchema(id);

            // loop over all the inputs returned by processing the schema and make sure they are all part of the main list of inputs on the schema
            for (String input : _STAGING.getInputs(schema))
                if (!schema.getInputMap().containsKey(input))
                    errors.add("Error processing schema " + schema.getId() + ": Table input '" + input + "' not in master list of inputs");
        }

        assertNoErrors(errors, "input values");
    }

    @Test
    public void testMappingIdUniqueness() {
        Set<String> errors = new HashSet<>();

        for (String schemaId : _STAGING.getSchemaIds()) {
            Schema schema = _STAGING.getSchema(schemaId);

            // build a list of input tables that should be excluded
            Set<String> ids = new HashSet<>();

            if (schema.getMappings() != null)
                for (Mapping mapping : schema.getMappings()) {
                    if (ids.contains(mapping.getId()))
                        errors.add("The mapping id " + schemaId + ":" + mapping.getId() + " is duplicated.  This should never happen");
                    ids.add(mapping.getId());
                }
        }

        assertNoErrors(errors, "input values and their assocated validation tables");
    }

    @Test
    public void testBehaviorDescriminator() {
        // test valid combination that requires discriminator and a good discriminator is supplied
        EodSchemaLookup lookup = new EodSchemaLookup("C717", "9591");
        List<Schema> lookups = _STAGING.lookupSchema(lookup);
        assertThat(lookups).hasSize(3);
        lookup.setInput(EodInput.DISCRIMINATOR_1.toString(), "1");
        lookup.setInput(EodInput.BEHAVIOR.toString(), "3");
        lookups = _STAGING.lookupSchema(lookup);
        assertThat(lookups).hasSize(1);
        assertThat(lookups.get(0).getId()).isEqualTo("hemeretic");
    }

    @Test
    public void testStagingEnums() {
        Set<String> enumInput = Arrays.stream(EodInput.values()).map(EodInput::toString).collect(Collectors.toSet());
        Set<String> enumOutput = Arrays.stream(EodOutput.values()).map(EodOutput::toString).collect(Collectors.toSet());

        // collect all input and output fields from all schemas
        Set<String> schemaInput = new HashSet<>();
        Set<String> schemaOutput = new HashSet<>();
        for (String schemaId : _STAGING.getSchemaIds()) {
            Schema schema = _STAGING.getSchema(schemaId);

            schemaInput.addAll(_STAGING.getInputs(schema));
            schemaOutput.addAll(_STAGING.getOutputs(schema));
        }

        assertThat(schemaInput).hasSameElementsAs(enumInput);
        assertThat(schemaOutput).hasSameElementsAs(enumOutput);
    }

    @Test
    public void testNaaccrXmlIds() {
        List<String> errors = new ArrayList<>();

        Map<String, Set<String>> inputMappings = new HashMap<>();
        Map<String, Set<String>> outputMappings = new HashMap<>();
        for (String schemaId : _STAGING.getSchemaIds()) {
            Schema schema = _STAGING.getSchema(schemaId);

            for (Input input : schema.getInputs()) {
                if (input.getNaaccrItem() != null && input.getNaaccrXmlId() == null)
                    errors.add("Schema input " + schema.getId() + "." + input.getKey() + " has a NAACCR number but is missing NAACCR XML ID");

                if (input.getNaaccrXmlId() != null) {
                    if (inputMappings.containsKey(input.getNaaccrXmlId()))
                        inputMappings.get(input.getNaaccrXmlId()).add(input.getKey());
                    else
                        inputMappings.put(input.getNaaccrXmlId(), new HashSet<>(Collections.singletonList(input.getKey())));
                }
            }

            for (Output output : schema.getOutputs()) {
                if (output.getNaaccrItem() != null && output.getNaaccrXmlId() == null)
                    errors.add("Schema output " + schema.getId() + "." + output.getKey() + " has a NAACCR number but is missing NAACCR XML ID");

                if (output.getNaaccrXmlId() != null) {
                    if (outputMappings.containsKey(output.getNaaccrXmlId()))
                        outputMappings.get(output.getNaaccrXmlId()).add(output.getKey());
                    else
                        outputMappings.put(output.getNaaccrXmlId(), new HashSet<>(Collections.singletonList(output.getKey())));
                }
            }
        }

        // verify that if a field has a given NAACCR XML ID, then all fields with that same XML ID have the same key.
        inputMappings.forEach((k, v) -> {
            if (v.size() > 1)
                errors.add("NAACCR XML Id " + k + " is listed for multiple inputs: " + v);
        });
        outputMappings.forEach((k, v) -> {
            if (v.size() > 1)
                errors.add("NAACCR XML Id " + k + " is listed for multiple outputs: " + v);
        });

        assertThat(errors).overridingErrorMessage(() -> "\n" + String.join("\n", errors)).isEmpty();
    }

    @Test
    public void testMisspelledProperty() {
        EodStagingData data = new EodStagingData();
        data.setInput(EodInput.DX_YEAR, "2020");
        data.setInput(EodInput.PRIMARY_SITE, "C180");
        data.setInput(EodInput.HISTOLOGY, "8000");
        data.setInput(EodInput.NODES_POS, "90");
        data.setInput(EodInput.EOD_PRIMARY_TUMOR, "700");
        data.setInput(EodInput.EOD_REGIONAL_NODES, "300");
        data.setInput(EodInput.EOD_METS, "10");

        // perform the staging
        _STAGING.stage(data);

        assertEquals(Result.STAGED, data.getResult());
        assertEquals("colon_rectum", data.getSchemaId());
        assertEquals(0, data.getErrors().size());
        assertEquals(11, data.getPath().size());

        // before the bug fix, AJCC_VERSION_NUMBER was returning an empty string
        assertEquals("08", data.getOutput(EodOutput.AJCC_VERSION_NUMBER));

        // check other output
        assertEquals("00200", data.getOutput(EodOutput.NAACCR_SCHEMA_ID));
        assertEquals("4A", data.getOutput(EodOutput.EOD_2018_STAGE_GROUP));
        assertEquals("2.1", data.getOutput(EodOutput.DERIVED_VERSION));
        assertEquals("7", data.getOutput(EodOutput.SS_2018_DERIVED));
        assertEquals("T4b", data.getOutput(EodOutput.EOD_2018_T));
        assertEquals("N2b", data.getOutput(EodOutput.EOD_2018_N));
        assertEquals("M1a", data.getOutput(EodOutput.EOD_2018_M));
        assertEquals("20", data.getOutput(EodOutput.AJCC_ID));
    }

    /**
     * Helper method to assert failures when tracked errors exist
     */
    public void assertNoErrors(Collection<String> errors, String description) {
        if (!errors.isEmpty()) {
            System.out.println("There were " + errors.size() + " issues with " + description + ".");
            errors.forEach(System.out::println);
            fail("There were " + errors.size() + " issues with " + description + ".");
        }
    }

}
