package com.dnastack.beacon.adater.variants;

import com.dnastack.beacon.adapter.api.BeaconAdapter;
import com.dnastack.beacon.adater.variants.client.ga4gh.Ga4ghClient;
import com.dnastack.beacon.adater.variants.client.ga4gh.exceptions.Ga4ghClientException;
import com.dnastack.beacon.exceptions.BeaconAlleleRequestException;
import com.dnastack.beacon.exceptions.BeaconException;
import com.dnastack.beacon.utils.AdapterConfig;
import com.dnastack.beacon.utils.ConfigValue;
import com.dnastack.beacon.utils.Reason;
import com.google.common.collect.Iterables;
import ga4gh.Metadata.Dataset;
import ga4gh.References.ReferenceSet;
import ga4gh.Variants.Call;
import ga4gh.Variants.CallSet;
import ga4gh.Variants.Variant;
import ga4gh.Variants.VariantSet;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.ga4gh.beacon.*;

import javax.enterprise.context.Dependent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Exposes Ga4gh variants as a Beacon.
 * Queries the underlying Ga4gh client and assembles Beacon responses.
 *
 * @author Artem (tema.voskoboynick@gmail.com)
 * @author Miro Cupak (mirocupak@gmail.com)
 * @version 1.0
 */
@Dependent
public class VariantsBeaconAdapter implements BeaconAdapter {

    public static final Beacon SAMPLE_BEACON = Beacon.newBuilder()
                                                     .setId("sample-beacon")
                                                     .setName("Sample Beacon")
                                                     .setApiVersion("0.3.0")
                                                     .setCreateDateTime("01.01.2016")
                                                     .setUpdateDateTime("01.01.2016")
                                                     .setOrganization(BeaconOrganization.newBuilder()
                                                                                        .setId("sample-organization")
                                                                                        .setName("Sample Organization")
                                                                                        .build())
                                                     .build();

    private Ga4ghClient ga4ghClient = new Ga4ghClient();

    /**
     * Copy of the the Java 8 function, but can throw {@link BeaconAlleleRequestException}.
     */
    @FunctionalInterface
    public interface FunctionThrowingAlleleRequestException<T, R> {

        R apply(T t) throws BeaconAlleleRequestException;
    }

    /**
     * Copy of the the Java 8 predicate, but can throw {@link BeaconAlleleRequestException}.
     */
    @FunctionalInterface
    public interface PredicateThrowingAlleleRequestException<T> {

        boolean test(T t) throws BeaconAlleleRequestException;
    }

    private void initGa4ghClient(AdapterConfig adapterConfig) {
        String ga4ghBaseUrl = getGa4ghBaseUrl(adapterConfig);
        ga4ghClient = ga4ghBaseUrl == null ? new Ga4ghClient() : new Ga4ghClient(ga4ghBaseUrl);
    }

    private String getGa4ghBaseUrl(AdapterConfig adapterConfig) {
        List<ConfigValue> configValues = adapterConfig.getConfigValues();
        ConfigValue ga4ghBaseUrl = Iterables.getFirst(configValues, null);
        return ga4ghBaseUrl == null ? null : ga4ghBaseUrl.getValue();
    }

    private BeaconAlleleRequest createRequest(String referenceName, Long start, String referenceBases, String alternateBases, String assemblyId, List<String> datasetIds, Boolean includeDatasetResponses) {
        return BeaconAlleleRequest.newBuilder()
                                  .setReferenceName(referenceName)
                                  .setStart(start)
                                  .setReferenceBases(referenceBases)
                                  .setAlternateBases(alternateBases)
                                  .setAssemblyId(assemblyId)
                                  .setDatasetIds(datasetIds)
                                  .setIncludeDatasetResponses(includeDatasetResponses)
                                  .build();
    }

    private BeaconAlleleResponse doGetBeaconAlleleResponse(String referenceName, Long start, String referenceBases, String alternateBases, String assemblyId, List<String> datasetIds, Boolean includeDatasetResponses) throws BeaconException {
        List<String> datasetIdsToSearch = getDatasetIdsToSearch(datasetIds);

        List<BeaconDatasetAlleleResponse> datasetResponses = map(datasetIdsToSearch,
                                                                 datasetId -> getDatasetResponse(datasetId,
                                                                                                 assemblyId,
                                                                                                 referenceName,
                                                                                                 start,
                                                                                                 referenceBases,
                                                                                                 alternateBases));

        List<BeaconDatasetAlleleResponse> returnedDatasetResponses = BooleanUtils.isTrue(includeDatasetResponses)
                                                                     ? datasetResponses
                                                                     : null;

        BeaconError anyError = datasetResponses.stream()
                                               .map(BeaconDatasetAlleleResponse::getError)
                                               .filter(error -> error != null)
                                               .findAny()
                                               .orElseGet(() -> null);

        Boolean exists = anyError != null
                         ? null
                         : datasetResponses.stream().anyMatch(BeaconDatasetAlleleResponse::getExists);

        BeaconAlleleResponse response = BeaconAlleleResponse.newBuilder()
                                                            .setAlleleRequest(null)
                                                            .setDatasetAlleleResponses(returnedDatasetResponses)
                                                            .setBeaconId(SAMPLE_BEACON.getId())
                                                            .setError(anyError)
                                                            .setExists(exists)
                                                            .build();

        return response;
    }

