package com.dnastack.beacon.adapter.variants.tests.error

import com.dnastack.beacon.adapter.variants.BaseTest
import com.dnastack.beacon.exceptions.BeaconException
import org.ga4gh.beacon.BeaconAlleleRequest
import org.ga4gh.beacon.BeaconAlleleResponse

import static com.dnastack.beacon.adapter.variants.TestData.*
import static org.assertj.core.api.Assertions.assertThat
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown

/**
 * @author Artem (tema.voskoboynick@gmail.com)
 * @version 1.0
 */
class BeaconResponseUnknownDataset extends BaseTest {

    /**
     * Test that requesting unknown datasets causes {@link BeaconException}.
     */
    @Override
    void doTest() {
        def referenceName = SEARCH_VARIANTS_TEST_REQUEST.referenceName
        def start = SEARCH_VARIANTS_TEST_REQUEST.start
        def referenceBases = TEST_VARIANT.referenceBases
        def alternateBases = TEST_VARIANT.getAlternateBases(0)
        def assemblyId = TEST_REFERENCE_SET.assemblyId
        def datasetIds = ["unknown-dataset"]
        def includeDatasetResponses = true

        BeaconAlleleRequest request = BeaconAlleleRequest.newBuilder()
                .setReferenceName(referenceName)
                .setStart(start)
                .setReferenceBases(referenceBases)
                .setAlternateBases(alternateBases)
                .setAssemblyId(assemblyId)
                .setDatasetIds(datasetIds)
                .setIncludeDatasetResponses(includeDatasetResponses)
                .build();

        testPostMethod(request)
        testGetMethod(request)
    }

    private void testGetMethod(BeaconAlleleRequest request) {
        try {
            ADAPTER.getBeaconAlleleResponse(
                    request.getReferenceName(),
                    request.getStart(),
                    request.getReferenceBases(),
                    request.getAlternateBases(),
                    request.getAssemblyId(),
                    request.getDatasetIds(),
                    request.getIncludeDatasetResponses());
            failBecauseExceptionWasNotThrown(BeaconException.class);
        } catch (BeaconException e) {
            def unknownDatasetId = request.getDatasetIds().get(0)
            assertThat(e).hasMessageContaining(unknownDatasetId);
        }
    }

    private void testPostMethod(BeaconAlleleRequest request) {
        try {
            ADAPTER.getBeaconAlleleResponse(request);
            failBecauseExceptionWasNotThrown(BeaconException.class);
        } catch (BeaconException e) {
            def unknownDatasetId = request.getDatasetIds().get(0)
            assertThat(e).hasMessageContaining(unknownDatasetId);
        }
    }
}