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

package org.eclipse.edc.connector.vp.context.service;

import org.eclipse.edc.connector.vp.context.model.VPContext;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gestor thread-safe para contextos de Verifiable Presentations.
 * 
 * Proporciona almacenamiento temporal de VP strings hasta que sean
 * requeridos por el flujo DSP de EDC.
 */
public class VPContextManager {
    
    private static final String SETTING_TTL_MINUTES = "edc.vp.context.ttl.minutes";
    private static final String SETTING_CLEANUP_INTERVAL_MINUTES = "edc.vp.context.cleanup.interval.minutes";
    private static final String SETTING_MAX_SIZE = "edc.vp.context.max.size";
    
    private static final long DEFAULT_TTL_MINUTES = 30;
    private static final long DEFAULT_CLEANUP_INTERVAL_MINUTES = 5;
    private static final int DEFAULT_MAX_SIZE = 10000;
    
    // Thread-safe storage
    private final Map<String, VPContext> vpContextStore = new ConcurrentHashMap<>();
    private final Map<String, String> correlationToContext = new ConcurrentHashMap<>();
    
    private final ScheduledExecutorService cleanupExecutor;
    private final Monitor monitor;
    private final long ttlMillis;
    private final int maxSize;
    
    // Métricas
    private final AtomicLong totalStored = new AtomicLong(0);
    private final AtomicLong totalRetrieved = new AtomicLong(0);
    private final AtomicLong totalExpired = new AtomicLong(0);
    
