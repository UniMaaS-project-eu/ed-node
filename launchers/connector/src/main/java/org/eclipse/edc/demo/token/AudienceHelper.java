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

/**
 * Utility class para manejar el audience dinámico desde tu código de aplicación.
 * 
 * EJEMPLO DE USO:
 * 
 * // Antes de hacer una petición que requiere un token:
 * AudienceHelper.setAudienceForRequest("did:web:target-participant");
 * try {
 *     // Realizar tu petición aquí (catalog, negotiation, transfer, etc.)
 *     var result = catalogService.requestCatalog(...);
 *     return result;
 * } finally {
 *     // IMPORTANTE: Siempre limpiar el audience
 *     AudienceHelper.clearAudience();
 * }
 */
public class AudienceHelper {
    
    /**
     * Establece el audience para la próxima generación de token en este thread.
     * 
     * @param counterPartyId El ID del participante destinatario (normalmente un DID)
     */
    public static void setAudienceForRequest(String counterPartyId) {
        MinimalAudienceTokenDecorator.setCounterPartyId(counterPartyId);
    }
    
    /**
     * Limpia el audience del thread actual.
     * IMPORTANTE: Debe llamarse siempre en un bloque finally para evitar memory leaks.
     */
    public static void clearAudience() {
        MinimalAudienceTokenDecorator.clearCounterPartyId();
    }
    
    /**
     * Obtiene el audience actualmente configurado para este thread.
     * 
     * @return El counterPartyId configurado o null si no hay ninguno
     */
    public static String getCurrentAudience() {
        return MinimalAudienceTokenDecorator.getCurrentCounterPartyId();
    }
    
    /**
     * Ejecuta una operación con un audience específico y limpia automáticamente.
     * Esta es la forma más segura de usar el sistema de audience dinámico.
     * 
     * @param counterPartyId El audience a establecer
     * @param operation La operación a ejecutar
     * @param <T> El tipo de retorno de la operación
     * @return El resultado de la operación
     */
    public static <T> T withAudience(String counterPartyId, AudienceOperation<T> operation) {
        setAudienceForRequest(counterPartyId);
        try {
            return operation.execute();
        } finally {
            clearAudience();
        }
    }
    
    /**
     * Functional interface para operaciones que necesitan un audience específico.
     */
    @FunctionalInterface
    public interface AudienceOperation<T> {
        T execute();
    }
}