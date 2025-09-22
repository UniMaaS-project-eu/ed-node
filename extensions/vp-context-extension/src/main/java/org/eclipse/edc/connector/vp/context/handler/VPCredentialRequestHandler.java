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

package org.eclipse.edc.connector.vp.context.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.connector.vp.context.model.VPContext;
import org.eclipse.edc.connector.vp.context.service.VPContextManager;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Handler que intercepta las solicitudes de Verifiable Presentations
 * desde providers y utiliza el contexto VP almacenado previamente.
 * 
 * Este handler se integra con el flujo de credenciales de EDC para
 * proporcionar VPs cuando el provider los requiere.
 */
public class VPCredentialRequestHandler {
    
    private final VPContextManager vpContextManager;
    private final Monitor monitor;
    private final ObjectMapper objectMapper;
    
    /**
     * Constructor con dependencias EDC
     * 
     * @param vpContextManager Manager para contextos VP
     * @param monitor Monitor EDC para logging
     */
    public VPCredentialRequestHandler(VPContextManager vpContextManager, Monitor monitor) {
        this.vpContextManager = vpContextManager;
        this.monitor = monitor;
        this.objectMapper = new ObjectMapper();
    }
    
    /**
     * Maneja una solicitud de VP utilizando el contexto almacenado.
     * 
     * Este método es llamado cuando el provider solicita VPs durante
     * el flujo DSP (catalog, negotiation, transfer).
     * 
     * @param presentationQuery Query de presentación del provider
     * @param context Contexto de la solicitud (contiene correlationIds)
     * @return CompletableFuture con el resultado del VP o error
     */
    public CompletableFuture<Result<Map<String, Object>>> handleVPRequest(
            String presentationQuery, 
            Map<String, Object> context) {
        
        monitor.debug("Handling VP request - context keys: {}", context.keySet());
        
        try {
            // Buscar identificador de contexto en diferentes propiedades
            String contextIdentifier = findContextIdentifier(context);
            
            if (contextIdentifier == null) {
                monitor.warning("No context identifier found in VP request context");
                return CompletableFuture.completedFuture(
                    Result.failure("No VP context identifier found")
                );
            }
            
            // Intentar recuperar contexto VP
            Optional<VPContext> vpContext = retrieveVPContext(contextIdentifier);
            
            if (vpContext.isEmpty()) {
                monitor.warning("VP context not found for identifier: {}", contextIdentifier);
                return CompletableFuture.completedFuture(
                    Result.failure("VP context not found or expired")
                );
            }
            
            VPContext context1 = vpContext.get();
            monitor.info("VP context retrieved - triggerType: {}, counterParty: {}, age: {}ms", 
                context1.getTriggerType(), context1.getCounterPartyId(), context1.getAgeMillis());
            
            // Parsear y estructurar el VP para la respuesta
            Map<String, Object> vpResponse = parseAndStructureVP(context1.getVpString(), presentationQuery);
            
            return CompletableFuture.completedFuture(Result.success(vpResponse));
            
        } catch (Exception e) {
            monitor.severe("Error handling VP request", e);
            return CompletableFuture.completedFuture(
                Result.failure("Failed to handle VP request: " + e.getMessage())
            );
        }
    }
    
    /**
     * Maneja solicitud de VP con parámetros específicos de EDC
     * 
     * @param correlationId ID de correlación del protocolo DSP
     * @param negotiationId ID de negociación (si aplica)
     * @param transferId ID de transferencia (si aplica)
     * @param presentationDefinition Definición de presentación requerida
     * @return CompletableFuture con el VP estructurado
     */
    public CompletableFuture<Result<Map<String, Object>>> handleVPRequestWithIds(
            String correlationId,
            String negotiationId,
            String transferId,
            String presentationDefinition) {
        
        monitor.debug("Handling VP request - correlationId: {}, negotiationId: {}, transferId: {}", 
            correlationId, negotiationId, transferId);
        
        // Crear contexto simulado para usar el método principal
        Map<String, Object> context = new HashMap<>();
        if (correlationId != null) context.put("correlationId", correlationId);
        if (negotiationId != null) context.put("negotiation.id", negotiationId);
        if (transferId != null) context.put("transfer.id", transferId);
        
        return handleVPRequest(presentationDefinition, context);
    }
    
    /**
     * Busca identificadores de contexto en las propiedades disponibles
     */
    private String findContextIdentifier(Map<String, Object> context) {
        // Orden de prioridad para buscar el identificador
        String[] possibleKeys = {
            "vp.context.id",        // ID directo del contexto VP
            "correlationId",        // ID de correlación DSP
            "negotiation.id",       // ID de negociación
            "transfer.id",          // ID de transferencia
            "catalog.correlation.id", // ID de correlación de catálogo
            "processId",            // ID de proceso genérico
            "requestId"             // ID de petición
        };
        
        for (String key : possibleKeys) {
            Object value = context.get(key);
            if (value != null) {
                String identifier = value.toString();
                monitor.debug("Found context identifier '{}' with value: {}", key, identifier);
                return identifier;
            }
        }
        
        return null;
    }
    
    /**
     * Recupera el contexto VP usando diferentes estrategias
     */
    private Optional<VPContext> retrieveVPContext(String identifier) {
        // Primero intentar recuperar directamente por contextId
        Optional<VPContext> context = vpContextManager.retrieveAndRemoveVPContext(identifier);
        
        if (context.isPresent()) {
            return context;
        }
        
        // Si no funciona, intentar por correlationId
        return vpContextManager.retrieveByCorrelation(identifier);
    }
    
