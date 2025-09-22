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

package org.eclipse.edc.connector.vp.context;

import org.eclipse.edc.connector.vp.context.controller.VPManagementController;
import org.eclipse.edc.connector.vp.context.handler.VPCredentialRequestHandler;
import org.eclipse.edc.connector.vp.context.service.VPContextManager;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.spi.system.health.HealthCheckService;
import org.eclipse.edc.web.spi.WebService;

/**
 * Service Extension principal para el VP Context Manager.
 * 
 * Esta extensión inicializa y configura todos los componentes necesarios
 * para manejar contextos de Verifiable Presentations en EDC 0.14.0.
 * 
 * Funcionalidades principales:
 * - Inicialización del VPContextManager
 * - Registro del VPManagementController en el WebService
 * - Provisión de beans para inyección de dependencias
 * - Health checks para monitoreo
 * - Shutdown graceful de recursos
 */
@Extension(value = "VP Context Manager Extension")
public class VPContextExtension implements ServiceExtension {
    
    public static final String NAME = "VP Context Manager";
    public static final String VERSION = "1.0.0";
    
    @Inject
    private Monitor monitor;
    
    @Inject 
    private WebService webService;
    
    @Inject
    private HealthCheckService healthCheckService;
    
    private VPContextManager vpContextManager;
    private VPCredentialRequestHandler vpCredentialRequestHandler;
    private VPManagementController vpManagementController;
    
    @Override
    public String name() {
        return NAME;
    }

    public VPContextExtension() {
        System.out.println("=== VP CONTEXT EXTENSION CONSTRUCTOR CALLED ===");
    }
    
    @Override
    public void initialize(ServiceExtensionContext context) {
        System.out.println("=== VP CONTEXT EXTENSION INITIALIZE STARTING ===");
        monitor.info("Initializing {} v{}", NAME, VERSION);
        
        try {
            // Inicializar VP Context Manager
            vpContextManager = new VPContextManager(monitor, context);
            monitor.info("VPContextManager initialized successfully");
            
            // Crear handler para solicitudes VP
            vpCredentialRequestHandler = new VPCredentialRequestHandler(vpContextManager, monitor);
            monitor.info("VPCredentialRequestHandler created successfully");
            
            // Crear y registrar controller REST
            vpManagementController = new VPManagementController(vpContextManager, monitor);
            webService.registerResource(vpManagementController);
            monitor.info("VPManagementController registered in WebService");
            
            // Registrar health check
            registerHealthChecks();
            
            // Registrar shutdown hook
            registerShutdownHook(context);
            
            monitor.info("{} v{} initialized successfully", NAME, VERSION);
            
        } catch (Exception e) {
            monitor.severe("Failed to initialize VP Context Manager Extension", e);
            throw new RuntimeException("VP Context Manager Extension initialization failed", e);
        }
    }
    
    @Override
    public void shutdown() {
        monitor.info("Shutting down {} v{}", NAME, VERSION);
        
        try {
            // Shutdown VP Context Manager
            if (vpContextManager != null) {
                vpContextManager.shutdown();
                monitor.info("VPContextManager shutdown completed");
            }
            
            monitor.info("{} v{} shutdown completed successfully", NAME, VERSION);
            
        } catch (Exception e) {
            monitor.severe("Error during VP Context Manager Extension shutdown", e);
        }
    }
    
    /**
     * Proporciona VPContextManager para inyección de dependencias
     */
    @Provider
    public VPContextManager vpContextManager() {
        return vpContextManager;
    }
    
    /**
     * Proporciona VPCredentialRequestHandler para inyección de dependencias
     */
    @Provider
    public VPCredentialRequestHandler vpCredentialRequestHandler() {
        return vpCredentialRequestHandler;
    }
    
    /**
     * Proporciona VPManagementController para inyección de dependencias
     */
    @Provider
    public VPManagementController vpManagementController() {
        return vpManagementController;
    }
    
