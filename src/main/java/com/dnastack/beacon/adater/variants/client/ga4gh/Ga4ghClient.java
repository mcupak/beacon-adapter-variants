package com.dnastack.beacon.adater.variants.client.ga4gh;

import com.dnastack.beacon.adater.variants.client.ga4gh.exceptions.Ga4ghClientException;
import com.dnastack.beacon.adater.variants.client.ga4gh.retro.Ga4ghRetroService;
import com.dnastack.beacon.adater.variants.client.ga4gh.retro.Ga4ghRetroServiceFactory;
import com.google.protobuf.GeneratedMessage;
import com.google.protobuf.Message.Builder;
import ga4gh.Metadata.Dataset;
import ga4gh.MetadataServiceOuterClass.SearchDatasetsRequest;
import ga4gh.MetadataServiceOuterClass.SearchDatasetsResponse;
import ga4gh.References.ReferenceSet;
import ga4gh.VariantServiceOuterClass.SearchVariantSetsRequest;
import ga4gh.VariantServiceOuterClass.SearchVariantSetsResponse;
import ga4gh.VariantServiceOuterClass.SearchVariantsRequest;
import ga4gh.VariantServiceOuterClass.SearchVariantsResponse;
import ga4gh.Variants.Variant;
import ga4gh.Variants.VariantSet;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import retrofit2.Call;
import retrofit2.Response;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Artem (tema.voskoboynick@gmail.com)
 * @version 1.0
 */
public class Ga4ghClient {

    private Ga4ghRetroService ga4ghRetroService = Ga4ghRetroServiceFactory.create("http://1kgenomes.ga4gh.org/");

    public List<Dataset> searchDatasets() throws Ga4ghClientException {
        SearchDatasetsRequest request = SearchDatasetsRequest.newBuilder().build();

        List<SearchDatasetsResponse> allResponsePages = requestAllResponsePages(request,
                pagedRequest -> executeCall(ga4ghRetroService.searchDatasets(pagedRequest)));

        List<Dataset> allDatasets = allResponsePages
                .stream()
                .flatMap(responsePage -> responsePage
                                .getDatasetsList()
                                .stream()
                )
                .collect(Collectors.toList());
        return allDatasets;
    }

    public List<Variant> searchVariants(String variantSetId, String referenceName, long start) throws Ga4ghClientException {
        SearchVariantsRequest request = SearchVariantsRequest.newBuilder()
                .setVariantSetId(variantSetId)
                .setReferenceName(referenceName)
                .setStart(start)
                .setEnd(start + 1)
                .build();

        List<SearchVariantsResponse> allResponsePages = requestAllResponsePages(request,
                pagedRequest -> executeCall(ga4ghRetroService.searchVariants(pagedRequest)));

        List<Variant> variants = allResponsePages
                .stream()
                .flatMap(responsePage -> responsePage
                                .getVariantsList()
                                .stream()
                )
                .collect(Collectors.toList());
        return variants;
    }

    public List<VariantSet> searchVariantSets(String datasetId) throws Ga4ghClientException {
        SearchVariantSetsRequest request = SearchVariantSetsRequest.newBuilder()
                .setDatasetId(datasetId)
                .build();

        List<SearchVariantSetsResponse> allResponsePages = requestAllResponsePages(request,
                pagedRequest -> executeCall(ga4ghRetroService.searchVariantSets(pagedRequest)));

        List<VariantSet> variantSets = allResponsePages
                .stream()
                .flatMap(responsePage -> responsePage
                                .getVariantSetsList()
                                .stream()
                )
                .collect(Collectors.toList());
        return variantSets;
    }

    public ReferenceSet loadReferenceSet(String id) throws Ga4ghClientException {
        return executeCall(ga4ghRetroService.loadReferenceSet(id));
    }

    private <T> T executeCall(Call<T> call) throws Ga4ghClientException {
        Response<T> response;
        try {
            response = call.execute();
        } catch (IOException | RuntimeException e) {
            throw new Ga4ghClientException("Error during communication to server.", e);
        }

        if (response.isSuccessful()) {
            return response.body();
        } else {
            throw new Ga4ghClientException(String.format("Received error response from server. HTTP code: %s", response.code()));
        }
    }

    private <REQUEST extends GeneratedMessage, RESPONSE> List<RESPONSE> requestAllResponsePages(REQUEST request,
                                                                                                RequestExecutor<REQUEST, RESPONSE> requestExecutor) throws Ga4ghClientException {
        List<RESPONSE> responsePages = new ArrayList<>();

        Builder requestBuilder = request.toBuilder();
        String nextPageToken = "";
        do {
            RESPONSE responsePage = loadResponsePage(requestBuilder, requestExecutor, nextPageToken);
            responsePages.add(responsePage);

            nextPageToken = invokeMethod(responsePage, "getNextPageToken");
        } while (StringUtils.isNotBlank(nextPageToken));

        return responsePages;
    }

    private <REQUEST extends GeneratedMessage, RESPONSE> RESPONSE loadResponsePage(Builder requestBuilder,
                                                                                   RequestExecutor<REQUEST, RESPONSE> requestExecutor,
                                                                                   String nextPageToken) throws Ga4ghClientException {
        invokeMethod(requestBuilder, "setPageToken", nextPageToken);
        //noinspection unchecked
        REQUEST requestWithPageToken = (REQUEST) requestBuilder.build();
        return requestExecutor.execute(requestWithPageToken);
    }

    @SuppressWarnings("unchecked")
    private <T> T invokeMethod(Object response, String methodName, Object... args) throws Ga4ghClientException {
        try {
            return (T) MethodUtils.invokeMethod(response, methodName, args);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new Ga4ghClientException(String.format("Couldn't invoke method %s.", methodName), e);
        }
    }

    @FunctionalInterface
    private interface RequestExecutor<REQUEST, RESPONSE> {
        RESPONSE execute(REQUEST request) throws Ga4ghClientException;
    }
}
