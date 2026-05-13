package be.flandersmake.edc.dataplane.dataaddress.rdf.registry;

import be.flandersmake.edc.dataplane.dataaddress.rdf.services.RDFService;

//for endpoints the service will be similar to quote request - 
// with the difference that we will run a predefined query in the background
public interface RDFServiceRegistry {
    void register(RDFService service);
    RDFService getEndpointService(String endpoint, String query); // null if not found
}

