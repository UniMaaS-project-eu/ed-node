/*
 *  Copyright (c) 2024 Eclipse Foundation
 *
 *  This program and the accompanying materials are made available under the
 *  terms of the Apache License, Version 2.0 which is available at
 *  https://www.apache.org/licenses/LICENSE-2.0
 *
 *  SPDX-License-Identifier: Apache-2.0
 *
 *  Contributors:
 *       Eclipse Foundation - initial API and implementation
 *
 */

package org.eclipse.edc.connector.vp.context.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa un contexto de Verifiable Presentation almacenado temporalmente
 * para ser utilizado cuando el provider solicite las credenciales.
 */
public class VPContext {
    
    /**
     * Tipos de operaciones que pueden desencadenar una solicitud de VP
     */
    public enum VPTriggerType {
        CATALOG_REQUEST,
        CONTRACT_NEGOTIATION,
        TRANSFER_PROCESS,
        DATASET_REQUEST,
        POLICY_EVALUATION
    }
    
    private final String vpString;
    private final String counterPartyId;
    private final VPTriggerType triggerType;
    private final long timestamp;
    private final String originalRequestId;
    
    /**
     * Constructor para crear un contexto VP
     * 
     * @param vpString String representación del Verifiable Presentation (JWT, JSON-LD, etc.)
     * @param counterPartyId Identificador del counterParty (provider)
     * @param triggerType Tipo de operación que desencadenó la necesidad de VP
     * @param originalRequestId ID de la petición original al Management API
     */
    public VPContext(String vpString, String counterPartyId, VPTriggerType triggerType, String originalRequestId) {
        this.vpString = Objects.requireNonNull(vpString, "vpString cannot be null");
        this.counterPartyId = Objects.requireNonNull(counterPartyId, "counterPartyId cannot be null");
        this.triggerType = Objects.requireNonNull(triggerType, "triggerType cannot be null");
        this.originalRequestId = originalRequestId;
        this.timestamp = Instant.now().toEpochMilli();
    }
    
    /**
     * @return El string VP original pasado al Management API
     */
    public String getVpString() { 
        return vpString; 
    }
    
    /**
     * @return ID del counterParty (provider) que solicitará el VP
     */
    public String getCounterPartyId() { 
        return counterPartyId; 
    }
    
    /**
     * @return Tipo de operación que desencadenó la creación de este contexto
     */
    public VPTriggerType getTriggerType() { 
        return triggerType; 
    }
    
    /**
     * @return Timestamp de creación del contexto (epoch milliseconds)
     */
    public long getTimestamp() { 
        return timestamp; 
    }
    
    /**
     * @return ID de la petición original al Management API (puede ser null)
     */
    public String getOriginalRequestId() { 
        return originalRequestId; 
    }
    
    /**
     * Verifica si el contexto ha expirado según el TTL proporcionado
     * 
     * @param ttlMillis Time-to-live en milliseconds
     * @return true si el contexto ha expirado
     */
    public boolean isExpired(long ttlMillis) {
        return (Instant.now().toEpochMilli() - timestamp) > ttlMillis;
    }
    
    /**
     * Calcula la edad del contexto en milliseconds
     * 
     * @return Edad del contexto en milliseconds
     */
    public long getAgeMillis() {
        return Instant.now().toEpochMilli() - timestamp;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VPContext vpContext = (VPContext) o;
        return timestamp == vpContext.timestamp &&
                Objects.equals(vpString, vpContext.vpString) &&
                Objects.equals(counterPartyId, vpContext.counterPartyId) &&
                triggerType == vpContext.triggerType &&
                Objects.equals(originalRequestId, vpContext.originalRequestId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(vpString, counterPartyId, triggerType, timestamp, originalRequestId);
    }
    
    @Override
    public String toString() {
        return "VPContext{" +
                "counterPartyId='" + counterPartyId + '\'' +
                ", triggerType=" + triggerType +
                ", originalRequestId='" + originalRequestId + '\'' +
                ", timestamp=" + timestamp +
                ", age=" + getAgeMillis() + "ms" +
                '}';
    }
}
