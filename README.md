# staging-algorithm-eod-public

[![Build Status](https://travis-ci.com/imsweb/staging-algorithm-eod-public.svg?branch=master)](https://travis-ci.com/imsweb/staging-algorithm-eod-public)
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/com.imsweb/staging-algorithm-eod-public/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.imsweb/staging-algorithm-eod-public)

Extent of Disease (EOD) is a set of three data items that describe how far a cancer has spread at the time of diagnosis. EOD 2018 is effective for cases 
diagnosed in 2018 and later.
 
In each EOD schema, valid values, definitions, and registrar notes are provided for
 
- EOD Primary Tumor
- EOD Lymph Nodes
- EOD Mets
- Summary Stage 2018
- Site-Specific Data Items (SSDIs), including grade, pertinent to the schema

For cancer cases diagnosed January 1, 2018 and later, the NCI SEER program will collect Extent of Disease (EOD) revised for 2018 and Summary Stage 2018. 
The schemas have been developed to be compatible with the AJCC 8th Edition chapter definitions. 

All of the standard setting organizations will collect the predictive and prognostic factors through Site Specific Data Items (SSDIs). Unlike the SSFs, 
these data items have formats and code structures specific to the data item.
 
## Download

Java 8 is the minimum version required to use the library.

Download [the latest JAR][1] or grab via Maven:

```xml
<dependency>
    <groupId>com.imsweb</groupId>
    <artifactId>staging-algorithm-eod-public</artifactId>
    <version>1.4</version>
</dependency>
```

or via Gradle:

```groovy
compile 'com.imsweb.com:staging-algorithm-eod-public:1.4'
```

## Usage

Full documentation can be found in the [Wiki](https://github.com/imsweb/staging-client-java/wiki/)

### Get a `Staging` instance

Everything starts with getting an instance of the `Staging` object.  There are `DataProvider` objects for each staging algorithm and version.  The `Staging`
object is thread safe and cached so subsequent calls to `Staging.getInstance()` will return the same object.

For example, to get an instance of the EOD algorithm

```java
Staging staging = Staging.getInstance(EodDataProvider.getInstance(EodVersion.v1_4));
```

### Schemas

Schemas represent sets of specific staging instructions.  Determining the schema to use for staging is based on primary site, histology and sometimes additional
discrimator values.  Schemas include the following information:

- schema identifier (i.e. "prostate")
- algorithm identifier (i.e. "eod_public")
- algorithm version (i.e. "1.4")
- name
- title, subtitle, description and notes
- schema selection criteria
- input definitions describing the data needed for staging
- list of table identifiers involved in the schema
- a list of initial output values set at the start of staging
- a list of mappings which represent the logic used to calculate staging output

To get a list of all schema identifiers,

```java
Set<String> schemaIds = staging.getSchemaIds();
```

To get a single schema by identifer,

```java
StagingSchema schema = staging.getSchema("prostate");
```

### Tables

Tables represent the building blocks of the staging instructions specified in schemas.  Tables are used to define schema selection criteria, input validation and staging logic.
Tables include the following information:

- table identifier (i.e. "ajcc7_stage")
- algorithm identifier (i.e. "eod_public")
- algorithm version (i.e. "1.4")
- name
- title, subtitle, description, notes and footnotes
- list of column definitions
- list of table data

To get a list of all table identifiers,

```java
Set<String> tableIds = staging.getTableIds();
```

That list will be quite large.  To get a list of table indentifiers involved in a particular schema,

```java
Set<String> tableIds = staging.getInvolvedTables("prostate");
```

To get a single table by identifer,

```java
StagingTable table = staging.getTable("ajcc7_stage");
```

### Lookup a schema

A common operation is to look up a schema based on primary site, histology and optionally one or more discriminators.  Each staging algorithm has 
a `SchemaLookup` object customized for the specific inputs needed to lookup a schema.

Here is a lookup based on site and histology.

```java
// test valid combinations that do not require a discriminator
EodSchemaLookup lookup = staging.lookupSchema(new EodSchemaLookup("C629", "9231"));
assertEquals(1, lookup.size());
assertEquals("soft_tissue_other", lookup.get(0).getId());
```

If the call returns a single result, then it was successful.  If it returns more than one result, then it needs a discriminator.  Information about the 
required discriminator is included in the list of results.  In the Collaborative Staging example, the field `ssf25` is always used as the discriminator.  
Other staging algorithms may use different sets of discriminators that can be determined based on the result.

```java
lookup = staging.lookupSchema(new EodSchemaLookup("C111", "8200"));
assertEquals(3, lookup.size());
assertEquals(new HashSet<>(Arrays.asList("oropharynx_hpv_mediated_p16_pos", "nasopharynx", "oropharynx_p16_neg")),
        lookup.stream().map(StagingSchema::getId).collect(Collectors.toSet()));
assertEquals(new HashSet<>(Arrays.asList("discriminator_1", "discriminator_2")), 
        lookup.stream().flatMap(d -> d.getSchemaDiscriminators().stream()).collect(Collectors.toSet()));

// test valid combination that requires discriminator and a good discriminator is supplied
schemaLookup = new EodSchemaLookup("C111", "8200");
schemaLookup.setInput(EodInput.DISCRIMINATOR_1.toString(), "1");
lookup = staging.lookupSchema(schemaLookup);
assertEquals(1, lookup.size());
assertEquals(new HashSet<>(Collections.singletonList("discriminator_1")), 
        lookup.stream().flatMap(d -> d.getSchemaDiscriminators().stream()).collect(Collectors.toSet()));
assertEquals("nasopharynx", lookup.get(0).getId());

schemaLookup.setInput(EodInput.DISCRIMINATOR_1.toString(), "2");
schemaLookup.setInput(EodInput.DISCRIMINATOR_2.toString(), "1");
lookup = staging.lookupSchema(schemaLookup);
assertEquals(1, lookup.size());
assertEquals(new HashSet<>(Arrays.asList("discriminator_1", "discriminator_2")), 
        lookup.stream().flatMap(d -> d.getSchemaDiscriminators().stream()).collect(Collectors.toSet()));
assertEquals("oropharynx_p16_neg", lookup.get(0).getId());
```

### Calculate stage

Staging a case requires first knowing which schema you are working with.  Once you have the schema, you can tell which fields (keys) need to be collected and supplied
to the `stage` method call.

A `StagingData` object is used to make staging calls.  All inputs to staging should be set on the `StagingData` object and the staging call will add the results.  The
results include:

- output - all output values resulting from the calculation
- errors - a list of errors and their descriptions
- path - an ordered list of the tables that were used in the calculation

```java
EodStagingData data = new EodStagingInputBuilder()
		.withInput(EodInput.PRIMARY_SITE, "C250")
		.withInput(EodInput.HISTOLOGY, "8154")
		.withInput(EodInput.DX_YEAR, "2018")
		.withInput(EodInput.TUMOR_SIZE_SUMMARY, "004")
		.withInput(EodInput.NODES_POS, "03")
		.withInput(EodInput.EOD_PRIMARY_TUMOR, "500")
		.withInput(EodInput.EOD_REGIONAL_NODES, "300")
		.withInput(EodInput.EOD_METS, "10").build();

// perform the staging
staging.stage(data);

assertEquals(StagingData.Result.STAGED, data.getResult());
assertEquals("pancreas", data.getSchemaId());
assertEquals(0, data.getErrors().size());
assertEquals(12, data.getPath().size());
assertEquals(8, data.getOutput().size());

// check outputs
assertEquals(EodDataProvider.EodVersion.LATEST.getVersion(), data.getOutput(EodOutput.DERIVED_VERSION));
assertEquals("7", data.getOutput(EodOutput.SS_2018_DERIVED));
assertEquals("00280", data.getOutput(EodOutput.NAACCR_SCHEMA_ID));
assertEquals("4", data.getOutput(EodOutput.EOD_2018_STAGE_GROUP));
assertEquals("T1", data.getOutput(EodOutput.EOD_2018_T));
assertEquals("N1", data.getOutput(EodOutput.EOD_2018_N));
assertEquals("M1", data.getOutput(EodOutput.EOD_2018_M));
assertEquals("28", data.getOutput(EodOutput.AJCC_ID));
```

## About SEER

The Surveillance, Epidemiology and End Results ([SEER](http://seer.cancer.gov)) Program is a premier source for cancer statistics in the United States. The SEER
Program collects information on incidence, prevalence and survival from specific geographic areas representing 28 percent of the US population and reports on all
these data plus cancer mortality data for the entire country.

[1]: http://repository.sonatype.org/service/local/artifact/maven/redirect?r=central-proxy&g=com.imsweb&a=staging-algorithm-tnm&v=LATEST
