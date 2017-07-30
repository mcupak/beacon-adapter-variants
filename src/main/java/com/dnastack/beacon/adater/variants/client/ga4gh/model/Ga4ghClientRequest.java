package com.dnastack.beacon.adater.variants.client.ga4gh.model;

import com.sun.istack.internal.NotNull;
import com.sun.istack.internal.Nullable;

import java.util.Objects;
import java.util.Optional;

/**
 * Request to create a client
 *
 * Created by aomochalov on 17.06.2017.
 */
public class Ga4ghClientRequest {

    @NotNull
    private String baseUrl;

    @Nullable
    private String apiKey;

    private Ga4ghClientRequest(@NotNull String baseUrl, @Nullable String apiKey) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.apiKey = apiKey;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Optional<String> getApiKey() {
        return Optional.ofNullable(apiKey);
    }

    public static Ga4ghClientRequestBuilder builder() {
        return new Ga4ghClientRequestBuilder();
    }

    public static class Ga4ghClientRequestBuilder {

        private String baseUrl;
        private String apiKey;

        public Ga4ghClientRequestBuilder withBaseUrl(@NotNull String baseUrl) {
            this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
            return this;
        }

        public Ga4ghClientRequestBuilder withApiKey(@Nullable String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Ga4ghClientRequest build() {
            return new Ga4ghClientRequest(baseUrl, apiKey);
        }

    }

}
