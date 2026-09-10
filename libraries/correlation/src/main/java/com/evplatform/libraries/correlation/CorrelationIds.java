package com.evplatform.libraries.correlation;

import java.util.UUID;

/**
 * Generation and validation of correlation/causation identifiers.
 *
 * Technical primitive only (ENG-001 doc §4.1): no business semantics.
 * Identifiers are UUIDv4 strings, matching the uuid-typed correlation fields
 * of ARC-022 §8 persistence conventions.
 */
public final class CorrelationIds {

    private CorrelationIds() {
    }

    /** @return a new random UUID string (lowercase, canonical form). */
    public static String newId() {
        return UUID.randomUUID().toString();
    }

    /** @return {@code true} if {@code value} parses as a UUID. */
    public static boolean isValid(String value) {
        if (value == null) {
            return false;
        }
        try {
            // stringent: UUID.fromString accepts variants but never garbage
            UUID.fromString(value.trim());
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