    /**
     * Parsea el VP string y lo estructura según la presentationQuery del provider
     */
    private Map<String, Object> parseAndStructureVP(String vpString, String presentationQuery) {
        try {
            // Determinar el formato del VP
            if (vpString.trim().startsWith("ey") && vpString.contains(".")) {
                // Es un JWT VP
                return parseJWTVerifiablePresentation(vpString, presentationQuery);
            } else if (vpString.trim().startsWith("{")) {
                // Es un JSON-LD VP
                return parseJSONLDVerifiablePresentation(vpString, presentationQuery);
            } else {
                // Formato desconocido, tratarlo como string plano
                return createGenericVPResponse(vpString);
            }
        } catch (Exception e) {
            monitor.warning("Failed to parse VP string, returning generic response", e);
            return createGenericVPResponse(vpString);
        }
    }
    
    /**
     * Parsea un Verifiable Presentation en formato JWT
     */
    private Map<String, Object> parseJWTVerifiablePresentation(String jwtVP, String presentationQuery) {
        try {
            // Parsear el JWT (sin validación de firma para este ejemplo)
            String[] jwtParts = jwtVP.split("\\.");
            if (jwtParts.length != 3) {
                throw new IllegalArgumentException("Invalid JWT format");
            }
            
            // Decodificar payload
            String payload = new String(Base64.getUrlDecoder().decode(jwtParts[1]));
            JsonNode payloadNode = objectMapper.readTree(payload);
            
            // Estructurar respuesta
            Map<String, Object> vpResponse = new HashMap<>();
            vpResponse.put("@context", "https://www.w3.org/2018/credentials/v1");
            vpResponse.put("@type", "VerifiablePresentation");
            vpResponse.put("format", "jwt_vp");
            vpResponse.put("presentation", jwtVP);
            
            // Extraer información del payload si está disponible
            if (payloadNode.has("vp")) {
                JsonNode vpNode = payloadNode.get("vp");
                if (vpNode.has("@context")) {
                    vpResponse.put("@context", vpNode.get("@context"));
                }
                if (vpNode.has("type")) {
                    vpResponse.put("type", vpNode.get("type"));
                }
                if (vpNode.has("verifiableCredential")) {
                    vpResponse.put("verifiableCredential", vpNode.get("verifiableCredential"));
                }
            }
            
            monitor.debug("Successfully parsed JWT VP");
            return vpResponse;
            
        } catch (Exception e) {
            monitor.warning("Failed to parse JWT VP, falling back to generic response", e);
            return createGenericVPResponse(jwtVP);
        }
    }
    
    /**
     * Parsea un Verifiable Presentation en formato JSON-LD
     */
    private Map<String, Object> parseJSONLDVerifiablePresentation(String jsonldVP, String presentationQuery) {
        try {
            JsonNode vpNode = objectMapper.readTree(jsonldVP);
            
            Map<String, Object> vpResponse = new HashMap<>();
            vpResponse.put("format", "ldp_vp");
            
            // Copiar propiedades estándar
            if (vpNode.has("@context")) {
                vpResponse.put("@context", vpNode.get("@context"));
            }
            if (vpNode.has("@type") || vpNode.has("type")) {
                vpResponse.put("@type", vpNode.has("@type") ? vpNode.get("@type") : vpNode.get("type"));
            }
            if (vpNode.has("@id") || vpNode.has("id")) {
                vpResponse.put("@id", vpNode.has("@id") ? vpNode.get("@id") : vpNode.get("id"));
            }
            if (vpNode.has("verifiableCredential")) {
                vpResponse.put("verifiableCredential", vpNode.get("verifiableCredential"));
            }
            if (vpNode.has("proof")) {
                vpResponse.put("proof", vpNode.get("proof"));
            }
            
            // Incluir el VP completo
            vpResponse.put("presentation", objectMapper.convertValue(vpNode, Map.class));
            
            monitor.debug("Successfully parsed JSON-LD VP");
            return vpResponse;
            
        } catch (Exception e) {
            monitor.warning("Failed to parse JSON-LD VP, falling back to generic response", e);
            return createGenericVPResponse(jsonldVP);
        }
    }
    
    /**
     * Crea una respuesta VP genérica cuando el parsing falla
     */
    private Map<String, Object> createGenericVPResponse(String vpString) {
        Map<String, Object> genericResponse = new HashMap<>();
        genericResponse.put("@context", "https://www.w3.org/2018/credentials/v1");
        genericResponse.put("@type", "VerifiablePresentation");
        genericResponse.put("format", "raw");
        genericResponse.put("presentation", vpString);
        genericResponse.put("note", "VP provided in raw format");
        
        return genericResponse;
    }
    
    /**
     * Validación básica de la presentationQuery del provider
     */
    public boolean isValidPresentationQuery(String presentationQuery) {
        if (presentationQuery == null || presentationQuery.trim().isEmpty()) {
            return false;
        }
        
        try {
            // Intentar parsear como JSON
            JsonNode queryNode = objectMapper.readTree(presentationQuery);
            
            // Verificar que tiene estructura básica de presentation definition
            return queryNode.has("id") || 
                   queryNode.has("input_descriptors") || 
                   queryNode.has("presentationDefinition");
                   
        } catch (Exception e) {
            // Si no es JSON válido, asumir que es válido en otro formato
            return true;
        }
    }
    
    /**
     * Obtiene estadísticas de uso del handler
     */
    public Map<String, Object> getHandlerStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("handlerType", "VPCredentialRequestHandler");
        stats.put("vpContextManagerStats", vpContextManager.getStats());
        stats.put("timestamp", System.currentTimeMillis());
        
        return stats;
    }
}
