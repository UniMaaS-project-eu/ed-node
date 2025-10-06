/*
 *  Copyright (c) 2023 Amadeus
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Amadeus - initial API and implementation
 *
 */

package org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411.function;

import com.apicatalog.vc.Credential;
import com.fasterxml.jackson.databind.ObjectMapper;
//import org.eclipse.edc.identityhub.spi.credentials.model.Credential;
import org.eclipse.edc.iam.verifiablecredentials.spi.model.VerifiableCredential;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.trustframework.policy.core.CredentialClaimsEvaluationFunction;
import org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411.claims.GaiaxParticipantClaims;

import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class GaiaxParticipantClaimsEvaluationFunction extends CredentialClaimsEvaluationFunction<GaiaxParticipantClaims> {

    //private static final String GAIAX_2206_CREDENTIAL_CLAIMS_SHAPE_CONTEXT = "https://registry.gaia-x.eu/v2206/api/shape";
    //private static final String GAIAX_2411_CREDENTIAL_CLAIMS_SHAPE_CONTEXT = "https://registry.lab.gaia-x.eu/main/context/2411";

    private static final Set<String> SUPPORTED_CONTEXTS = Set.of(
        "https://registry.gaia-x.eu/v2206/api/shape",
        "https://registry.lab.gaia-x.eu/main/context/2411"
    );

    private static final String GAIAX_LEGAL_PERSON_TYPE = "LegalPerson";

    public GaiaxParticipantClaimsEvaluationFunction(Monitor monitor, ObjectMapper mapper, Function<GaiaxParticipantClaims, Object> navigation) {
        super(monitor, mapper, navigation);
    }

    @Override
    protected Predicate<VerifiableCredential> credentialFilter() {
        return credential ->
            credential.getCredentialSchema().stream()
                    .anyMatch(schema -> SUPPORTED_CONTEXTS.contains(schema.id()))
            && credential.getType().contains(GAIAX_LEGAL_PERSON_TYPE);
    }

    @Override
    protected Class<GaiaxParticipantClaims> getClaimsShapeType() {
        return GaiaxParticipantClaims.class;
    }
}
