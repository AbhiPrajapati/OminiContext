package com.omnicontext.exception;

/**
 * Thrown when the rawContent payload exceeds the configured maximum size.
 */
public class PayloadTooLargeException extends RuntimeException {

    private final long actualBytes;
    private final long maxBytes;

    public PayloadTooLargeException(long actualBytes, long maxBytes) {
        super(String.format("Payload size %d bytes exceeds maximum allowed %d bytes", actualBytes, maxBytes));
        this.actualBytes = actualBytes;
        this.maxBytes = maxBytes;
    }

    public long getActualBytes() {
        return actualBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }
}
