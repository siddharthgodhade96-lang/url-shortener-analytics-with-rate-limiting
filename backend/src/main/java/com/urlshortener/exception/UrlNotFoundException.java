package com.urlshortener.exception;

public class UrlNotFoundException extends RuntimeException {

    public UrlNotFoundException(String code) {
        super("No active URL found for code: " + code);
    }
}
