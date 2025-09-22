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

package org.eclipse.edc.connector.vp.context.controller;

import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.vp.context.model.VPContext;
import org.eclipse.edc.connector.vp.context.service.VPContextManager;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.web.spi.ApiErrorDetail;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * REST Controller que extiende el Management API de EDC para soportar
 * contextos de Verifiable Presentations.
 * 
 * Intercepta las llamadas a los endpoints estándar y almacena los VP strings
 * cuando se proporcionan via header X-VP-Context.
 */
@Path("/api/management/v3")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class VPManagementController {
    
    private static final String VP_CONTEXT_HEADER = "X-VP-Context";
    private static final String VP_CONTEXT_PROPERTY = "vp.context.id";
    
    private final VPContextManager vpContextManager;
    private final Monitor monitor;
    
    /**
     * Constructor que recibe las dependencias EDC
     * 
     * @param vpContextManager Manager para contextos VP
     * @param monitor Monitor EDC para logging
     */
    public VPManagementController(VPContextManager vpContextManager, Monitor monitor) {
        this.vpContextManager = vpContextManager;
        this.monitor = monitor;
    }
    
    /**
     * POST /api/management/v3/catalog/request
     * Solicitar catálogo con contexto VP opcional
     * 
     * @param requestBody Cuerpo de la petición de catálogo en formato JSON
     * @param vpString Header X-VP-Context con el VP string (opcional)
     * @return Respuesta con el ID de la petición y contextId del VP si aplicable
     */
    @POST
    @Path("/vp/catalog/request")
    public Response requestCatalogWithVP(String requestBody,
                                        @HeaderParam(VP_CONTEXT_HEADER) String vpString) {
        
        monitor.debug("Catalog request received with VP context: {}", vpString != null);
        
        try {
            // Parse básico para extraer counterPartyAddress
            String counterPartyId = extractCounterPartyFromJson(requestBody);
            String requestId = UUID.randomUUID().toString();
            String contextId = null;
            
            // Si hay VP string, almacenarlo
            if (vpString != null && !vpString.trim().isEmpty()) {
                contextId = vpContextManager.storeVPContext(
                    vpString,
                    counterPartyId,
                    VPContext.VPTriggerType.CATALOG_REQUEST,
                    requestId
                );
                
                monitor.info("VP context stored for catalog request - contextId: {}, counterParty: {}", 
                    contextId, counterPartyId);
            }
            
            // En una implementación real, aquí delegarías al CatalogService real
            // CatalogRequest request = parseRequestBody(requestBody);
            // if (contextId != null) {
            //     request.getProperties().put(VP_CONTEXT_PROPERTY, contextId);
            // }
            // var result = catalogService.requestCatalog(request);
            
            Map<String, Object> response = new HashMap<>();
            response.put("@type", "IdResponse");
            response.put("@id", requestId);
            response.put("createdAt", System.currentTimeMillis());
            if (contextId != null) {
                response.put("vpContextId", contextId);
            }
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            monitor.severe("Error processing catalog request with VP context", e);
            return createErrorResponse("Failed to process catalog request: " + e.getMessage(), 
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * POST /api/management/v3/contractnegotiations
     * Iniciar negociación de contrato con contexto VP opcional
     * 
     * @param requestBody Cuerpo de la petición de negociación en formato JSON
     * @param vpString Header X-VP-Context con el VP string (opcional)
     * @return Respuesta con el ID de la negociación y contextId del VP si aplicable
     */
    @POST
    @Path("/vp/contractnegotiations")
    public Response initiateNegotiationWithVP(String requestBody,
                                              @HeaderParam(VP_CONTEXT_HEADER) String vpString) {
        
        monitor.debug("Contract negotiation request received with VP context: {}", vpString != null);
        
        try {
            String counterPartyId = extractCounterPartyFromJson(requestBody);
            String negotiationId = UUID.randomUUID().toString();
            String contextId = null;
            
            // Si hay VP string, almacenarlo
            if (vpString != null && !vpString.trim().isEmpty()) {
                contextId = vpContextManager.storeVPContext(
                    vpString,
                    counterPartyId,
                    VPContext.VPTriggerType.CONTRACT_NEGOTIATION,
                    negotiationId
                );
                
                // Registrar correlación para tracking en DSP
                vpContextManager.registerCorrelation(contextId, negotiationId);
                
                monitor.info("VP context stored for contract negotiation - contextId: {}, negotiationId: {}", 
                    contextId, negotiationId);
            }
            
            // En una implementación real, aquí delegarías al ContractNegotiationService
            // ContractRequest request = parseRequestBody(requestBody);
            // if (contextId != null) {
            //     request.getProperties().put(VP_CONTEXT_PROPERTY, contextId);
            // }
            // var result = contractNegotiationService.initiateNegotiation(request);
            
            Map<String, Object> response = new HashMap<>();
            response.put("@type", "IdResponse");
            response.put("@id", negotiationId);
            response.put("createdAt", System.currentTimeMillis());
            if (contextId != null) {
                response.put("vpContextId", contextId);
            }
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            monitor.severe("Error processing contract negotiation with VP context", e);
            return createErrorResponse("Failed to initiate negotiation: " + e.getMessage(), 
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * POST /api/management/v3/transferprocesses
     * Iniciar proceso de transferencia con contexto VP opcional
     * 
     * @param requestBody Cuerpo de la petición de transferencia en formato JSON
     * @param vpString Header X-VP-Context con el VP string (opcional)
     * @return Respuesta con el ID de la transferencia y contextId del VP si aplicable
     */
    @POST
    @Path("/vp/transferprocesses")
    public Response initiateTransferWithVP(String requestBody,
                                          @HeaderParam(VP_CONTEXT_HEADER) String vpString) {
        
        monitor.debug("Transfer process request received with VP context: {}", vpString != null);
        
        try {
            String contractId = extractContractIdFromJson(requestBody);
            String counterPartyId = extractCounterPartyFromContract(contractId);
            String transferId = UUID.randomUUID().toString();
            String contextId = null;
            
            // Si hay VP string, almacenarlo
            if (vpString != null && !vpString.trim().isEmpty()) {
                contextId = vpContextManager.storeVPContext(
                    vpString,
                    counterPartyId,
                    VPContext.VPTriggerType.TRANSFER_PROCESS,
                    transferId
                );
                
                // Registrar correlación para tracking en DSP
                vpContextManager.registerCorrelation(contextId, transferId);
                
                monitor.info("VP context stored for transfer process - contextId: {}, transferId: {}", 
                    contextId, transferId);
            }
            
            // En una implementación real, aquí delegarías al TransferProcessService
            // TransferRequest request = parseRequestBody(requestBody);
            // if (contextId != null) {
            //     request.getProperties().put(VP_CONTEXT_PROPERTY, contextId);
            // }
            // var result = transferProcessService.initiateTransfer(request);
            
            Map<String, Object> response = new HashMap<>();
            response.put("@type", "IdResponse");
            response.put("@id", transferId);
            response.put("createdAt", System.currentTimeMillis());
            if (contextId != null) {
                response.put("vpContextId", contextId);
            }
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            monitor.severe("Error processing transfer request with VP context", e);
            return createErrorResponse("Failed to initiate transfer: " + e.getMessage(), 
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * POST /api/management/v3/catalog/dataset/request
     * Solicitar dataset específico con contexto VP opcional
     */
    @POST
    @Path("/vp/catalog/dataset/request")
    public Response requestDatasetWithVP(String requestBody,
                                        @HeaderParam(VP_CONTEXT_HEADER) String vpString) {
        
        monitor.debug("Dataset request received with VP context: {}", vpString != null);
        
        try {
            String counterPartyId = extractCounterPartyFromJson(requestBody);
            String requestId = UUID.randomUUID().toString();
            String contextId = null;
            
            if (vpString != null && !vpString.trim().isEmpty()) {
                contextId = vpContextManager.storeVPContext(
                    vpString,
                    counterPartyId,
                    VPContext.VPTriggerType.DATASET_REQUEST,
                    requestId
                );
                
                monitor.info("VP context stored for dataset request - contextId: {}", contextId);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("@type", "IdResponse");
            response.put("@id", requestId);
            response.put("createdAt", System.currentTimeMillis());
            if (contextId != null) {
                response.put("vpContextId", contextId);
            }
            
            return Response.ok(response).build();
            
        } catch (Exception e) {
            monitor.severe("Error processing dataset request with VP context", e);
            return createErrorResponse("Failed to process dataset request: " + e.getMessage(), 
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * GET /api/management/v3/vp/context/{contextId}
     * Consultar estado de un contexto VP (para debugging/monitoreo)
     */
    @GET
    @Path("/vp/context/{contextId}")
    public Response getVPContext(@PathParam("contextId") String contextId) {
        
        var context = vpContextManager.getVPContext(contextId);
        
        if (context.isPresent()) {
            VPContext vpContext = context.get();
            Map<String, Object> response = new HashMap<>();
            response.put("@type", "VPContext");
            response.put("contextId", contextId);
            response.put("counterPartyId", vpContext.getCounterPartyId());
            response.put("triggerType", vpContext.getTriggerType().toString());
            response.put("timestamp", vpContext.getTimestamp());
            response.put("ageMs", vpContext.getAgeMillis());
            response.put("originalRequestId", vpContext.getOriginalRequestId());
            response.put("status", "active");
            
            return Response.ok(response).build();
        } else {
            return createErrorResponse("VP context not found or expired", Response.Status.NOT_FOUND);
        }
    }
    
    /**
     * GET /api/management/v3/vp/context/stats
     * Obtener estadísticas del VPContextManager
     */
    @GET
    @Path("/vp/context/stats")
    public Response getVPContextStats() {
        var stats = vpContextManager.getStats();
        
        Map<String, Object> response = new HashMap<>();
        response.put("@type", "VPContextStats");
        response.put("activeContexts", stats.getActiveContexts());
        response.put("activeCorrelations", stats.getActiveCorrelations());
        response.put("totalStored", stats.getTotalStored());
        response.put("totalRetrieved", stats.getTotalRetrieved());
        response.put("totalExpired", stats.getTotalExpired());
        response.put("timestamp", System.currentTimeMillis());
        
        return Response.ok(response).build();
    }
    
    /**
     * POST /api/management/v3/vp/context/cleanup
     * Forzar limpieza manual de contextos expirados
     */
    @POST
    @Path("/vp/context/cleanup")
    public Response forceCleanup() {
        try {
            int cleaned = vpContextManager.forceCleanup();
            
            Map<String, Object> response = new HashMap<>();
            response.put("@type", "CleanupResult");
            response.put("cleanedContexts", cleaned);
            response.put("timestamp", System.currentTimeMillis());
            
            return Response.ok(response).build();
        } catch (Exception e) {
            return createErrorResponse("Failed to perform cleanup: " + e.getMessage(), 
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * GET /api/management/v3/internal/vp/context/{correlationId}
     * Endpoint interno para que el Identity Hub obtenga el VP por correlationId
     * Este endpoint es diferente al de consulta, elimina el contexto cuando lo recupera
     */
    @GET
    @Path("/internal/vp/context/{correlationId}")
    public Response getVPForIdentityHub(@PathParam("correlationId") String correlationId) {
        
        monitor.info("Identity Hub requesting VP for correlation: {}", correlationId);
        
        try {
            // Recuperar contexto VP por correlationId y eliminarlo (consume-once)
            Optional<VPContext> context = vpContextManager.retrieveByCorrelation(correlationId);
            
            if (context.isPresent()) {
                VPContext vpContext = context.get();
                
                Map<String, Object> response = new HashMap<>();
                response.put("@type", "VPContextResponse");
                response.put("vpString", vpContext.getVpString());
                response.put("counterPartyId", vpContext.getCounterPartyId());
                response.put("triggerType", vpContext.getTriggerType().toString());
                response.put("timestamp", vpContext.getTimestamp());
                response.put("originalRequestId", vpContext.getOriginalRequestId());
                response.put("status", "consumed");
                
                monitor.info("VP context provided to Identity Hub - triggerType: {}, age: {}ms", 
                    vpContext.getTriggerType(), vpContext.getAgeMillis());
                
                return Response.ok(response).build();
                
            } else {
                monitor.warning("VP context not found for correlationId: {}", correlationId);
                return createErrorResponse("VP context not found or expired for correlation: " + correlationId, 
                    Response.Status.NOT_FOUND);
            }
            
        } catch (Exception e) {
            monitor.severe("Error retrieving VP context for Identity Hub", e);
            return createErrorResponse("Failed to retrieve VP context: " + e.getMessage(), 
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    /**
     * GET /api/management/v3/internal/vp/context/by-context-id/{contextId}
     * Endpoint alternativo para búsqueda directa por contextId (para Identity Hub)
     */
    @GET
    @Path("/internal/vp/context/by-context-id/{contextId}")
    public Response getVPByContextIdForIdentityHub(@PathParam("contextId") String contextId) {
        
        monitor.info("Identity Hub requesting VP for contextId: {}", contextId);
        
        try {
            // Recuperar contexto VP por contextId y eliminarlo
            Optional<VPContext> context = vpContextManager.retrieveAndRemoveVPContext(contextId);
            
            if (context.isPresent()) {
                VPContext vpContext = context.get();
                
                Map<String, Object> response = new HashMap<>();
                response.put("@type", "VPContextResponse");
                response.put("vpString", vpContext.getVpString());
                response.put("counterPartyId", vpContext.getCounterPartyId());
                response.put("triggerType", vpContext.getTriggerType().toString());
                response.put("timestamp", vpContext.getTimestamp());
                response.put("originalRequestId", vpContext.getOriginalRequestId());
                response.put("status", "consumed");
                
                monitor.info("VP context provided to Identity Hub by contextId - triggerType: {}", 
                    vpContext.getTriggerType());
                
                return Response.ok(response).build();
                
            } else {
                monitor.warning("VP context not found for contextId: {}", contextId);
                return createErrorResponse("VP context not found or expired for contextId: " + contextId, 
                    Response.Status.NOT_FOUND);
            }
            
        } catch (Exception e) {
            monitor.severe("Error retrieving VP context by contextId for Identity Hub", e);
            return createErrorResponse("Failed to retrieve VP context: " + e.getMessage(), 
                Response.Status.INTERNAL_SERVER_ERROR);
        }
    }
    
    // ============================================================================
    // Métodos auxiliares para parsing y utilidades
    // ============================================================================
    
    /**
     * Extrae counterPartyAddress del JSON de petición
     */
    private String extractCounterPartyFromJson(String jsonBody) {
        // Implementación básica - en producción usar un parser JSON real
        // Este es un ejemplo simplificado
        try {
            if (jsonBody.contains("counterPartyAddress")) {
                String[] parts = jsonBody.split("counterPartyAddress")[1].split("\"");
                if (parts.length > 2) {
                    String address = parts[2];
                    // Extraer ID del final de la URL
                    return address.contains("/") ? 
                        address.substring(address.lastIndexOf("/") + 1) : address;
                }
            }
            if (jsonBody.contains("counterPartyId")) {
                String[] parts = jsonBody.split("counterPartyId")[1].split("\"");
                if (parts.length > 2) {
                    return parts[2];
                }
            }
            // Fallback
            return "unknown-counterparty-" + System.currentTimeMillis();
        } catch (Exception e) {
            monitor.warning("Failed to extract counterParty from JSON, using fallback", e);
            return "fallback-counterparty-" + System.currentTimeMillis();
        }
    }
    
    /**
     * Extrae contractId del JSON de petición de transferencia
     */
    private String extractContractIdFromJson(String jsonBody) {
        try {
            if (jsonBody.contains("contractId")) {
                String[] parts = jsonBody.split("contractId")[1].split("\"");
                if (parts.length > 2) {
                    return parts[2];
                }
            }
            return "unknown-contract-" + System.currentTimeMillis();
        } catch (Exception e) {
            monitor.warning("Failed to extract contractId from JSON", e);
            return "fallback-contract-" + System.currentTimeMillis();
        }
    }
    
    /**
     * Obtiene counterParty desde contractId (lógica específica del dominio)
     */
    private String extractCounterPartyFromContract(String contractId) {
        // En una implementación real, consultarías el ContractNegotiationStore
        // o ContractDefinitionService para obtener el counterParty real
        
        // Por ahora, implementación de fallback
        return "counterparty-from-" + contractId.hashCode();
    }
    
    /**
     * Crea respuesta de error estándar
     */
    private Response createErrorResponse(String message, Response.Status status) {
        Map<String, Object> error = new HashMap<>();
        error.put("@type", "ApiErrorDetail");
        error.put("message", message);
        error.put("type", status.getReasonPhrase());
        error.put("code", String.valueOf(status.getStatusCode()));
        error.put("timestamp", System.currentTimeMillis());
        
        return Response.status(status).entity(error).build();
    }
}
