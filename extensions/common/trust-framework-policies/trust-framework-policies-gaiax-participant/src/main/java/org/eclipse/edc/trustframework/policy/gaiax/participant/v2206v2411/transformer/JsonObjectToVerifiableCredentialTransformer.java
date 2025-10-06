package org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411.transformer;

import jakarta.json.*;
import jakarta.json.stream.JsonParsingException;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSchema;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSubject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.StringReader;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;

// DEPRECATED: JsonObjectToVerifiableCredentialTransformer not working propertly
/**
 * Transformer fill issuanceDate/expirationDate for JWT VCs : (top-level, vc/credential, rawVc JWT).
 */
public class JsonObjectToVerifiableCredentialTransformer extends AbstractJsonLdTransformer<JsonObject, VerifiableCredential> {

    public JsonObjectToVerifiableCredentialTransformer() {
        super(JsonObject.class, VerifiableCredential.class);
    }

    @Override
    public @Nullable VerifiableCredential transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        try {
            VerifiableCredential vc = context.transform(object, VerifiableCredential.class);
            if (vc == null) {
                context.reportProblem("Base transform returned null for VerifiableCredential");
                return null;
            }

            Instant issuance = vc.getIssuanceDate();
            Instant expiration = vc.getExpirationDate();

            if (issuance != null && expiration != null) {
                return vc;
            }

            Instant foundIssuance = (issuance != null) ? issuance : findInstant(object, "iat", "nbf", "issuanceDate");
            Instant foundExpiration = (expiration != null) ? expiration : findInstant(object, "exp", "expirationDate", "expDate");

            if ((foundIssuance == null || foundExpiration == null) && containsRawJwt(object)) {
                JsonObject payload = decodeJwtPayload(object.getString("rawVc"));
                if (payload != null) {
                    if (foundIssuance == null) {
                        foundIssuance = findInstant(payload, "iat", "nbf", "issuanceDate");
                    }
                    if (foundExpiration == null) {
                        foundExpiration = findInstant(payload, "exp", "expirationDate");
                    }
                }
            }

            boolean sameIssuance = (issuance == null && foundIssuance == null) || (issuance != null && issuance.equals(foundIssuance));
            boolean sameExpiration = (expiration == null && foundExpiration == null) || (expiration != null && expiration.equals(foundExpiration));
            if (sameIssuance && sameExpiration) {
                context.reportProblem("No issuance/expiration could be inferred for VC id=" + vc.getId());
                return vc;
            }

            VerifiableCredential.Builder builder = VerifiableCredential.Builder.newInstance()
                    .id(vc.getId())
                    .issuer(vc.getIssuer())
                    .issuanceDate(foundIssuance)
                    .expirationDate(foundExpiration);

            if (vc.getType() != null) {
                vc.getType().forEach(builder::type);
            }

            if (vc.getCredentialSubject() != null) {
                for (CredentialSubject cs : vc.getCredentialSubject()) {
                    builder.credentialSubject(cs);
                }
            }

            if (vc.getCredentialSchema() != null) {
                for (CredentialSchema cs : vc.getCredentialSchema()) {
                    builder.credentialSchema(cs);
                }
            }

            context.reportProblem("Patched VC id=" + vc.getId() + " issuance=" + foundIssuance + " expiration=" + foundExpiration);
            return builder.build();

        } catch (Exception e) {
            context.reportProblem("Error in JsonObjectToVerifiableCredentialTransformer: " + e.getMessage());
            return null;
        }
    }

    private boolean containsRawJwt(JsonObject object) {
        return object.containsKey("rawVc") && object.get("rawVc").getValueType() == JsonValue.ValueType.STRING;
    }

    private JsonObject decodeJwtPayload(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if (parts.length < 2) return null;
            String payloadB64 = parts[1];
            // base64url decode
            byte[] decoded = Base64.getUrlDecoder().decode(padBase64(payloadB64));
            String payload = new String(decoded);
            try (JsonReader jr = Json.createReader(new StringReader(payload))) {
                return jr.readObject();
            }
        } catch (IllegalArgumentException | JsonParsingException e) {
            return null;
        }
    }

    private String padBase64(String s) {
        int rem = s.length() % 4;
        if (rem == 2) return s + "==";
        if (rem == 3) return s + "=";
        if (rem == 1) return s + "===";
        return s;
    }

    private Instant findInstant(JsonObject obj, String... keys) {
        for (String key : keys) {
            if (obj.containsKey(key)) {
                JsonValue v = obj.get(key);
                Instant parsed = parseJsonValueToInstant(v);
                if (parsed != null) return parsed;
            }
        }

        String[] nested = {"vc", "credential", "verifiableCredential"};
        for (String n : nested) {
            if (obj.containsKey(n) && obj.get(n).getValueType() == JsonValue.ValueType.OBJECT) {
                JsonObject nestedObj = obj.getJsonObject(n);
                for (String key : keys) {
                    if (nestedObj.containsKey(key)) {
                        Instant parsed = parseJsonValueToInstant(nestedObj.get(key));
                        if (parsed != null) return parsed;
                    }
                }
                for (String numKey : new String[]{"iat", "exp", "nbf"}) {
                    if (nestedObj.containsKey(numKey)) {
                        Instant parsed = parseJsonValueToInstant(nestedObj.get(numKey));
                        if (parsed != null) return parsed;
                    }
                }
            }
        }

        return null;
    }

    private Instant parseJsonValueToInstant(JsonValue v) {
        if (v == null) return null;
        try {
            if (v.getValueType() == JsonValue.ValueType.NUMBER) {
                JsonNumber num = (JsonNumber) v;
                long epoch = num.longValue();
                return Instant.ofEpochSecond(epoch);
            }
            if (v.getValueType() == JsonValue.ValueType.STRING) {
                String s = ((JsonString) v).getString();
                try {
                    long epoch = Long.parseLong(s);
                    return Instant.ofEpochSecond(epoch);
                } catch (NumberFormatException ignored) {}
                try {
                    return Instant.parse(s);
                } catch (DateTimeParseException ignored) {}
            }
        } catch (Exception e) {
        }
        return null;
    }
}
