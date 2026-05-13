package be.flandersmake.edc.controlplane.dataaddress.rdf.validator;

import org.eclipse.edc.spi.system.SystemExtension;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;

import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFServiceDataAddressSchema.*;
import static org.eclipse.edc.validator.spi.Violation.violation;

public class RDFServiceDataAddressValidator implements Validator<DataAddress> {

    /**
     * Validates the given DataAddress if its type is "RDFService".
     * Ensures required fields are present and correctly formatted.
     * 
     * Currently this class is mainly checking if the fields are not empty but not
     * checking the format of the content
     * 
     * @param address The DataAddress to validate
     * @return a successful or failed ValidationResult
     */
    @Override
    public ValidationResult validate(DataAddress address) {

        // Validate required: it should be either of type file or endpoint
        var serviceType = address.getStringProperty(SERVICE_TYPE);
        if (serviceType == null || serviceType.isBlank()) {
            var v = violation(
                    "DataAddress of type %s must indicate the type of RDFDataset sparqlEndpoint,file or httpEndpoint"
                            .formatted(RDF_SERVICE_TYPE),
                    SERVICE_TYPE,
                    serviceType);
            return ValidationResult.failure(v);

        }

        if (serviceType.equalsIgnoreCase("file") || serviceType.equalsIgnoreCase("httpEndpoint")) {

            if (serviceType.equals("file")) {
                var file = address.getStringProperty(FILE);
                if (file == null || file.isBlank()) {
                    var v = violation(
                            "DataAddress of type %s must contain a non-blank file url".formatted(RDF_SERVICE_TYPE),
                            FILE,
                            file);
                    return ValidationResult.failure(v);
                }
            } else {
                var file_endpoint = address.getStringProperty(FILE_ENDPOINT);
                if (file_endpoint == null || file_endpoint.isBlank()) {
                    var v = violation(
                            "DataAddress of type %s must contain a non-blank file url".formatted(RDF_SERVICE_TYPE),
                            FILE_ENDPOINT,
                            file_endpoint);
                    return ValidationResult.failure(v);
                }
            }

            var encoding = address.getStringProperty(ENCODING);
            if (encoding == null || encoding.isBlank()) {
                var v = violation(
                        "DataAddress of type %s must contain a non-blank file encoding format"
                                .formatted(RDF_SERVICE_TYPE),
                        ENCODING,
                        encoding);
                return ValidationResult.failure(v);
            }

            var serialization = address.getStringProperty(SERIALIZATION);
            if (serialization == null || serialization.isBlank()) {
                var v = violation(
                        "DataAddress of type %s must contain a non-blank file serialization format"
                                .formatted(RDF_SERVICE_TYPE),
                        SERIALIZATION,
                        serialization);
                return ValidationResult.failure(v);
            }

        } else if (serviceType.equalsIgnoreCase("sparqlEndpoint")) {
            var endpoint = address.getStringProperty(SPARQL_ENDPOINT);

            if ((endpoint == null || endpoint.isBlank())) {
                var v = violation(
                        "DataAddress of type %s must contain a non-blank endpoint URL ".formatted(RDF_SERVICE_TYPE),
                        SPARQL_ENDPOINT,
                        endpoint);
                return ValidationResult.failure(v);
            } // for now checking only if it is blank or not

            var query = address.getStringProperty(QUERY);
            if ((query == null || query.isBlank())) {
                var v = violation(
                        "DataAddress of type %s must contain a non-blank sparql query definition "
                                .formatted(RDF_SERVICE_TYPE),
                        QUERY,
                        query);
                return ValidationResult.failure(v);
            }

            var credential_user = address.getStringProperty(CREDENTIAL_USER);
            if ((credential_user.isBlank())) {
                var v = violation(
                        "DataAddress of type %s must contain a non-blank credential username for accessing the endpoint "
                                .formatted(RDF_SERVICE_TYPE),
                        CREDENTIAL_USER,
                        credential_user);
                return ValidationResult.failure(v);
            }

            var credential_password = address.getStringProperty(CREDENTIAL_PASSWORD);
            if ((credential_password.isBlank())) {
                var v = violation(
                        "DataAddress of type %s must contain a non-blank credential password for accessing the endpoint "
                                .formatted(RDF_SERVICE_TYPE),
                        CREDENTIAL_USER,
                        credential_password);
                return ValidationResult.failure(v);
            }

        } else {
            var v = violation(
                    "DataAddress of type %s must indicate the type of RDFDataset endpoint or file"
                            .formatted(RDF_SERVICE_TYPE),
                    SERVICE_TYPE,
                    serviceType);
            return ValidationResult.failure(v);
        }
        

        return ValidationResult.success();
    }

}
