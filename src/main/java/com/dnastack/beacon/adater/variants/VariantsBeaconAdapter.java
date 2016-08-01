package com.dnastack.beacon.adater.variants;

import com.dnastack.beacon.adapter.api.BeaconAdapter;
import com.dnastack.beacon.adater.variants.client.ga4gh.Ga4ghClient;
import com.dnastack.beacon.adater.variants.client.ga4gh.exceptions.Ga4ghClientException;
import com.dnastack.beacon.exceptions.BeaconAlleleRequestException;
import com.dnastack.beacon.exceptions.BeaconException;
import com.dnastack.beacon.utils.Reason;
import ga4gh.Metadata.Dataset;
import ga4gh.References.ReferenceSet;
import ga4gh.Variants.Call;
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

/**
 * @author Artem (tema.voskoboynick@gmail.com)
 * @version 1.0
 */
@Dependent
public class VariantsBeaconAdapter implements BeaconAdapter {

    private Ga4ghClient ga4ghClient = new Ga4ghClient();

    @Override
    public BeaconAlleleResponse getBeaconAlleleResponse(BeaconAlleleRequest request) throws BeaconException {
        try {
            return doGetBeaconAlleleResponse(request);
        } catch (BeaconAlleleRequestException e) {
            //e.setRequest(request);
            throw e;
        }
    }

    @Override
    public Beacon getBeacon() throws BeaconException {
        return null;
    }

    private BeaconAlleleResponse doGetBeaconAlleleResponse(BeaconAlleleRequest request) throws BeaconAlleleRequestException {
        List<String> datasetIds = getDatasetIdsToSearch(request.getDatasetIds());

        List<BeaconDatasetAlleleResponse> datasetResponses = map(datasetIds,
                datasetId -> getDatasetResponse(datasetId, request.getAssemblyId(), request.getReferenceName(),
                        request.getStart(), request.getReferenceBases(), request.getAlternateBases())
        );

        List<BeaconDatasetAlleleResponse> returnedDatasetResponses = BooleanUtils
                .isTrue(request.getIncludeDatasetResponses()) ? datasetResponses : null;

        BeaconError anyError = datasetResponses
                .stream()
                .map(BeaconDatasetAlleleResponse::getError)
                .filter(error -> error != null)
                .findAny()
                .orElseGet(() -> null);

        Boolean exists = anyError != null ? null : datasetResponses
                .stream()
                .anyMatch(BeaconDatasetAlleleResponse::getExists);

        BeaconAlleleResponse response = BeaconAlleleResponse.newBuilder()
                .setAlleleRequest(request)
                .setDatasetAlleleResponses(returnedDatasetResponses)
                .setBeaconId("")
                .setError(anyError)
                .setExists(exists)
                .build();
        System.out.println(response);
        return response;
    }

    private BeaconDatasetAlleleResponse getDatasetResponse(String datasetId, String assemblyId, String referenceName,
                                                           long start, String referenceBases, String alternateBases) throws BeaconAlleleRequestException {
        List<VariantSet> variantSets = getVariantSetsToSearch(datasetId, assemblyId);

        List<Variant> variants = map(variantSets, variantSet -> loadVariants(variantSet.getId(), referenceName, start))
                .stream()
                .flatMap(Collection::stream)
                .filter(variant -> isVariantMatchesRequested(variant, referenceBases, alternateBases))
                .collect(Collectors.toList());

        Double frequency = calculateFrequency(alternateBases, variants);

        Long callsCount = variants.stream().collect(Collectors.summingLong(Variant::getCallsCount));

        long variantCount = (long) variants.size();

        boolean exists = CollectionUtils.isNotEmpty(variants);

        BeaconDatasetAlleleResponse datasetResponse = BeaconDatasetAlleleResponse.newBuilder()
                .setDatasetId(datasetId)
                .setFrequency(frequency)
                .setCallCount(callsCount)
                .setVariantCount(variantCount)
                .setExists(exists)
                .build();
        return datasetResponse;
    }

    private Double calculateFrequency(String alternateBases, List<Variant> variants) {
        Long matchingGenotypesCount = variants
                .stream()
                .mapToLong(variant -> calculateMatchingGenotypesCount(variant, alternateBases))
                .sum();

        Long totalGenotypesCount = variants
                .stream()
                .flatMap(variant -> variant
                        .getCallsList()
                        .stream())
                .collect(Collectors.summingLong(Call::getGenotypeCount));

        return (totalGenotypesCount == 0) ? null : ((double) matchingGenotypesCount / totalGenotypesCount);
    }

