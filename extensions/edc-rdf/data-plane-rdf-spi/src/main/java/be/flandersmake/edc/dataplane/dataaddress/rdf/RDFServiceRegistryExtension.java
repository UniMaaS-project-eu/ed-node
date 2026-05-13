package be.flandersmake.edc.dataplane.dataaddress.rdf;

import org.eclipse.edc.runtime.metamodel.annotation.Provider;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import be.flandersmake.edc.dataplane.dataaddress.rdf.registry.RDFServiceRegistryImpl;
import be.flandersmake.edc.dataplane.dataaddress.rdf.registry.RDFServiceRegistry;

@Extension(RDFServiceRegistryExtension.NAME)
public class RDFServiceRegistryExtension implements ServiceExtension{
    public static final String NAME = "RDF Service Registry";

    private final RDFServiceRegistryImpl registry = new RDFServiceRegistryImpl();

    @Provider
    public RDFServiceRegistry registry(){
        return registry ; 
    }

}