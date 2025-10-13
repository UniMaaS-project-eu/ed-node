package org.eclipse.edc.trustframework.jsonld;

import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.runtime.metamodel.annotation.Setting;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// DEPRECATED: REPLACED BY MOCK REGISTRY
@Extension(value = "GAIA-X JSON-LD Context Cache")
public class GaiaxJsonLdContextExtension implements ServiceExtension {
/*
    @Setting(value = "Base path for GAIA-X context files", defaultValue = "/app/resources/context")
    private static final String GAIAX_CONTEXT_PATH = "edc.gaiax.context.path";

    @Setting(key = "unimaas.mockregistry.context.url", description = "Local GAIA-X mock registry service", defaultValue = "http://localhost/main/context/2411")
    private String mockRegistryUrl;

    @Inject
    private JsonLd jsonLd;

    @Override
    public String name() {
        return "GAIA-X JSON-LD Context Cache";
    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        
        try {
            var basePath = context.getSetting(GAIAX_CONTEXT_PATH, "/app/resources/context");
            
            // GAIA-X 22.06 context
            registerContext(
                    "https://registry.gaia-x.eu/v2206/api/shape",
                    basePath,
                    "gaiax-context-2206.jsonld",
                    context
            );

            // GAIA-X 24.11 context
            registerContext(
                    "https://registry.lab.gaia-x.eu/main/context/2411",
                    basePath,
                    "gaiax-context-2411.jsonld",
                    context
            );

            // Mock GAIA-X 24.11 context
            registerContext(
                    mockRegistryUrl,
                    basePath,
                    "gaiax-context-2411.jsonld",
                    context
            );


            monitor.info("✅ GAIA-X JSON-LD contexts registered successfully");

        } catch (Exception e) {
            monitor.severe("❌ Failed to register GAIA-X JSON-LD contexts", e);
            throw new RuntimeException("Critical: GAIA-X context registration failed", e);
        }
    }

    private void registerContext(String url, String basePath, String filename, ServiceExtensionContext context) {
        var monitor = context.getMonitor();
        URI contextUri = null;

        try {
            // Intento 1: Buscar en el classpath
            var resourceUrl = getClass().getClassLoader().getResource("context/" + filename);
            if (resourceUrl != null) {
                contextUri = resourceUrl.toURI();
                monitor.info(String.format("✓ Found context in classpath: context/%s", filename));
            } else {
                // Intento 2: Buscar en el sistema de archivos
                var filePaths = new String[]{
                        basePath + "/" + filename,
                        "/app/resources/context/" + filename,
                        "./resources/context/" + filename,
                        "../resources/context/" + filename
                };

                for (String filePath : filePaths) {
                    Path path = Paths.get(filePath);
                    if (Files.exists(path) && Files.isReadable(path)) {
                        contextUri = path.toUri();
                        monitor.info(String.format("✓ Found context in filesystem: %s", filePath));
                        break;
                    }
                }

                if (contextUri == null) {
                    monitor.warning(String.format("⚠️ Context file not found: %s", filename));
                    monitor.warning("Searched in:");
                    for (String path : filePaths) {
                        monitor.warning("  - " + path);
                    }
                    return;
                }
            }

            // Registrar el contexto en el cache de JsonLd
            jsonLd.registerCachedDocument(url, contextUri);
            monitor.info(String.format("✅ Registered %s -> %s", url, contextUri));

        } catch (Exception e) {
            monitor.severe(String.format("❌ Failed to register context for %s", url), e);
        }
    }
*/
}