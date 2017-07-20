package com.dnastack.beacon.adater.variants;

import com.dnastack.beacon.adapter.api.BeaconAdapter;
import com.dnastack.beacon.adater.variants.client.ga4gh.Ga4ghClient;
import com.dnastack.beacon.adater.variants.client.ga4gh.exceptions.Ga4ghClientException;
import com.dnastack.beacon.exceptions.BeaconAlleleRequestException;
import com.dnastack.beacon.exceptions.BeaconException;
import com.dnastack.beacon.utils.AdapterConfig;
import com.dnastack.beacon.utils.ConfigValue;
import com.dnastack.beacon.utils.Reason;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.protobuf.ListValue;
import ga4gh.Metadata.Dataset;
import ga4gh.References.ReferenceSet;
import ga4gh.Variants.Call;
import ga4gh.Variants.CallSet;
import ga4gh.Variants.Variant;
import ga4gh.Variants.VariantSet;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.ga4gh.beacon.*;

import javax.enterprise.context.Dependent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
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

    private Ga4ghClient ga4ghClient;

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
        List<ConfigValue> configValues = adapterConfig.getConfigValues();
        Beacon beacon = null;

        for (ConfigValue configValue : configValues) {
            switch (configValue.getName()) {
                case "beaconJsonFile":
                    beacon = readBeaconJsonFile(configValue.getValue());
                    break;
                case "beaconJson":
                    beacon = readBeaconJson(configValue.getValue());
                    break;
            }
        }

        if (beacon == null) {
            throw new RuntimeException(
                    "Missing required parameter: beaconJson. Please add the appropriate configuration paramter then retry");
        }

        ga4ghClient = new Ga4ghClient(beacon);
    }

    private Beacon readBeaconJsonFile(String filename) {
        File beaconJsonFile = new File(filename);
        if (!beaconJsonFile.exists()) {
            throw new RuntimeException("BeaconJson file does not exist");
        }
        try {

            String beaconJson = new String(Files.readAllBytes(beaconJsonFile.toPath()));
            return readBeaconJson(beaconJson);

        } catch (IOException e) {
            throw new RuntimeException(e.getMessage());
        }
    }

    private Beacon readBeaconJson(String json) {
        Gson gson = new GsonBuilder().create();
        return gson.fromJson(json, Beacon.class);
    }

    private BeaconAlleleRequest createRequest(String referenceName,
                                              Long start,
                                              String referenceBases,
                                              String alternateBases,
                                              String assemblyId,
                                              List<String> datasetIds,
                                              Boolean includeDatasetResponses) {
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

    private BeaconAlleleResponse doGetBeaconAlleleResponse(String referenceName,
                                                           Long start,
                                                           String referenceBases,
                                                           String alternateBases,
                                                           String assemblyId,
                                                           List<String> datasetIds,
                                                           Boolean includeDatasetResponses) throws BeaconException {
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
                .filter(Objects::nonNull)
                .findAny()
                .orElse(null);

        Boolean exists = anyError != null
                ? null
                : datasetResponses.stream().anyMatch(BeaconDatasetAlleleResponse::getExists);

        return BeaconAlleleResponse.newBuilder()
                .setAlleleRequest(null)
                .setDatasetAlleleResponses(returnedDatasetResponses)
                .setBeaconId(getBeacon().getId())
                .setError(anyError)
                .setExists(exists)
                .build();
    }

    private BeaconDatasetAlleleResponse getDatasetResponse(String datasetId,
                                                           String assemblyId,
                                                           String referenceName,
                                                           long start,
                                                           String referenceBases,
                                                           String alternateBases) throws BeaconAlleleRequestException {
        List<VariantSet> variantSets = getVariantSetsToSearch(datasetId, assemblyId);

        List<Variant> variants = map(variantSets,
                variantSet -> loadVariants(datasetId, variantSet.getId(), referenceName, start)).stream()
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

        Long sampleCount = countSamples(datasetId, variants);

        Long callsCount = variants.stream().mapToLong(Variant::getCallsCount).sum();

        long variantCount = (long) variants.size();

        boolean exists = CollectionUtils.isNotEmpty(variants);

        return BeaconDatasetAlleleResponse.newBuilder()
                .setDatasetId(datasetId)
                .setFrequency(frequency)
                .setCallCount(callsCount)
                .setVariantCount(variantCount)
                .setSampleCount(sampleCount)
                .setExists(exists)
                .build();
    }

    private Long countSamples(String datasetId, List<Variant> variants) throws BeaconAlleleRequestException {
        List<String> callSetIds = variants.stream()
                .flatMap(variant -> variant.getCallsList().stream())
                .map(Call::getCallSetId)
                .collect(Collectors.toList());

        List<CallSet> callSets = new ArrayList<>();

        for (String callSetId : callSetIds) {
            callSets.add(loadCallSet(datasetId, callSetId));
        }

        return callSets.stream().map(CallSet::getBiosampleId).distinct().count();
    }

    private Double calculateFrequency(String alternateBases, List<Variant> variants) {
        Long matchingGenotypesCount = variants.stream()
                .mapToLong(variant -> calculateMatchingGenotypesCount(variant,
                        alternateBases))
                .sum();

        Long totalGenotypesCount = variants.stream()
                .flatMap(variant -> variant.getCallsList().stream())
                .map(Call::getGenotype)
                .mapToLong(ListValue::getValuesCount).sum();

        return (totalGenotypesCount == 0) ? null : ((double) matchingGenotypesCount / totalGenotypesCount);
    }

    private long calculateMatchingGenotypesCount(Variant variant, String alternateBases) {
        int requestedGenotype = variant.getAlternateBasesList().indexOf(alternateBases) + 1;
        return variant.getCallsList()
                .stream()
                .map(Call::getGenotype)
                .flatMap(listValue -> listValue.getValuesList().stream())
                .filter(genotype -> genotype.getNumberValue() == (double) requestedGenotype)
                .count();
    }

    private List<VariantSet> getVariantSetsToSearch(String datasetId, String assemblyId) throws BeaconAlleleRequestException {
        List<VariantSet> variantSets = loadVariantSets(datasetId);
        filter(variantSets, variantSet -> isVariantSetMatchesRequested(datasetId, variantSet, assemblyId));
        return variantSets;
    }

    private boolean isVariantSetMatchesRequested(String datasetId, VariantSet variantSet, String assemblyId) throws BeaconAlleleRequestException {
        ReferenceSet referenceSet = loadReferenceSet(datasetId, variantSet.getReferenceSetId());
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

        return allDatasets.stream().map(Dataset::getId).collect(Collectors.toList());
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

    private List<Variant> loadVariants(String datasetId, String variantSetId, String referenceName, long start) throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.searchVariants(datasetId, variantSetId, referenceName, start);
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

    private ReferenceSet loadReferenceSet(String datasetId, String referenceSetId) throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.loadReferenceSet(datasetId ,referenceSetId);
        } catch (Ga4ghClientException e) {
            BeaconAlleleRequestException alleleRequestException = new BeaconAlleleRequestException(
                    "Couldn't load reference set with id %s.",
                    Reason.CONN_ERR,
                    null);
            alleleRequestException.initCause(e);
            throw alleleRequestException;
        }
    }

    private CallSet loadCallSet(String datasetId, String callSetId) throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.loadCallSet(datasetId, callSetId);
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

    private void checkAdapterInit() {
        if (ga4ghClient == null || ga4ghClient.getBeacon() == null) {
            throw new IllegalStateException(
                    "VariantsBeaconAdapter adapter has not been initialized");
        }
    }

    @Override
    public void initAdapter(AdapterConfig adapterConfig) {
        initGa4ghClient(adapterConfig);
    }

    @Override
    public Beacon getBeacon() throws BeaconException {
        checkAdapterInit();
        return ga4ghClient.getBeacon();
    }

    @Override
    public BeaconAlleleResponse getBeaconAlleleResponse(BeaconAlleleRequest request) throws BeaconException {
        checkAdapterInit();
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
    public BeaconAlleleResponse getBeaconAlleleResponse(String referenceName, Long start, String referenceBases,
                                                        String alternateBases, String assemblyId, List<String> datasetIds,
                                                        Boolean includeDatasetResponses) throws BeaconException {
        checkAdapterInit();
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
