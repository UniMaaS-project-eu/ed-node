package be.flandersmake.edc.controlplane.dataaddress.rdf.spi;

import static be.flandersmake.edc.controlplane.dataaddress.rdf.util.RDFServiceNamespace.EDC_RDF_IRI;;
/**
 * Defines the schema of a DataAddress representing a RDF Sink, to be used as destination data address.
 * Uses the edc_rdf ontology in the Unimaass submodule
 * This schema is used to extract typed properties from a generic DataAddress when the type is "RDFSink".
 */
public interface RDFSinkDataAddressSchema {

    String RDF_DATA_SINK_TYPE = "RDFSink";

    String SINK_ENDPOINT_URL = EDC_RDF_IRI + "targetURL";

    String STORE_TYPE = EDC_RDF_IRI + "store";

    String SINK_GRAPH = EDC_RDF_IRI + "graph";

    String SINK_USER = EDC_RDF_IRI + "username";

    String SINK_PASSWORD = EDC_RDF_IRI + "password";

    String SERIALIZATION_SINK = EDC_RDF_IRI + "serialization";

    String FILE_NAME = EDC_RDF_IRI + "fileName";

}
