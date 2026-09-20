package com.ApnaAspatal.portal.common.exception;

import java.time.Instant;

/**
 * Error body returned to clients when a request cannot be fulfilled.
 *
 * <p>A single, predictable shape for every handled failure, so clients can
 * parse errors the same way they parse successes.
 */
public record ApiError(int status, String message, Instant timestamp) {
}
