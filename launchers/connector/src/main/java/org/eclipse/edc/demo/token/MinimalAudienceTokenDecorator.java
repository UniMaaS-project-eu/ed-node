/*
 *  Copyright (c) 2023 Bayerische Motoren Werke Aktiengesellschaft (BMW AG)
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Bayerische Motoren Werke Aktiengesellschaft (BMW AG) - initial API and implementation
 */

package org.eclipse.edc.demo.token;

import org.eclipse.edc.spi.iam.TokenParameters;
import org.eclipse.edc.token.spi.TokenDecorator;

/**
 * TokenDecorator que maneja el audience dinámicamente usando ThreadLocal.
 * Compatible con EDC 0.14.0.
 */
public class MinimalAudienceTokenDecorator implements TokenDecorator {

    private static final ThreadLocal<String> CURRENT_COUNTER_PARTY_ID = new ThreadLocal<>();
    private final String defaultAudience;

    public MinimalAudienceTokenDecorator(String defaultAudience) {
        this.defaultAudience = defaultAudience != null ? defaultAudience : "edc:default";
    }

    /**
     * Establece el counterPartyId para el thread actual.
     */
    public static void setCounterPartyId(String counterPartyId) {
        if (counterPartyId != null && !counterPartyId.isBlank()) {
            CURRENT_COUNTER_PARTY_ID.set(counterPartyId);
        }
    }

    /**
     * Limpia el counterPartyId del thread actual.
     */
    public static void clearCounterPartyId() {
        CURRENT_COUNTER_PARTY_ID.remove();
    }

    /**
     * Obtiene el counterPartyId actual o null si no está establecido.
     */
    public static String getCurrentCounterPartyId() {
        return CURRENT_COUNTER_PARTY_ID.get();
    }

    @Override
    public TokenParameters.Builder decorate(TokenParameters.Builder tokenParameters) {
        String counterPartyId = CURRENT_COUNTER_PARTY_ID.get();
        String audience = (counterPartyId != null && !counterPartyId.isBlank()) ? counterPartyId : defaultAudience;
        
        // Establecer el claim "aud" con el audience determinado
        return tokenParameters.claims("aud", audience);
    }
}