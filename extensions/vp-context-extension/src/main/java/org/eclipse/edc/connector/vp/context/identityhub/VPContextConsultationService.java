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

package org.eclipse.edc.connector.vp.context.identityhub;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Servicio para que el Identity Hub consulte contextos VP almacenados en el Conector Consumer.
 * 
 * Esta clase debe ser utilizada en el Identity Hub para obtener VPs pre-almacenados
 * cuando el Provider solicita credenciales via DSP.
 */
public class VPContextConsultationService {
    
    private static final String SETTING_CONNECTOR_INTERNAL_URL = "edc.connector.internal.url";
    private static final String DEFAULT_CONNECTOR_URL = "http://localhost:8181";
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    
    private final String connectorInternalUrl;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Monitor monitor;
    
    /**
     * Constructor para integración con Identity Hub EDC 0.14.0
     * 
     * @param context Contexto del ServiceExtension del Identity Hub
     * @param monitor Monitor para logging
     */
    public VPContextConsultationService(ServiceExtensionContext context, Monitor monitor) {
        this.connectorInternalUrl = context.getSetting(SETTING_CONNECTOR_INTERNAL_URL, DEFAULT_CONNECTOR_URL);
        this.monitor = monitor;
        this.objectMapper = new ObjectMapper();
        
        // Cliente HTTP configurado para llamadas internas
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(REQUEST_TIMEOUT)
            .build();
        
        monitor.info("VPContextConsultationService initialized - Connector URL: {}", connectorInternalUrl);
    }
    
    /**
     * Obtiene un VP del contexto almacenado en el Conector usando correlationId
     * 
     * @param correlationId ID de correlación del protocolo DSP
     * @return Optional con el VP string si se encuentra
     */
    public Optional<String> getVPFromConnectorContext(String correlationId) {
        monitor.debug("Requesting VP from connector for correlationId: {}", correlationId);
        
        try {
            String url = connectorInternalUrl + "/api/management/v3/internal/vp/context/" + correlationId;
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String vpString = json.get("vpString").asText();
                
                monitor.info("VP successfully retrieved from connector context - correlationId: {}", correlationId);
                return Optional.of(vpString);
                
            } else if (response.statusCode() == 404) {
                monitor.info("VP context not found in connector - correlationId: {}", correlationId);
                return Optional.empty();
                
            } else {
                monitor.warning("Unexpected response from connector: {} - {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            
        } catch (IOException e) {
            monitor.warning("IO error consulting connector VP context - correlationId: {}", correlationId, e);
            return Optional.empty();
        } catch (InterruptedException e) {
            monitor.warning("Request interrupted while consulting connector VP context - correlationId: {}", correlationId);
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            monitor.severe("Unexpected error consulting connector VP context - correlationId: {}", correlationId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Obtiene un VP del contexto almacenado usando contextId directo
     * 
     * @param contextId ID directo del contexto VP
     * @return Optional con el VP string si se encuentra
     */
    public Optional<String> getVPByContextId(String contextId) {
        monitor.debug("Requesting VP from connector for contextId: {}", contextId);
        
        try {
            String url = connectorInternalUrl + "/api/management/v3/internal/vp/context/by-context-id/" + contextId;
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                JsonNode json = objectMapper.readTree(response.body());
                String vpString = json.get("vpString").asText();
                
                monitor.info("VP successfully retrieved from connector context - contextId: {}", contextId);
                return Optional.of(vpString);
                
            } else if (response.statusCode() == 404) {
                monitor.info("VP context not found in connector - contextId: {}", contextId);
                return Optional.empty();
                
            } else {
                monitor.warning("Unexpected response from connector: {} - {}", response.statusCode(), response.body());
                return Optional.empty();
            }
            
        } catch (Exception e) {
            monitor.severe("Error consulting connector VP context by contextId: {}", contextId, e);
            return Optional.empty();
        }
    }
    
    /**
     * Intenta obtener VP usando múltiples identificadores de contexto
     * 
     * @param contextIdentifiers Map con diferentes tipos de identificadores
     * @return Optional con el VP string si se encuentra con cualquier identificador
     */
    public Optional<String> getVPByAnyIdentifier(Map<String, String> contextIdentifiers) {
        monitor.debug("Attempting VP retrieval with identifiers: {}", contextIdentifiers.keySet());
        
        // Intentar por correlationId primero (más común en DSP)
        if (contextIdentifiers.containsKey("correlationId")) {
            String correlationId = contextIdentifiers.get("correlationId");
            Optional<String> vp = getVPFromConnectorContext(correlationId);
            if (vp.isPresent()) {
                return vp;
            }
        }
        
        // Intentar por contextId directo
        if (contextIdentifiers.containsKey("contextId")) {
            String contextId = contextIdentifiers.get("contextId");
            Optional<String> vp = getVPByContextId(contextId);
            if (vp.isPresent()) {
                return vp;
            }
        }
        
        // Intentar por negotiationId, transferId, etc.
        for (Map.Entry<String, String> entry : contextIdentifiers.entrySet()) {
            if (!entry.getKey().equals("correlationId") && !entry.getKey().equals("contextId")) {
                Optional<String> vp = getVPFromConnectorContext(entry.getValue());
                if (vp.isPresent()) {
                    monitor.info("VP found using identifier type: {}", entry.getKey());
                    return vp;
                }
            }
        }
        
        monitor.info("VP not found with any provided identifier");
        return Optional.empty();
    }
    
    /**
     * Verifica si el servicio de consulta VP está disponible
     * 
     * @return true si el conector responde correctamente
     */
    public boolean isConnectorVPServiceAvailable() {
        try {
            String url = connectorInternalUrl + "/api/management/v3/vp/context/stats";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            boolean available = response.statusCode() == 200;
            monitor.debug("Connector VP service availability check: {}", available);
            return available;
            
        } catch (Exception e) {
            monitor.warning("Connector VP service availability check failed", e);
            return false;
        }
    }
    
    /**
     * Obtiene estadísticas del servicio para monitoreo
     * 
     * @return Map con estadísticas del servicio
     */
    public Map<String, Object> getServiceStats() {
        try {
            String url = connectorInternalUrl + "/api/management/v3/vp/context/stats";
            
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Accept", "application/json")
                .GET()
                .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() == 200) {
                @SuppressWarnings("unchecked")
                Map<String, Object> stats = objectMapper.readValue(response.body(), Map.class);
                return stats;
            }
            
        } catch (Exception e) {
            monitor.warning("Failed to get connector VP service stats", e);
        }
        
        return Map.of("error", "Failed to retrieve stats");
    }
    
    /**
     * Cierra el cliente HTTP de forma segura
     */
    public void shutdown() {
        // HttpClient se cierra automáticamente, pero podemos hacer cleanup adicional si es necesario
        monitor.info("VPContextConsultationService shutdown completed");
    }
}