    private BeaconDatasetAlleleResponse getDatasetResponse(String datasetId, String assemblyId, String referenceName, long start, String referenceBases, String alternateBases) throws BeaconAlleleRequestException {
        List<VariantSet> variantSets = getVariantSetsToSearch(datasetId, assemblyId);

        List<Variant> variants = map(variantSets,
                                     variantSet -> loadVariants(variantSet.getId(), referenceName, start)).stream()
                                                                                                          .flatMap(
                                                                                                                  Collection::stream)
                                                                                                          .filter(variant -> isVariantMatchesRequested(
                                                                                                                  variant,
                                                                                                                  referenceBases,
                                                                                                                  alternateBases))
                                                                                                          .collect(
                                                                                                                  Collectors
                                                                                                                          .toList());

        Double frequency = calculateFrequency(alternateBases, variants);

        Long sampleCount = countSamples(variants);

        Long callsCount = variants.stream().collect(Collectors.summingLong(Variant::getCallsCount));

        long variantCount = (long) variants.size();

        boolean exists = CollectionUtils.isNotEmpty(variants);

        BeaconDatasetAlleleResponse datasetResponse = BeaconDatasetAlleleResponse.newBuilder()
                                                                                 .setDatasetId(datasetId)
                                                                                 .setFrequency(frequency)
                                                                                 .setCallCount(callsCount)
                                                                                 .setVariantCount(variantCount)
                                                                                 .setSampleCount(sampleCount)
                                                                                 .setExists(exists)
                                                                                 .build();
        return datasetResponse;
    }

    private Long countSamples(List<Variant> variants) throws BeaconAlleleRequestException {
        Stream<String> callSetIds = variants.stream()
                                            .flatMap(variant -> variant.getCallsList().stream())
                                            .map(Call::getCallSetId);

        long sampleCount = map(callSetIds, this::loadCallSet).stream().map(CallSet::getBioSampleId).distinct().count();

        return sampleCount;
    }

    private Double calculateFrequency(String alternateBases, List<Variant> variants) {
        Long matchingGenotypesCount = variants.stream()
                                              .mapToLong(variant -> calculateMatchingGenotypesCount(variant,
                                                                                                    alternateBases))
                                              .sum();

        Long totalGenotypesCount = variants.stream()
                                           .flatMap(variant -> variant.getCallsList().stream())
                                           .collect(Collectors.summingLong(Call::getGenotypeCount));

        return (totalGenotypesCount == 0) ? null : ((double) matchingGenotypesCount / totalGenotypesCount);
    }

    private long calculateMatchingGenotypesCount(Variant variant, String alternateBases) {
        int requestedGenotype = variant.getAlternateBasesList().indexOf(alternateBases) + 1;
        return variant.getCallsList()
                      .stream()
                      .map(Call::getGenotypeList)
                      .flatMap(List::stream)
                      .filter(genotype -> genotype.equals(requestedGenotype))
                      .count();
    }

    private List<VariantSet> getVariantSetsToSearch(String datasetId, String assemblyId) throws BeaconAlleleRequestException {
        List<VariantSet> variantSets = loadVariantSets(datasetId);
        filter(variantSets, variantSet -> isVariantSetMatchesRequested(variantSet, assemblyId));
        return variantSets;
    }

    private boolean isVariantSetMatchesRequested(VariantSet variantSet, String assemblyId) throws BeaconAlleleRequestException {
        ReferenceSet referenceSet = loadReferenceSet(variantSet.getReferenceSetId());
        return StringUtils.equals(referenceSet.getAssemblyId(), assemblyId);
    }

    private boolean isVariantMatchesRequested(Variant variant, String referenceBases, String alternateBases) {
        return StringUtils.equals(variant.getReferenceBases(), referenceBases) && variant.getAlternateBasesList()
                                                                                         .contains(alternateBases);
    }

    private List<String> getDatasetIdsToSearch(List<String> requestedDatasetIds) throws BeaconAlleleRequestException {
        return CollectionUtils.isNotEmpty(requestedDatasetIds) ? requestedDatasetIds : loadAllDatasetIds();
    }

    private List<String> loadAllDatasetIds() throws BeaconAlleleRequestException {
        List<Dataset> allDatasets = loadAllDatasets();

        List<String> allDatasetIds = allDatasets.stream().map(Dataset::getId).collect(Collectors.toList());
        return allDatasetIds;
    }

