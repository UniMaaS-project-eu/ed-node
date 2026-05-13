package be.flandersmake.edc.controlplane.dataaddress.rdf;

import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import org.eclipse.edc.validator.spi.DataAddressValidatorRegistry;

import be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFServiceDataAddressSchema;
import be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFSinkDataAddressSchema;
import be.flandersmake.edc.controlplane.dataaddress.rdf.validator.RDFServiceDataAddressValidator;
import be.flandersmake.edc.controlplane.dataaddress.rdf.validator.RDFSinkDataAddressValidator;

/**
 * Control plane extension that registers a DataAddress validator
 * for the "RDFService" DataAddress type.
 * <p>
 * This ensures that any DataAddress with type "RDFService" is validated
 * using {@link RDFServiceDataAddressValidator} before being accepted
 * into the system (e.g., during asset creation or contract negotiation).
 */
@Extension(RDFServiceDataAddressValidatorExtension.NAME)
public class RDFServiceDataAddressValidatorExtension implements ServiceExtension {

    public static final String NAME = "RDFService DataAddress Validator";

    @Inject
    private DataAddressValidatorRegistry registry;

    /**
     * Initializes the extension by registering the RDFService validator
     * for both source and destination DataAddresses of type "RDFService".
     *
     * @param context the extension context
     */
    @Override
    public void initialize(ServiceExtensionContext context) {
        var validator_service = new RDFServiceDataAddressValidator();
        var validator_sink = new RDFSinkDataAddressValidator();

        // Register for DataAddress.type == "RDFService" (source and destination)
        registry.registerSourceValidator(RDFServiceDataAddressSchema.RDF_SERVICE_TYPE, validator_service);
        registry.registerDestinationValidator(RDFServiceDataAddressSchema.RDF_SERVICE_TYPE, validator_service);
        registry.registerDestinationValidator(RDFSinkDataAddressSchema.RDF_DATA_SINK_TYPE, validator_sink);
    }
}
