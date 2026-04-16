package com.artha.domain.account;

/** Business rule violations surfaced to callers as 4xx responses. */
public class DomainException extends RuntimeException {
    public DomainException(String message) {
        super(message);
    }
}
