package com.evplatform.libraries.securelogging;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * Field-name classification for the ENG-001 doc §9 never-log list.
 *
 * Technical primitive only: the denylist mirrors the approved policy —
 * access/refresh tokens, client secrets, passwords, authorization secrets,
 * raw QR secrets, private keys, complete email addresses, identity and
 * vehicle data, full message payloads with personal data.
 */
public final class SensitiveFields {

    /** Substrings that mark a field name as prohibited in logs. */
    private static final Collection<String> DENYLIST = List.of(
            "accesstoken", "refreshtoken", "idtoken",
            "token",              // catches start_authorization secrets etc.
            "secret", "clientsecret",
            "password", "passwd", "pwd",
            "authorization", "credential",
            "privatekey", "signingkey",
            "qrcode", "qrsecret", "qrvalue",
            "emailaddress", "email",
            "phonenumber",
            "nationalid", "ssn",
            "vehicleidentification", "vin",
            "sessionkey", "apikey");

    /** The mask value used for every redacted field. */
    public static final String MASK = "[REDACTED]";

    private SensitiveFields() {
    }

    /**
     * @return {@code true} when the field name contains any prohibited
     *         substring (case-insensitive, underscores/hyphens normalized).
     */
    public static boolean isSensitive(String fieldName) {
        if (fieldName == null) {
            return true; // fail closed for unnamed values
        }
        String normalized = fieldName.toLowerCase(Locale.ROOT)
                .replace("_", "").replace("-", "").replace(".", "").replace(" ", "");
        return DENYLIST.stream().anyMatch(normalized::contains);
    }
}
