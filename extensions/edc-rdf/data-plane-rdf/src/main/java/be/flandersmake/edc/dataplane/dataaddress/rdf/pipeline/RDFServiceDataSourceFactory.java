package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline;

import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFServiceDataAddressSchema.*;
import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFSinkDataAddressSchema.SERIALIZATION_SINK;

import org.apache.jena.base.Sys;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSourceFactory;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.jetbrains.annotations.NotNull;

import be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.api.RDFFileEndpointDataSource;
import be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.file.RDFFileDataSource;
import be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.query.RDFSparqlQueryDataSource;

public class RDFServiceDataSourceFactory implements DataSourceFactory {

    @Override
    public String supportedType() {
        return RDF_SERVICE_TYPE;
    }

    // creates different data sources based on the data address transfer type
    @Override
    public DataSource createSource(DataFlowStartMessage request) {
        String typeTransfer = request.getSourceDataAddress().getStringProperty(SERVICE_TYPE);
        System.err.println("Type transfer" + typeTransfer);
        // "sparqlendpoint", "fileendpoint" or "file".
        String target_serialization = request.getDestinationDataAddress().getStringProperty(SERIALIZATION_SINK);
            if(target_serialization == null){
                String serializationParam = request.getProperties().get("queryParams");
                if(serializationParam.startsWith(SERIALIZATION_SINK)){
                    target_serialization = serializationParam.split("=")[1]; 
                }
                System.err.print("target_serilization" + target_serialization);
            }
        if (typeTransfer.equalsIgnoreCase("sparqlendpoint")) {
            String endpoint = request.getSourceDataAddress().getStringProperty(SPARQL_ENDPOINT);
            String query = request.getSourceDataAddress().getStringProperty(QUERY);
            String credential_user = request.getSourceDataAddress().getStringProperty(CREDENTIAL_USER);
            String credential_password = request.getSourceDataAddress().getStringProperty(CREDENTIAL_PASSWORD);
            return new RDFSparqlQueryDataSource(endpoint, query, credential_user, credential_password,
                    target_serialization);
        } else if (typeTransfer.equalsIgnoreCase("httpendpoint")) { // This acts similarly to the HttpData Data Address
            String endpoint = request.getSourceDataAddress().getStringProperty(FILE_ENDPOINT);
            String serialization = request.getSourceDataAddress().getStringProperty(SERIALIZATION);
            String credential_user = request.getSourceDataAddress().getStringProperty(CREDENTIAL_USER);
            String credential_password = request.getSourceDataAddress().getStringProperty(CREDENTIAL_PASSWORD);
            return new RDFFileEndpointDataSource(endpoint, serialization, credential_user, credential_password);
        } else if (typeTransfer.equalsIgnoreCase("file")) {
            String path = request.getSourceDataAddress().getStringProperty(FILE);
            String serialization = request.getSourceDataAddress().getStringProperty(SERIALIZATION);
            return new RDFFileDataSource(path, serialization, target_serialization);
        } else {
            throw new RuntimeException("Data address service type not set to correct value, transfer cancelled");
        }
    }

    @Override
    public @NotNull Result<Void> validateRequest(DataFlowStartMessage request) {
        var source = request.getSourceDataAddress();
        if (!RDF_SERVICE_TYPE.equals(source.getType())) {
            return Result.failure("Invalid dataSource.type: " + source.getType());
        }
        var serviceType = source.getStringProperty(SERVICE_TYPE);
        if (serviceType == null || serviceType.isBlank()) {
            return Result.failure("Missing required property: edc_rdf:serviceType");
        }
        switch (serviceType.toLowerCase()) {
            case "file" -> {
                var file = source.getStringProperty(FILE);
                if (file == null || file.isBlank()) {
                    return Result.failure("Missing required property for file mode");
                }
            }
            case "sparqlendpoint" -> {
                var endpoint = source.getStringProperty(SPARQL_ENDPOINT);
                if (endpoint == null || endpoint.isBlank()) {
                    return Result.failure("Missing required property: " + SPARQL_ENDPOINT);
                }

                var query = source.getStringProperty(QUERY);
                if (query == null || query.isBlank()) {
                    return Result.failure("Missing required property: " + QUERY);
                }
            }
            default -> {
                return Result.failure("Invalid " + SERVICE_TYPE + serviceType);
            }
        }
        if (request.getId() == null) {
            return Result.failure("Missing request ID");
        }
        return Result.success();
    }

}
