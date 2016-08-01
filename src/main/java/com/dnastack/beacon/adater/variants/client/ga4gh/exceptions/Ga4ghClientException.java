package com.dnastack.beacon.adater.variants.client.ga4gh.exceptions;

/**
 * @author Artem (tema.voskoboynick@gmail.com)
 * @version 1.0
 */
public class Ga4ghClientException extends Exception {

    public Ga4ghClientException(String message) {
        super(message);
    }

    public Ga4ghClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
