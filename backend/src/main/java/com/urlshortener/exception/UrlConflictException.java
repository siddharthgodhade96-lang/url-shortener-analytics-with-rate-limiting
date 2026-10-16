package com.urlshortener.exception;

public class UrlConflictException extends RuntimeException {

    public UrlConflictException(String message) {
        super(message);
    }
}
