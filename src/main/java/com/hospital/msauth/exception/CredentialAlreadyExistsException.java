package com.hospital.msauth.exception;

public class CredentialAlreadyExistsException extends RuntimeException {

    public CredentialAlreadyExistsException(String message) {
        super(message);
    }
}