    /**
     * Registra health checks para monitoreo del sistema
     */
    private void registerHealthChecks() {
        // Health check para VP Context Manager
        healthCheckService.addReadinessProvider(() -> {
            try {
                if (vpContextManager == null) {
                    return HealthCheckService.Result.failed("VPContextManager not initialized");
                }
                
                var stats = vpContextManager.getStats();
                
                // Verificar que el sistema esté funcionando correctamente
                if (stats.getActiveContexts() >= 0) {
                    return HealthCheckService.Result.success();
                } else {
                    return HealthCheckService.Result.failed("VPContextManager in invalid state");
                }
                
            } catch (Exception e) {
                monitor.warning("VP Context Manager health check failed", e);
                return HealthCheckService.Result.failed("VP Context Manager health check error: " + e.getMessage());
            }
        });
        
        monitor.debug("VP Context Manager health checks registered");
    }
    
    /**
     * Registra shutdown hook para limpieza de recursos
     */
    private void registerShutdownHook(ServiceExtensionContext context) {
        // El shutdown se maneja automáticamente por EDC via el método shutdown()
        // Pero podemos registrar limpieza adicional si es necesario
        
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            monitor.info("VP Context Manager Extension shutdown hook executed");
            if (vpContextManager != null) {
                try {
                    // Estadísticas finales antes del shutdown
                    var finalStats = vpContextManager.getStats();
                    monitor.info("Final VP Context Manager stats: {}", finalStats);
                } catch (Exception e) {
                    monitor.warning("Error getting final stats during shutdown", e);
                }
            }
        }, "vp-context-shutdown-hook"));
    }
    
    /**
     * Validación de configuración en tiempo de inicialización
     */
    private void validateConfiguration(ServiceExtensionContext context) {
        // Validar configuraciones críticas
        long ttlMinutes = context.getSetting("edc.vp.context.ttl.minutes", 30L);
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("VP context TTL must be positive: " + ttlMinutes);
        }
        
        long cleanupInterval = context.getSetting("edc.vp.context.cleanup.interval.minutes", 5L);
        if (cleanupInterval <= 0) {
            throw new IllegalArgumentException("VP context cleanup interval must be positive: " + cleanupInterval);
        }
        
        int maxSize = context.getSetting("edc.vp.context.max.size", 10000);
        if (maxSize <= 0) {
            throw new IllegalArgumentException("VP context max size must be positive: " + maxSize);
        }
        
        monitor.info("VP Context Manager configuration validated - TTL: {}min, Cleanup: {}min, MaxSize: {}", 
            ttlMinutes, cleanupInterval, maxSize);
    }
    
    /**
     * Obtiene información de la extensión para debugging
     */
    public ExtensionInfo getExtensionInfo() {
        var stats = vpContextManager != null ? vpContextManager.getStats() : null;
        
        return new ExtensionInfo(
            NAME,
            VERSION,
            vpContextManager != null,
            vpCredentialRequestHandler != null,
            vpManagementController != null,
            stats
        );
    }
    
    /**
     * Clase inmutable con información de la extensión
     */
    public static class ExtensionInfo {
        private final String name;
        private final String version;
        private final boolean vpContextManagerInitialized;
        private final boolean vpCredentialRequestHandlerInitialized;
        private final boolean vpManagementControllerInitialized;
        private final VPContextManager.VPContextStats stats;
        
        public ExtensionInfo(String name, String version, 
                           boolean vpContextManagerInitialized,
                           boolean vpCredentialRequestHandlerInitialized,
                           boolean vpManagementControllerInitialized,
                           VPContextManager.VPContextStats stats) {
            this.name = name;
            this.version = version;
            this.vpContextManagerInitialized = vpContextManagerInitialized;
            this.vpCredentialRequestHandlerInitialized = vpCredentialRequestHandlerInitialized;
            this.vpManagementControllerInitialized = vpManagementControllerInitialized;
            this.stats = stats;
        }
        
        // Getters
        public String getName() { return name; }
        public String getVersion() { return version; }
        public boolean isVpContextManagerInitialized() { return vpContextManagerInitialized; }
        public boolean isVpCredentialRequestHandlerInitialized() { return vpCredentialRequestHandlerInitialized; }
        public boolean isVpManagementControllerInitialized() { return vpManagementControllerInitialized; }
        public VPContextManager.VPContextStats getStats() { return stats; }
        
        @Override
        public String toString() {
            return String.format("ExtensionInfo{name='%s', version='%s', initialized=%b/%b/%b, stats=%s}",
                name, version, vpContextManagerInitialized, vpCredentialRequestHandlerInitialized,
                vpManagementControllerInitialized, stats);
        }
    }
}