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

package org.eclipse.edc.trustframework.policy.gaiax.participant.v2206v2411.claims;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.trustframework.policy.core.FieldEvaluationProperty;

import java.util.List;

@JsonDeserialize(builder = GaiaxParticipantClaims.Builder.class)
public class GaiaxParticipantClaims {

    private static final String NAME_FIELD = "gx-participant:name";
    private static final String LEGAL_NAME_FIELD = "gx-participant:legalName";
    private static final String REGISTRATION_NUMBER_FIELD = "gx-participant:registrationNumber";
    private static final String HEADQUARTER_ADDRESS_FIELD = "gx-participant:headquarterAddress";
    private static final String LEGAL_ADDRESS_FIELD = "gx-participant:legalAddress";
    private static final String TERMS_AND_CONDITIONS_FIELD = "gx-participant:termsAndConditions";
    private static final String PARTICIPANT_ROLE_FIELD = "gx:participantRole";

    @JsonProperty(NAME_FIELD)
    @FieldEvaluationProperty(displayName = NAME_FIELD)
    private String name;

    @JsonProperty(LEGAL_NAME_FIELD)
    @FieldEvaluationProperty(displayName = LEGAL_NAME_FIELD)
    private String legalName;

    @JsonProperty(REGISTRATION_NUMBER_FIELD)
    @FieldEvaluationProperty(displayName = REGISTRATION_NUMBER_FIELD, unwrap = true)
    private ParticipantRegistrationNumber registrationNumber;

    @JsonProperty(HEADQUARTER_ADDRESS_FIELD)
    @FieldEvaluationProperty(displayName = HEADQUARTER_ADDRESS_FIELD, unwrap = true)
    private ParticipantAddress headquarterAddress;

    @JsonProperty(LEGAL_ADDRESS_FIELD)
    @FieldEvaluationProperty(displayName = LEGAL_ADDRESS_FIELD, unwrap = true)
    private ParticipantAddress legalAddress;

    @JsonProperty(TERMS_AND_CONDITIONS_FIELD)
    @FieldEvaluationProperty(displayName = TERMS_AND_CONDITIONS_FIELD)
    private String termsAndConditions;

    @JsonProperty(PARTICIPANT_ROLE_FIELD)
    @FieldEvaluationProperty(displayName = PARTICIPANT_ROLE_FIELD)
    private List<String> participantRole;

    private GaiaxParticipantClaims() {
    }

    public String getName() {
        return name;
    }

    public String getLegalName() {
        return legalName;
    }

    public ParticipantRegistrationNumber getRegistrationNumber() {
        return registrationNumber;
    }

    public ParticipantAddress getHeadquarterAddress() {
        return headquarterAddress;
    }

    public ParticipantAddress getLegalAddress() {
        return legalAddress;
    }

    public String getTermsAndConditions() {
        return termsAndConditions;
    }

    // NUEVO: Getter para roles
    public List<String> getParticipantRole() {
        return participantRole;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder {
        private final GaiaxParticipantClaims claims;

        private Builder(GaiaxParticipantClaims claims) {
            this.claims = claims;
        }

        @JsonCreator
        public static GaiaxParticipantClaims.Builder newInstance() {
            return new GaiaxParticipantClaims.Builder(new GaiaxParticipantClaims());
        }

        @JsonProperty(NAME_FIELD)
        public Builder name(String name) {
            claims.name = name;
            return this;
        }

        @JsonProperty(LEGAL_NAME_FIELD)
        public Builder legalName(String legalName) {
            claims.legalName = legalName;
            return this;
        }

        @JsonProperty(REGISTRATION_NUMBER_FIELD)
        public Builder registrationNumber(ParticipantRegistrationNumber registrationNumber) {
            claims.registrationNumber = registrationNumber;
            return this;
        }

        @JsonProperty(HEADQUARTER_ADDRESS_FIELD)
        public Builder headquarterAddress(ParticipantAddress headquarterAddress) {
            claims.headquarterAddress = headquarterAddress;
            return this;
        }

        @JsonProperty(LEGAL_ADDRESS_FIELD)
        public Builder legalAddress(ParticipantAddress legalAddress) {
            claims.legalAddress = legalAddress;
            return this;
        }

        @JsonProperty(TERMS_AND_CONDITIONS_FIELD)
        public Builder termsAndConditions(String termsAndConditions) {
            claims.termsAndConditions = termsAndConditions;
            return this;
        }

        @JsonProperty(PARTICIPANT_ROLE_FIELD)
        public Builder participantRole(List<String> participantRole) {
            claims.participantRole = participantRole;
            return this;
        }

        public GaiaxParticipantClaims build() {
            return claims;
        }
    }
}
