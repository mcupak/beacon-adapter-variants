# Beacon adapter for GA4GH Variants API [![Build Status](https://travis-ci.org/mcupak/beacon-adapter-variants.svg?branch=develop)](https://travis-ci.org/mcupak/beacon-adapter-variants) [![GitHub license](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://raw.githubusercontent.com/mcupak/beacon-adapter-variants/develop/LICENSE)

Beacon variants adapter is an implementation of the [Beacon Adapter API](https://github.com/mcupak/beacon-adapter-api). This adapter wraps [GA4GH Variants API](http://ga4gh-schemas.readthedocs.io/en/latest/api/variants.html) and allows you to turn the API into a beacon via a compatible Beacon implementation, such as [JBDK](https://github.com/mcupak/beacon-java).

Prerequisites: Java 8, [GA4GH schemas](https://github.com/ga4gh/schemas/releases/tag/v0.6.0a8).

## Configuring the Adapter

In order to properly configure the adapter you must call the initAdapter method from the VcfBeaconAdapter class, supplying it with an AdapterConfig object once a new adapter object has been created.
There are one required parameter for the configuration that must be supplied as ConfigValues to the AdapterConfig object:

#### Required one of the following
| Name | Value | example |
|--- | ---| --- |
| "beconJsonFile" | Path to a json file that describes this beacon. The json file is a serialized representation of a beacon and must meet all the requirements of a normal beacon object. | "/path/to/beacon.json" |
| "beaconJson" | Json string that describes this beacon | See below |

#### Example beacon.json

```json
{
  "id": "sample-beacon",
  "name": "vcf_test_beacon",
  "apiVersion": "0.3",
  "organization": {
    "id": "vcf_org",
    "name": "Vcf Adapter organization",
    "description": "test organization for the vcf Beacon adapter",
    "address": "99 Lambda Drive, Consumer, Canada",
    "welcomeUrl": "www.welcome.com",
    "contactUrl": "www.contact.com",
    "logoUrl": "www.logo.com"
  },
  "description": "This beacon demonstrates the usage of the VcfBeaconAdapter",
  "version": "1",
  "welcomeUrl": "www.welcome.com",
  "alternativeUrl": "www.alternative.com",
  "createDateTime": "2016/07/23 19:23:11",
  "updateDateTime": "2016/07/23 19:23:11",
  "datasets": [
    {
      "id": "test-dataset",
      "name": "vcf-test-gt",
      "description": "Vcf Adapter test dataset which includes sample / gt info",
      "assemblyId": "test-assembly",
      "createDateTime": "2016/07/23 19:23:11",
      "updateDateTime": "2016/07/23 19:23:11",
      "version": "1",
      "variantCount": 26,
      "sampleCount": 1,
      "externalUrl": "http://localhost:8089/"
    }
  ],
  "sampleAlleleRequests": [
    {
      "referenceName": "test-reference-name",
      "start": 100,
      "referenceBases": "T",
      "alternateBases": "C",
      "assemblyId": "grch37",
      "datasetIds": [
        "vcf-test-gt"
      ],
      "includeDatasetResponses": true
    },
    {
      "referenceName": "1",
      "start": 10109,
      "referenceBases": "A",
      "alternateBases": "T",
      "assemblyId": "grch37",
      "datasetIds": [
        "vcf-test-no-gt"
      ],
      "includeDatasetResponses": true
    }
  ]
}
```