package org.eclipse.edc.odins.extension.xusercontext;

import okhttp3.OkHttpClient;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.web.spi.WebService;
import org.eclipse.edc.web.spi.configuration.PortMappingRegistry;

import java.util.List;

/**
 * Extensión unificada que maneja tanto peticiones entrantes como salientes
 * para la propagación del header X-User-Context en EDC 0.14.0
 */
public class UnifiedUserContextExtension implements ServiceExtension {

    private static final List<String> PREFERRED_CONTEXTS = List.of(
            "dsp",           // DSP API context - PRIORITARIO para comunicación inter-connector
            "protocol",      // Protocol context
            "management",    // Management API context  
            "default"        // Fallback context
    );

    @Inject
    private WebService webService;
    
    @Inject 
    private PortMappingRegistry portMappingRegistry;

    @Override
    public String name() {
        return "Unified User Context Propagation Extension";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        Monitor monitor = context.getMonitor();

        // 1. CONFIGURAR INTERCEPTOR PARA PETICIONES SALIENTES
        setupOutboundInterceptor(context, monitor);

        // 2. REGISTRAR FILTRO PARA PETICIONES ENTRANTES  
        setupInboundFilter(context, monitor);

        monitor.info("✓ Unified User Context Extension initialized successfully for EDC 0.14.0");
    }

    private void setupOutboundInterceptor(ServiceExtensionContext context, Monitor monitor) {
        try {
            // Verificar si ya existe un OkHttpClient registrado
            OkHttpClient existingClient = null;
            try {
                existingClient = context.getService(OkHttpClient.class);
            } catch (Exception e) {
                // No existe, crearemos uno nuevo
            }

            OkHttpClient clientWithInterceptor;
            
            if (existingClient != null) {
                // Crear un nuevo cliente basado en el existente, añadiendo nuestro interceptor
                clientWithInterceptor = existingClient.newBuilder()
                        .addInterceptor(new OutboundUserContextInterceptor(monitor))
                        .build();
                
                monitor.info("✓ Added UserContextInterceptor to existing OkHttpClient");
            } else {
                // Crear un cliente completamente nuevo
                clientWithInterceptor = new OkHttpClient.Builder()
                        .addInterceptor(new OutboundUserContextInterceptor(monitor))
                        .build();
                
                monitor.info("✓ Created new OkHttpClient with UserContextInterceptor");
            }

            // Registrar el cliente (esto sobrescribirá el existente si lo había)
            context.registerService(OkHttpClient.class, clientWithInterceptor);
            
            monitor.info("✓ OkHttpClient with UserContextInterceptor registered successfully");

        } catch (Exception e) {
            monitor.severe("✗ Failed to setup outbound interceptor", e);
            throw new RuntimeException("Failed to setup outbound interceptor", e);
        }
    }

    private void setupInboundFilter(ServiceExtensionContext context, Monitor monitor) {
        try {
            var availableContexts = portMappingRegistry.getAll().stream()
                    .map(mapping -> mapping.name())
                    .toList();

            monitor.info("=== USER CONTEXT INBOUND FILTER SETUP ===");
            monitor.info("Available web contexts: " + availableContexts);

            // TEMPORAL: Intentar registrar en TODOS los contextos para debug
            boolean registeredInAny = false;
            
            for (String contextName : availableContexts) {
                try {
                    InboundUserContextFilter inboundFilter = new InboundUserContextFilter(monitor);
                    webService.registerResource(contextName, inboundFilter);
                    monitor.info("✓ Successfully registered InboundUserContextFilter in context: " + contextName);
                    registeredInAny = true;
                } catch (Exception e) {
                    monitor.warning("✗ Failed to register in context '" + contextName + "': " + e.getMessage());
                }
            }
            
            if (!registeredInAny) {
                monitor.severe("✗ Failed to register InboundUserContextFilter in ANY context!");
                throw new RuntimeException("Could not register filter in any available context");
            }

            monitor.info("=== END INBOUND SETUP ===");

        } catch (Exception e) {
            monitor.severe("✗ Failed to setup inbound filter", e);
            throw new RuntimeException("Failed to setup inbound filter", e);
        }
    }

    private String findBestAvailableContext(List<String> availableContexts, Monitor monitor) {
        // Buscar específicamente 'dsp' primero (comunicación inter-connector)
        if (availableContexts.contains("dsp")) {
            monitor.info("✓ Selected 'dsp' context for inter-connector communication");
            return "dsp";
        }

        // Buscar otros contextos preferidos
        for (String preferredContext : PREFERRED_CONTEXTS) {
            if (availableContexts.contains(preferredContext)) {
                monitor.info("✓ Selected preferred context: " + preferredContext);
                return preferredContext;
            }
        }

        // Fallback al primer disponible
        if (!availableContexts.isEmpty()) {
            String firstAvailable = availableContexts.get(0);
            monitor.warning("⚠ No preferred context found, using first available: " + firstAvailable);
            return firstAvailable;
        }

        // Último recurso
        monitor.warning("⚠ No web contexts found, falling back to 'default'");
        return "default";
    }

    @Override
    public void shutdown() {
        // Limpiar cualquier ThreadLocal que pueda quedar
        UserContextHolder.clear();
    }
}
