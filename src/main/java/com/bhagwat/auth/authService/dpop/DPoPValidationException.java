package com.bhagwat.auth.authService.dpop;

/**
 * Thrown when DPoP proof validation fails.
 */
public class DPoPValidationException extends RuntimeException {
    public DPoPValidationException(String message) {
        super(message);
    }
}
