# Beacon adapter for GA4GH Variants API [![Build Status](https://travis-ci.org/mcupak/beacon-adapter-variants.svg?branch=develop)](https://travis-ci.org/mcupak/beacon-adapter-variants) [![GitHub license](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://raw.githubusercontent.com/mcupak/beacon-adapter-variants/develop/LICENSE)

Beacon variants adapter is an implementation of the [Beacon Adapter API](https://github.com/mcupak/beacon-adapter-api). This adapter wraps [GA4GH Variants API](http://ga4gh-schemas.readthedocs.io/en/latest/api/variants.html) and allows you to turn the API into a beacon via a compatible Beacon implementation, such as [JBDK](https://github.com/mcupak/beacon-java).

Prerequisites: Java 8, [GA4GH schemas](https://github.com/ga4gh/schemas/releases/tag/v0.6.0a8).

## Configuring the Adapter

In order to properly configure the adapter you must call the initAdapter method from the VariantsBeaconAdapter class, supplying it with an AdapterConfig object once a new adapter object has been created.
There is one required parameter for the configuration that must be supplied as ConfigValues to the AdapterConfig object:

#### Required one of the following
| Name | Value |
|--- | ---|
| "beaconJsonFile" | Path to a JSON file that describes this beacon. |
| "beaconJson" | JSON string that describes this beacon |
