/*
 * Copyright (C) 2015 Information Management Services, Inc.
 */
package com.imsweb.staging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.Test;

import com.imsweb.decisionengine.ColumnDefinition;
import com.imsweb.staging.entities.StagingColumnDefinition;
import com.imsweb.staging.entities.StagingMapping;
import com.imsweb.staging.entities.StagingRange;
import com.imsweb.staging.entities.StagingSchema;
import com.imsweb.staging.entities.StagingSchemaInput;
import com.imsweb.staging.entities.StagingTable;
import com.imsweb.staging.entities.StagingTableRow;
import com.imsweb.staging.eod.EodSchemaLookup;
import com.imsweb.staging.eod.EodStagingData;
import com.imsweb.staging.eod.EodStagingData.EodInput;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

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
            StagingTable table = _STAGING.getTable(id);

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
            StagingSchema schema = _STAGING.getSchema(id);
            for (StagingSchemaInput input : schema.getInputs()) {
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
            StagingSchema schema = _STAGING.getSchema(id);
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
            StagingSchema schema = _STAGING.getSchema(schemaId);

            // build a list of input tables that should be excluded
            for (StagingSchemaInput input : schema.getInputs()) {
                if (input.getTable() != null) {
                    Set<String> inputKeys = new HashSet<>();
                    StagingTable table = _STAGING.getTable(input.getTable());
                    for (StagingColumnDefinition def : table.getColumnDefinitions())
                        if (ColumnDefinition.ColumnType.INPUT.equals(def.getType()))
                            inputKeys.add(def.getKey());

                    // make sure the input key matches the an input column
                    if (!inputKeys.contains(input.getKey()))
                        errors.add("Input key " + schemaId + ":" + input.getKey() + " does not match validation table " + table.getId() + ": " + inputKeys.toString());
                }
            }
        }

        assertNoErrors(errors, "input values and their assocated validation tables");
    }

    @Test
    public void verifyInputs() {
        List<String> errors = new ArrayList<>();

        for (String id : _STAGING.getSchemaIds()) {
            StagingSchema schema = _STAGING.getSchema(id);

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
            StagingSchema schema = _STAGING.getSchema(schemaId);

            // build a list of input tables that should be excluded
            Set<String> ids = new HashSet<>();

            List<StagingMapping> mappings = schema.getMappings();
            if (mappings != null)
                for (StagingMapping mapping : mappings) {
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
        List<StagingSchema> lookups = _STAGING.lookupSchema(lookup);
        assertThat(lookups).hasSize(3);
        lookup.setInput(EodInput.DISCRIMINATOR_1.toString(), "1");
        lookup.setInput(EodInput.BEHAVIOR.toString(), "3");
        lookups = _STAGING.lookupSchema(lookup);
        assertThat(lookups).hasSize(1);
        assertThat(lookups.get(0).getId()).isEqualTo("hemeretic");
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

    /**
     * Return the input length from a specified table
     * @param tableId table indentifier
     * @param key input key
     * @return null if no length couild be determined, or the length
     */
    protected Integer getInputLength(String tableId, String key) {
        StagingTable table = _STAGING.getTable(tableId);
        Integer length = null;

        // loop over each row
        for (StagingTableRow row : table.getTableRows()) {
            List<StagingRange> ranges = row.getInputs().get(key);

            for (StagingRange range : ranges) {
                String low = range.getLow();
                String high = range.getHigh();

                if (range.matchesAll() || low.isEmpty())
                    continue;

                if (low.startsWith("{{") && low.contains(Staging.CTX_YEAR_CURRENT))
                    low = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
                if (high.startsWith("{{") && high.contains(Staging.CTX_YEAR_CURRENT))
                    high = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));

                if (length != null && (low.length() != length || high.length() != length))
                    throw new IllegalStateException("Inconsistent lengths in table " + tableId + " for key " + key);

                length = low.length();
            }
        }

        return length;
    }

}
