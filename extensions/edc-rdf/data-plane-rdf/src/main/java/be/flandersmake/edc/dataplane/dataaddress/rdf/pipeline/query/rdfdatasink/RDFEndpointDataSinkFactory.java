package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.query.rdfdatasink;

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSinkFactory;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.jetbrains.annotations.NotNull;

import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFServiceDataAddressSchema.CREDENTIAL_PASSWORD;
import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFServiceDataAddressSchema.CREDENTIAL_USER;
import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFServiceDataAddressSchema.SERIALIZATION;
import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFSinkDataAddressSchema.*;

import java.nio.channels.UnsupportedAddressTypeException;

public class RDFEndpointDataSinkFactory implements DataSinkFactory {

    @Override
    public String supportedType() {
        return RDF_DATA_SINK_TYPE;
    }

    @Override
    public DataSink createSink(DataFlowStartMessage request) {
        DataAddress address = request.getDestinationDataAddress();
        String endpoint_type = address.getStringProperty(STORE_TYPE);
        if (endpoint_type.equalsIgnoreCase("sparql")) {
            String endpoint_url = address.getStringProperty(SINK_ENDPOINT_URL);
            String serialization_source = request.getSourceDataAddress().getStringProperty(SERIALIZATION);
            String credential_user = address.getStringProperty(CREDENTIAL_USER);
            String credential_password = address.getStringProperty(CREDENTIAL_PASSWORD);
            String graph = address.getStringProperty(SINK_GRAPH);
            return new RDFStoreSparqlEndpointDataSink(endpoint_url, serialization_source, credential_user,
                    credential_password, graph);
        } else if (endpoint_type.equalsIgnoreCase("file")) {
            String endpoint_url = address.getStringProperty(SINK_ENDPOINT_URL);
            String serialization_target = address.getStringProperty(SERIALIZATION_SINK);
            String serialization_source = request.getSourceDataAddress().getStringProperty(SERIALIZATION);
            String file_name = address.getStringProperty(FILE_NAME);
            return new RDFFileDataSink(endpoint_url, request.getProcessId(), serialization_source, serialization_target, file_name);
        } else {
            throw new UnsupportedAddressTypeException();
        }
    }

    @Override
    public @NotNull Result<Void> validateRequest(DataFlowStartMessage request) {
        var destination = request.getDestinationDataAddress();
        if (!RDF_DATA_SINK_TYPE.equals(destination.getType())) {
            return Result.failure("Invalid dataDestination.type: " + destination.getType());
        }

        // Validate required RDF sink properties
        if (destination.getProperty(SINK_ENDPOINT_URL) == null) {
            return Result.failure("Missing required property:" + SINK_ENDPOINT_URL);
        }

        if (request.getId() == null) {
            return Result.failure("Missing request ID");
        }
        return Result.success();
    }

}
