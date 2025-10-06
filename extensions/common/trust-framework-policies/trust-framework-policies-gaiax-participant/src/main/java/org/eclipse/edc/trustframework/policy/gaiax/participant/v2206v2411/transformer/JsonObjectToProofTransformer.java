package org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411.transformer;

import jakarta.json.JsonObject;
import jakarta.json.JsonString;
import jakarta.json.JsonValue;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

// DEPRETACED: PROOF is not fully tested, JWT solution is implemented
// - The implementation is simplified and would likely require more work for real-world JSON-LD cases.
// - If JSON-LD is implemented in the future, proper implementation requires more than just a transformer (you'll need the entire Linked Data signature validation suite).
public class JsonObjectToProofTransformer extends AbstractJsonLdTransformer<JsonObject, Map> {


    private static final String PROOF_TYPE = "type";
    private static final String PROOF_VALUE = "proofValue";
    private static final String PROOF_PURPOSE = "proofPurpose";
    private static final String VERIFICATION_METHOD = "verificationMethod";
    private static final String CREATED = "created";

    public JsonObjectToProofTransformer() {
        super(JsonObject.class, Map.class);
    }

    @Override
    public @Nullable Map transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        Map<String, Object> proof = new HashMap<>();
        
        // Extraer campos del proof preservando el proofValue
        var type = object.get(PROOF_TYPE);
        if (type != null) {
            proof.put(PROOF_TYPE, transformString(type, context));
        }
        
        var proofValue = object.get(PROOF_VALUE);
        if (proofValue != null) {
            proof.put(PROOF_VALUE, transformString(proofValue, context));
        }
        
        var proofPurpose = object.get(PROOF_PURPOSE);
        if (proofPurpose != null) {
            proof.put(PROOF_PURPOSE, transformString(proofPurpose, context));
        }
        
        var verificationMethod = object.get(VERIFICATION_METHOD);
        if (verificationMethod != null) {
            proof.put(VERIFICATION_METHOD, transformString(verificationMethod, context));
        }
        
        var created = object.get(CREATED);
        if (created != null) {
            proof.put(CREATED, transformString(created, context));
        }
        
        return proof.isEmpty() ? null : proof;
    }
}