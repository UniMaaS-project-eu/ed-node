package be.flandersmake.edc.controlplane.dataaddress.rdf.spi;

import static be.flandersmake.edc.controlplane.dataaddress.rdf.util.RDFServiceNamespace.MSC_RDF_NAMESPACE;

// Contains the information on the properties at Asset level for Unimaass RDF Datasets
//Ontology defined on unimaass ontology submodule on folder dms-rdf-service under file msc-dms-rdf.ttl
public interface UnimaassDatasetSchema {

    /**
     * IRI or String indicating the location of this dataset (range: dcterm:Spatial)
     */
    String SPATIAL_REGION = MSC_RDF_NAMESPACE + "relatedSpatialRegion";

    /**
     * Date of the dataset (range: dcterm:temporal)
     */
    String TEMPORAL_REGION = MSC_RDF_NAMESPACE + "relatedTemporalRegion";

    /**
     * ID of the sc:Route
     */
    String ROUTE = MSC_RDF_NAMESPACE + "aboutRoute";

    /**
     * Name or ID of sc:Supplier
     */
    String SUPPLIER = MSC_RDF_NAMESPACE + "aboutSupplier";

    /**
     * Name or ID of sc:EndProduct
     */
    String END_PRODUCT = MSC_RDF_NAMESPACE + "aboutEndProduct";

    /**
     * boolean if it is relatedToResources
     */
    String RESOURCES_BOOLEAN = MSC_RDF_NAMESPACE + "relatedToResources";

    /**
     * boolean if it is related to Planning
     */
    String PLANNING_BOOLEAN = MSC_RDF_NAMESPACE + "relatedToPlanning";

}
