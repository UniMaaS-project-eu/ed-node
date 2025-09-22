package org.eclipse.edc.odins.extension.xusercontext;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.edc.spi.monitor.Monitor;

import java.io.IOException;

/**
 * Filtro mejorado para peticiones ENTRANTES (server-side).
 * Captura el header X-User-Context y lo establece en UserContextHolder
 * para que se pueda propagar en las peticiones salientes.
 */
@Provider
public class InboundUserContextFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final String HEADER_NAME = "X-User-Context";
    private final Monitor monitor;

    // ThreadLocal auxiliar para evitar fugas de memoria
    private final ThreadLocal<String> inboundValue = new ThreadLocal<>();

    public InboundUserContextFilter(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String requestUri = requestContext.getUriInfo().getRequestUri().toString();
        String method = requestContext.getMethod();
        
        // TEMPORAL: Loguear TODAS las peticiones para debug
        monitor.info("🔍 INBOUND FILTER - ALL REQUESTS:");
        monitor.info("  URI: " + requestUri);
        monitor.info("  Method: " + method);
        monitor.info("  Thread: " + Thread.currentThread().getName());
        
        // Ignorar peticiones de health check y otras peticiones no relevantes
        if (isIgnorableRequest(requestUri)) {
            monitor.debug("  ↳ Ignoring health/status request");
            return;
        }
        
        String value = requestContext.getHeaderString(HEADER_NAME);
        
        // Solo hacer debug detallado para peticiones DSP o Management API
        boolean isRelevantRequest = isRelevantForUserContext(requestUri);
        
        monitor.info("  ↳ Is relevant: " + isRelevantRequest);
        monitor.info("  ↳ X-User-Context header: " + (value != null ? value : "NOT FOUND"));
        
        if (isRelevantRequest) {
            monitor.info("=== INBOUND FILTER DEBUG ===");
            monitor.info("Thread: " + Thread.currentThread().getName());
            monitor.info("Request URI: " + requestUri);
            monitor.info("Request Method: " + requestContext.getMethod());
            
            // Mostrar headers solo para peticiones relevantes
            monitor.info("All incoming headers:");
            requestContext.getHeaders().forEach((name, values) -> {
                monitor.info("  " + name + ": " + values);
            });
        }
        
        if (value != null && !value.trim().isEmpty()) {
            inboundValue.set(value);
            UserContextHolder.setCurrentUser(value);
            monitor.info("✓ INBOUND: Received and set X-User-Context=" + value + " for " + requestUri);
            
            if (isRelevantRequest) {
                monitor.info("✓ ThreadLocal set for thread: " + Thread.currentThread().getName());
            }
        } else if (isRelevantRequest) {
            monitor.warning("⚠ INBOUND: No X-User-Context header in request to " + requestUri);
        }
        
        // Verificar que el ThreadLocal se estableció correctamente
        if (isRelevantRequest) {
            String verifyUser = UserContextHolder.getCurrentUser();
            monitor.info("Verification - Current user from ThreadLocal: " + verifyUser);
            monitor.info("=== END INBOUND DEBUG ===");
        }
    }

    /**
     * Determina si una petición debe ser ignorada completamente
     */
    private boolean isIgnorableRequest(String requestUri) {
        return requestUri.contains("/health") ||
               requestUri.contains("/check") ||
               requestUri.contains("/metrics") ||
               requestUri.contains("/status") ||
               requestUri.contains("/actuator");
    }

    /**
     * Determina si una petición es relevante para el contexto de usuario
     */
    private boolean isRelevantForUserContext(String requestUri) {
        return requestUri.contains("/api/dsp") ||           // DSP protocol calls
               requestUri.contains("/api/management") ||     // Management API calls
               requestUri.contains("/catalog") ||            // Catalog requests
               requestUri.contains("/negotiation") ||        // Contract negotiation
               requestUri.contains("/transfer");             // Data transfer
    }

    @Override
    public void filter(ContainerRequestContext requestContext,
                       ContainerResponseContext responseContext) throws IOException {
        String requestUri = requestContext.getUriInfo().getRequestUri().toString();
        
        // Ignorar peticiones no relevantes
        if (isIgnorableRequest(requestUri)) {
            return;
        }
        
        String value = inboundValue.get();
        boolean isRelevantRequest = isRelevantForUserContext(requestUri);
        
        if (isRelevantRequest) {
            monitor.info("=== INBOUND RESPONSE FILTER DEBUG ===");
            monitor.info("Thread: " + Thread.currentThread().getName());
            monitor.info("Stored inbound value: " + value);
            monitor.info("Current ThreadLocal value: " + UserContextHolder.getCurrentUser());
        }
        
        if (value != null) {
            // Opcional: devolver el header al consumidor
            responseContext.getHeaders().putSingle(HEADER_NAME, value);
            if (isRelevantRequest) {
                monitor.info("✓ INBOUND: Echoed X-User-Context=" + value + " back to response");
            }
        }

        // Limpiar ThreadLocals para evitar memory leaks
        inboundValue.remove();
        UserContextHolder.clear();
        
        if (isRelevantRequest) {
            monitor.info("✓ ThreadLocal cleared for thread: " + Thread.currentThread().getName());
            monitor.info("=== END INBOUND RESPONSE DEBUG ===");
        }
    }
}