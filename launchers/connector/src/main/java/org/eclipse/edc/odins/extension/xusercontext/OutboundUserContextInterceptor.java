package org.eclipse.edc.odins.extension.xusercontext;

import java.io.IOException;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.eclipse.edc.spi.monitor.Monitor;

/**
 * Interceptor mejorado para peticiones SALIENTES (client-side) usando OkHttp:
 * Añade el header X-User-Context a todas las peticiones salientes,
 * obteniendo el valor desde UserContextHolder (ThreadLocal).
 */
public class OutboundUserContextInterceptor implements Interceptor {

    private static final String HEADER_NAME = "X-User-Context";
    private final Monitor monitor;

    public OutboundUserContextInterceptor(Monitor monitor) {
        this.monitor = monitor;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request original = chain.request();
        String url = original.url().toString();
        String user = UserContextHolder.getCurrentUser();
        
        // TEMPORAL: Loguear TODAS las peticiones outbound para debug
        monitor.info("🔍 OUTBOUND INTERCEPTOR - ALL REQUESTS:");
        monitor.info("  URL: " + url);
        monitor.info("  Method: " + original.method());
        monitor.info("  Thread: " + Thread.currentThread().getName());
        monitor.info("  Current User from ThreadLocal: " + (user != null ? user : "NULL"));
        
        // Solo hacer debug detallado para peticiones DSP o relevantes
        boolean isRelevantRequest = isRelevantForUserContext(url);
        monitor.info("  ↳ Is relevant: " + isRelevantRequest);
        
        Request.Builder builder = original.newBuilder();

        if (user != null && !user.trim().isEmpty()) {
            builder.header(HEADER_NAME, user);
            monitor.info("✓ OUTBOUND: Added X-User-Context=" + user + " to " + url);
        } else if (isRelevantRequest || url.contains("/api/")) {
            monitor.warning("⚠ OUTBOUND: No user context available for " + url);
            monitor.warning("  ThreadLocal value is: " + user);
        }

        Request modifiedRequest = builder.build();
        
        // DEBUG: Mostrar headers solo para peticiones relevantes
        if (isRelevantRequest) {
            monitor.info("=== OUTBOUND DETAILED DEBUG ===");
            monitor.info("Final request headers:");
            modifiedRequest.headers().forEach(pair -> {
                monitor.info("  " + pair.getFirst() + ": " + pair.getSecond());
            });
            monitor.info("=== END OUTBOUND DEBUG ===");
        }

        return chain.proceed(modifiedRequest);
    }

    /**
     * Determina si una petición es relevante para el contexto de usuario
     */
    private boolean isRelevantForUserContext(String url) {
        return url.contains("/api/dsp") ||           // DSP protocol calls
               url.contains("/catalog") ||           // Catalog requests  
               url.contains("/negotiation") ||       // Contract negotiation
               url.contains("/transfer") ||          // Data transfer
               url.contains("/dataspace");           // General dataspace calls
    }
}