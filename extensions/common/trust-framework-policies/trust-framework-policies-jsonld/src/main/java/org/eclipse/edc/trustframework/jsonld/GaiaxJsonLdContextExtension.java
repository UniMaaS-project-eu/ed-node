package org.eclipse.edc.trustframework.jsonld;

import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

@Extension(value = "GAIA-X JSON-LD Context Cache")
public class GaiaxJsonLdContextExtension implements ServiceExtension {

    @Inject
    private JsonLd jsonLd;

//    @Override
//    public void initialize(ServiceExtensionContext context) {
//        try {
//            // GAIA-X 22.06 context
//            var context2206 = getClass()
//                    .getClassLoader()
//                    .getResource("/app/resources/context/gaiax-context-2206.jsonld")
//                    .toURI();
//
//            jsonLd.registerCachedDocument(
//                    "https://registry.gaia-x.eu/v2206/api/shape",
//                    context2206
//            );
//
//            // GAIA-X 24.11 context
//            var context2411 = getClass().getClassLoader()
//                    .getResource("/app/resources/context/gaiax-context-2411.jsonld")
//                    .toURI();
//            jsonLd.registerCachedDocument(
//                    "https://registry.lab.gaia-x.eu/main/context/2411",
//                    context2411
//            );            
//
//            context.getMonitor().info("GAIA-X JSON-LD context cached locally");
//        } catch (URISyntaxException e) {
//            throw new RuntimeException("Failed to load GAIA-X context from resources", e);
//        }
//    }

    @Override
    public void initialize(ServiceExtensionContext context) {
        try {
            // GAIA-X 22.06 context
            registerContext(
                    "https://registry.gaia-x.eu/v2206/api/shape",
                    "context/gaiax-context-2206.jsonld",
                    "/app/resources/context/gaiax-context-2206.jsonld",
                    context
            );

            // GAIA-X 24.11 context
            registerContext(
                    "https://registry.lab.gaia-x.eu/main/context/2411",
                    "context/gaiax-context-2411.jsonld",
                    "/app/resources/context/gaiax-context-2411.jsonld",
                    context
            );

            context.getMonitor().info("✅ GAIA-X JSON-LD contexts cached locally");

        } catch (Exception e) {
            throw new RuntimeException("❌ Failed to load GAIA-X JSON-LD contexts", e);
        }
    }


    private void registerContext(String url, String classpathPath, String filePath, ServiceExtensionContext context)
            throws URISyntaxException {
        URI contextUri = null;
        URL resourceUrl = getClass().getClassLoader().getResource(classpathPath);

        if (resourceUrl != null) {
            contextUri = resourceUrl.toURI();
            context.getMonitor().info("Found context on classpath: " + classpathPath);
        } else {
            File file = new File(filePath);
            if (file.exists()) {
                contextUri = file.toURI();
                context.getMonitor().info("Found context on filesystem: " + filePath);
            } else {
                context.getMonitor().warning("⚠️ Context not found: " + classpathPath + " or " + filePath);
            }
        }

        if (contextUri != null) {
            jsonLd.registerCachedDocument(url, contextUri);
        }
    }
}