    private long calculateMatchingGenotypesCount(Variant variant, String alternateBases) {
        int requestedGenotype = variant.getAlternateBasesList().indexOf(alternateBases) + 1;
        return variant
                .getCallsList()
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
        return StringUtils.equals(referenceSet.getAssemblyId(), assemblyId) ||
                StringUtils.equals(referenceSet.getName(), assemblyId);
    }

    private boolean isVariantMatchesRequested(Variant variant, String referenceBases, String alternateBases) {
        return StringUtils.equals(variant.getReferenceBases(), referenceBases) &&
                variant.getAlternateBasesList().contains(alternateBases);
    }

    private List<String> getDatasetIdsToSearch(List<String> requestedDatasetIds) throws BeaconAlleleRequestException {
        return CollectionUtils.isNotEmpty(requestedDatasetIds) ? requestedDatasetIds : loadAllDatasetIds();
    }

    private List<String> loadAllDatasetIds() throws BeaconAlleleRequestException {
        List<Dataset> allDatasets = loadAllDatasets();

        List<String> allDatasetIds = allDatasets
                .stream()
                .map(Dataset::getId)
                .collect(Collectors.toList());
        return allDatasetIds;
    }

    private List<Dataset> loadAllDatasets() throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.searchDatasets();
        } catch (Ga4ghClientException e) {
            BeaconAlleleRequestException alleleRequestException = new BeaconAlleleRequestException("Couldn't load all datasets.", Reason.CONN_ERR, null);
            alleleRequestException.initCause(e);
            throw alleleRequestException;
        }
    }

    private List<Variant> loadVariants(String variantSetId, String referenceName, long start) throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.searchVariants(variantSetId, referenceName, start);
        } catch (Ga4ghClientException e) {
            BeaconAlleleRequestException alleleRequestException = new BeaconAlleleRequestException("Couldn't load reference set with id %s.", Reason.CONN_ERR, null);
            alleleRequestException.initCause(e);
            throw alleleRequestException;
        }
    }

    private List<VariantSet> loadVariantSets(String datasetId) throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.searchVariantSets(datasetId);
        } catch (Ga4ghClientException e) {
            BeaconAlleleRequestException alleleRequestException =
                    new BeaconAlleleRequestException(String.format("Couldn't load all variant sets for dataset id %s.", datasetId), Reason.CONN_ERR, null);
            alleleRequestException.initCause(e);
            throw alleleRequestException;
        }
    }

    private ReferenceSet loadReferenceSet(String id) throws BeaconAlleleRequestException {
        try {
            return ga4ghClient.loadReferenceSet(id);
        } catch (Ga4ghClientException e) {
            BeaconAlleleRequestException alleleRequestException = new BeaconAlleleRequestException("Couldn't load reference set with id %s.", Reason.CONN_ERR, null);
            alleleRequestException.initCause(e);
            throw alleleRequestException;
        }
    }

    public final <P_OUT, R> List<R> map(List<P_OUT> list,
                                        FunctionThrowingAlleleRequestException<? super P_OUT, ? extends R> mapper) throws BeaconAlleleRequestException {
        List<R> result = new ArrayList<>();

        for (P_OUT aList : list) {
            result.add(mapper.apply(aList));
        }

        return result;
    }

    private <T> void filter(Collection<T> collection, PredicateThrowingAlleleRequestException<? super T> filter) throws BeaconAlleleRequestException {
        Iterator<T> each = collection.iterator();
        while (each.hasNext()) {
            if (!filter.test(each.next())) {
                each.remove();
            }
        }
    }

    @FunctionalInterface
    public interface FunctionThrowingAlleleRequestException<T, R> {

        R apply(T t) throws BeaconAlleleRequestException;
    }


    @FunctionalInterface
    public interface PredicateThrowingAlleleRequestException<T> {

        boolean test(T t) throws BeaconAlleleRequestException;
    }


    public static void main(String[] args) throws BeaconException {
        VariantsBeaconAdapter adapter = new VariantsBeaconAdapter();
        BeaconAlleleRequest request = BeaconAlleleRequest.newBuilder()
                .setReferenceName("1")
                .setStart(0)
                .setReferenceBases("")
                .setAlternateBases("")
                .setAssemblyId("NCBI37")
                .build();
        adapter.getBeaconAlleleResponse(request);
    }
}
