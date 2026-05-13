package be.flandersmake.edc.dataplane.dataaddress.rdf.registry;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import be.flandersmake.edc.dataplane.dataaddress.rdf.services.RDFService;
import be.flandersmake.edc.dataplane.dataaddress.rdf.utils.Pair;

public class RDFServiceRegistryImpl implements RDFServiceRegistry{
    private final Map<Pair<String, String>, RDFService> RDFServices = new ConcurrentHashMap<>();

    @Override
    public void register(RDFService service) {
        RDFServices.put(new Pair<String,String>(service.getEndpointDefinition(), service.getQuery()), service);
    }

    @Override
    public RDFService getEndpointService(String endpoint, String query) {
        return RDFServices.get(new Pair<String, String>(endpoint, query));
    }

}