    private List<Dataset> loadAllDatasets() throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.searchDatasets();
        } catch (Ga4ghClientException e) {
            BeaconAlleleRequestException alleleRequestException = new BeaconAlleleRequestException(
                    "Couldn't load all datasets.",
                    Reason.CONN_ERR,
                    null);
            alleleRequestException.initCause(e);
            throw alleleRequestException;
        }
    }

    private List<Variant> loadVariants(String variantSetId, String referenceName, long start) throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.searchVariants(variantSetId, referenceName, start);
        } catch (Ga4ghClientException e) {
            BeaconAlleleRequestException alleleRequestException = new BeaconAlleleRequestException(
                    "Couldn't load reference set with id %s.",
                    Reason.CONN_ERR,
                    null);
            alleleRequestException.initCause(e);
            throw alleleRequestException;
        }
    }

    private List<VariantSet> loadVariantSets(String datasetId) throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.searchVariantSets(datasetId);
        } catch (Ga4ghClientException e) {
            BeaconAlleleRequestException alleleRequestException = new BeaconAlleleRequestException(String.format(
                    "Couldn't load all variant sets for dataset id %s.",
                    datasetId), Reason.CONN_ERR, null);
            alleleRequestException.initCause(e);
            throw alleleRequestException;
        }
    }

    private ReferenceSet loadReferenceSet(String id) throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.loadReferenceSet(id);
        } catch (Ga4ghClientException e) {
            BeaconAlleleRequestException alleleRequestException = new BeaconAlleleRequestException(
                    "Couldn't load reference set with id %s.",
                    Reason.CONN_ERR,
                    null);
            alleleRequestException.initCause(e);
            throw alleleRequestException;
        }
    }

    private CallSet loadCallSet(String id) throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.loadCallSet(id);
        } catch (Ga4ghClientException e) {
            BeaconAlleleRequestException alleleRequestException = new BeaconAlleleRequestException(
                    "Couldn't load call set with id %s.",
                    Reason.CONN_ERR,
                    null);
            alleleRequestException.initCause(e);
            throw alleleRequestException;
        }
    }

    /**
     * Works the same way as the Java 8 filter API map method, but can throw {@link BeaconAlleleRequestException}.
     */
    private <T> void filter(Collection<T> collection, PredicateThrowingAlleleRequestException<? super T> filter) throws BeaconAlleleRequestException {
        Iterator<T> it = collection.iterator();

        while (it.hasNext()) {
            T item = it.next();

            if (!filter.test(item)) {
                it.remove();
            }
        }
    }

    @Override
    public void initAdapter(AdapterConfig adapterConfig) {
        initGa4ghClient(adapterConfig);
    }

    @Override
    public Beacon getBeacon() throws BeaconException {
        return SAMPLE_BEACON;
    }

    @Override
    public BeaconAlleleResponse getBeaconAlleleResponse(BeaconAlleleRequest request) throws BeaconException {
        try {
            BeaconAlleleResponse response = doGetBeaconAlleleResponse(request.getReferenceName(),
                                                                      request.getStart(),
                                                                      request.getReferenceBases(),
                                                                      request.getAlternateBases(),
                                                                      request.getAssemblyId(),
                                                                      request.getDatasetIds(),
                                                                      request.getIncludeDatasetResponses());
            response.setAlleleRequest(request);
            return response;
        } catch (BeaconAlleleRequestException e) {
            e.setRequest(request);
            throw e;
        }
    }

    @Override
    public BeaconAlleleResponse getBeaconAlleleResponse(String referenceName, Long start, String referenceBases, String alternateBases, String assemblyId, List<String> datasetIds, Boolean includeDatasetResponses) throws BeaconException {
        BeaconAlleleRequest request = createRequest(referenceName,
                                                    start,
                                                    referenceBases,
                                                    alternateBases,
                                                    assemblyId,
                                                    datasetIds,
                                                    includeDatasetResponses);
        return getBeaconAlleleResponse(request);
    }

    /**
     * Works the same way as the Java 8 stream API map method, but can throw {@link BeaconAlleleRequestException}.
     */
    public <T, R> List<R> map(List<T> list, FunctionThrowingAlleleRequestException<? super T, ? extends R> mapper) throws BeaconAlleleRequestException {
        List<R> result = new ArrayList<>();

        for (T item : list) {
            R mapped = mapper.apply(item);
            result.add(mapped);
        }

        return result;
    }

    /**
     * Works the same way as the Java 8 stream API map method, but can throw {@link BeaconAlleleRequestException}.
     */
    public <T, R> List<R> map(Stream<T> stream, FunctionThrowingAlleleRequestException<? super T, ? extends R> mapper) throws BeaconAlleleRequestException {
        List<R> result = new ArrayList<>();

        Iterator<T> it = stream.iterator();
        while (it.hasNext()) {
            R mappedItem = mapper.apply(it.next());
            result.add(mappedItem);
        }

        return result;
    }
}
