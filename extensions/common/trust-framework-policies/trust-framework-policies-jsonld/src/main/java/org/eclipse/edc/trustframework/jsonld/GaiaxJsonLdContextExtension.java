package org.eclipse.edc.trustframework.jsonld;

import org.eclipse.edc.jsonld.spi.JsonLd;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;

import java.net.URI;
import java.net.URISyntaxException;

@Extension(value = "GAIA-X JSON-LD Context Cache")
public class GaiaxJsonLdContextExtension implements ServiceExtension {

    @Inject
    private JsonLd jsonLd;

    @Override
    public void initialize(ServiceExtensionContext context) {
        try {
            // GAIA-X 22.06 context
            var context2206 = getClass()
                    .getClassLoader()
                    .getResource("context/gaiax-context-2206.jsonld")
                    .toURI();

            jsonLd.registerCachedDocument(
                    "https://registry.gaia-x.eu/v2206/api/shape",
                    context2206
            );

            // GAIA-X 24.11 context
            var context2411 = getClass().getClassLoader()
                    .getResource("context/gaiax-context-2411.jsonld")
                    .toURI();
            jsonLd.registerCachedDocument(
                    "https://registry.lab.gaia-x.eu/main/context/2411",
                    context2411
            );            

            context.getMonitor().info("GAIA-X JSON-LD context cached locally");
        } catch (URISyntaxException e) {
            throw new RuntimeException("Failed to load GAIA-X context from resources", e);
        }
    }
}