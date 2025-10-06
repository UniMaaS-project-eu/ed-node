package org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411.transformer;

import jakarta.json.JsonObject;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.CredentialSchema;
import org.eclipse.edc.jsonld.spi.transformer.AbstractJsonLdTransformer;
import org.eclipse.edc.transform.spi.TransformerContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class JsonObjectToCredentialSchemaTransformer extends AbstractJsonLdTransformer<JsonObject, CredentialSchema> {

    public JsonObjectToCredentialSchemaTransformer() {
        super(JsonObject.class, CredentialSchema.class);
    }

    @Override
    public @Nullable CredentialSchema transform(@NotNull JsonObject object, @NotNull TransformerContext context) {
        var id = transformString(object.get(CredentialSchema.CREDENTIAL_SCHEMA_ID_PROPERTY), context);
        var type = transformString(object.get(CredentialSchema.CREDENTIAL_SCHEMA_TYPE_PROPERTY), context);

        if (id == null || type == null) {
            context.reportProblem("CredentialSchema must have 'id' and 'type' fields");
            return null;
        }

        // Record constructor simple
        return new CredentialSchema(id, type);
    }
}