    /**
     * Constructor que inicializa el manager con configuración del contexto EDC
     * 
     * @param monitor Monitor EDC para logging
     * @param context Contexto del ServiceExtension para obtener configuración
     */
    public VPContextManager(Monitor monitor, ServiceExtensionContext context) {
        this.monitor = monitor;
        this.ttlMillis = context.getSetting(SETTING_TTL_MINUTES, DEFAULT_TTL_MINUTES) * 60 * 1000;
        this.maxSize = context.getSetting(SETTING_MAX_SIZE, DEFAULT_MAX_SIZE);
        
        long cleanupInterval = context.getSetting(SETTING_CLEANUP_INTERVAL_MINUTES, DEFAULT_CLEANUP_INTERVAL_MINUTES);
        
        // Configurar executor para limpieza automática
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "vp-context-cleanup");
            t.setDaemon(true);
            return t;
        });
        
        // Iniciar limpieza automática
        cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredContexts, 
            cleanupInterval, cleanupInterval, TimeUnit.MINUTES);
        
        monitor.info("VPContextManager initialized - TTL: {}ms, MaxSize: {}, CleanupInterval: {}min", 
            ttlMillis, maxSize, cleanupInterval);
    }
    
    /**
     * Almacena un contexto VP y devuelve el contextId generado.
     * Thread-safe para uso concurrente.
     * 
     * @param vpString String representación del VP (JWT, JSON-LD, etc.)
     * @param counterPartyId ID del counterParty que solicitará el VP
     * @param triggerType Tipo de operación que desencadenó la necesidad de VP
     * @param originalRequestId ID de la petición original (opcional)
     * @return contextId único para tracking
     * @throws IllegalStateException si se alcanza el límite máximo de contextos
     */
    public String storeVPContext(String vpString, String counterPartyId, 
                                VPContext.VPTriggerType triggerType, String originalRequestId) {
        
        // Verificar límite de tamaño
        if (vpContextStore.size() >= maxSize) {
            // Forzar limpieza antes de rechazar
            cleanupExpiredContexts();
            if (vpContextStore.size() >= maxSize) {
                throw new IllegalStateException("VP context store is full. Max size: " + maxSize);
            }
        }
        
        String contextId = UUID.randomUUID().toString();
        VPContext context = new VPContext(vpString, counterPartyId, triggerType, originalRequestId);
        
        vpContextStore.put(contextId, context);
        totalStored.incrementAndGet();
        
        monitor.debug("VP context stored - contextId: {}, triggerType: {}, counterParty: {}", 
            contextId, triggerType, counterPartyId);
        
        return contextId;
    }
    
    /**
     * Registra una correlación entre un correlationId (de DSP) y el contextId.
     * Esto permite recuperar el contexto VP cuando llega la solicitud del provider.
     * 
     * @param contextId ID del contexto VP almacenado
     * @param correlationId ID de correlación del protocolo DSP
     */
    public void registerCorrelation(String contextId, String correlationId) {
        if (vpContextStore.containsKey(contextId)) {
            correlationToContext.put(correlationId, contextId);
            monitor.debug("VP correlation registered - correlationId: {}, contextId: {}", 
                correlationId, contextId);
        } else {
            monitor.warning("Cannot register correlation for non-existent contextId: {}", contextId);
        }
    }
    
    /**
     * Recupera un contexto VP por contextId y lo elimina del store.
     * Operación thread-safe y atómica.
     * 
     * @param contextId ID del contexto a recuperar
     * @return Optional con el contexto si existe, empty si no se encuentra o expiró
     */
    public Optional<VPContext> retrieveAndRemoveVPContext(String contextId) {
        VPContext context = vpContextStore.remove(contextId);
        
        if (context != null) {
            if (context.isExpired(ttlMillis)) {
                totalExpired.incrementAndGet();
                monitor.warning("VP context expired - contextId: {}, age: {}ms", 
                    contextId, context.getAgeMillis());
                return Optional.empty();
            }
            
            totalRetrieved.incrementAndGet();
            monitor.debug("VP context retrieved and removed - contextId: {}, triggerType: {}", 
                contextId, context.getTriggerType());
            return Optional.of(context);
        }
        
        monitor.warning("VP context not found - contextId: {}", contextId);
        return Optional.empty();
    }
    
    /**
     * Recupera un contexto VP por correlationId y lo elimina del store.
     * Utiliza el mapeo correlationId -> contextId para encontrar el contexto.
     * 
     * @param correlationId ID de correlación del protocolo DSP
     * @return Optional con el contexto si existe, empty si no se encuentra
     */
    public Optional<VPContext> retrieveByCorrelation(String correlationId) {
        String contextId = correlationToContext.remove(correlationId);
        
        if (contextId != null) {
            return retrieveAndRemoveVPContext(contextId);
        }
        
        monitor.warning("VP context not found for correlationId: {}", correlationId);
        return Optional.empty();
    }
    
    /**
     * Recupera un contexto sin eliminarlo (para consulta/debugging).
     * 
     * @param contextId ID del contexto a consultar
     * @return Optional con el contexto si existe y no ha expirado
     */
    public Optional<VPContext> getVPContext(String contextId) {
        VPContext context = vpContextStore.get(contextId);
        
        if (context != null) {
            if (context.isExpired(ttlMillis)) {
                // Context expirado, eliminarlo
                vpContextStore.remove(contextId);
                totalExpired.incrementAndGet();
                return Optional.empty();
            }
            return Optional.of(context);
        }
        
        return Optional.empty();
    }
    
    /**
     * Limpia contextos expirados automáticamente.
     * Ejecutado por el ScheduledExecutorService periódicamente.
     */
    private void cleanupExpiredContexts() {
        int removedContexts = 0;
        int removedCorrelations = 0;
        
        // Limpiar contextos principales expirados
        var contextIterator = vpContextStore.entrySet().iterator();
        while (contextIterator.hasNext()) {
            var entry = contextIterator.next();
            if (entry.getValue().isExpired(ttlMillis)) {
                contextIterator.remove();
                removedContexts++;
                totalExpired.incrementAndGet();
            }
        }
        
        // Limpiar correlaciones huérfanas
        var correlationIterator = correlationToContext.entrySet().iterator();
        while (correlationIterator.hasNext()) {
            var entry = correlationIterator.next();
            if (!vpContextStore.containsKey(entry.getValue())) {
                correlationIterator.remove();
                removedCorrelations++;
            }
        }
        
        if (removedContexts > 0 || removedCorrelations > 0) {
            monitor.info("VP context cleanup completed - {} expired contexts removed, {} orphaned correlations removed", 
                removedContexts, removedCorrelations);
        }
    }
    
    /**
     * Obtiene estadísticas del manager para monitoreo
     * 
     * @return Estadísticas actuales del VP Context Manager
     */
    public VPContextStats getStats() {
        return new VPContextStats(
            vpContextStore.size(),
            correlationToContext.size(),
            totalStored.get(),
            totalRetrieved.get(),
            totalExpired.get()
        );
    }
    
    /**
     * Fuerza la limpieza manual de contextos expirados
     * 
     * @return Número de contextos eliminados
     */
    public int forceCleanup() {
        int initialSize = vpContextStore.size();
        cleanupExpiredContexts();
        return initialSize - vpContextStore.size();
    }
    
    /**
     * Cierra el manager y sus recursos de forma segura
     */
    public void shutdown() {
        monitor.info("Shutting down VPContextManager...");
        cleanupExecutor.shutdown();
        try {
            if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                cleanupExecutor.shutdownNow();
                monitor.warning("VP Context cleanup executor forced shutdown");
            }
        } catch (InterruptedException e) {
            cleanupExecutor.shutdownNow();
            Thread.currentThread().interrupt();
            monitor.warning("VP Context cleanup executor interrupted during shutdown");
        }
        
        vpContextStore.clear();
        correlationToContext.clear();
        monitor.info("VPContextManager shutdown completed - Final stats: {}", getStats());
    }
    
    /**
     * Clase inmutable para estadísticas del VP Context Manager
     */
    public static class VPContextStats {
        private final int activeContexts;
        private final int activeCorrelations;
        private final long totalStored;
        private final long totalRetrieved;
        private final long totalExpired;
        
        public VPContextStats(int activeContexts, int activeCorrelations, 
                             long totalStored, long totalRetrieved, long totalExpired) {
            this.activeContexts = activeContexts;
            this.activeCorrelations = activeCorrelations;
            this.totalStored = totalStored;
            this.totalRetrieved = totalRetrieved;
            this.totalExpired = totalExpired;
        }
        
        public int getActiveContexts() { return activeContexts; }
        public int getActiveCorrelations() { return activeCorrelations; }
        public long getTotalStored() { return totalStored; }
        public long getTotalRetrieved() { return totalRetrieved; }
        public long getTotalExpired() { return totalExpired; }
        
        @Override
        public String toString() {
            return String.format("VPContextStats{active: %d, correlations: %d, stored: %d, retrieved: %d, expired: %d}", 
                activeContexts, activeCorrelations, totalStored, totalRetrieved, totalExpired);
        }
    }
}