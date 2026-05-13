package be.flandersmake.edc.controlplane.dataaddress.rdf.spi;

import static be.flandersmake.edc.controlplane.dataaddress.rdf.util.RDFServiceNamespace.EDC_RDF_PREFIX;
import static be.flandersmake.edc.controlplane.dataaddress.rdf.util.RDFServiceNamespace.EDC_RDF_IRI;

/**
 * Defines the schema of a DataAddress representing a rdf endpoint.
 * Uses the edc_rdf ontology in the Unimaass submodule
 * This schema is used to extract typed properties from a generic DataAddress
 * when the type is "RDFService".
 */
public interface RDFServiceDataAddressSchema {

    String RDF_SERVICE_TYPE = "RDFService";

    String SERVICE_TYPE = EDC_RDF_IRI + "serviceType";

    String QUERY = EDC_RDF_IRI + "sparqlQuery";

    String SPARQL_ENDPOINT = EDC_RDF_IRI + "sparqlEndpoint";

    String FILE_ENDPOINT = EDC_RDF_IRI + "datasetURL";

    String FILE = EDC_RDF_IRI + "filePath";

    String CREDENTIAL_USER = EDC_RDF_IRI + "username";

    String CREDENTIAL_PASSWORD = EDC_RDF_IRI + "password";

    String ENCODING = EDC_RDF_IRI + "encoding";

    String SERIALIZATION = EDC_RDF_IRI + "serialization";

}
