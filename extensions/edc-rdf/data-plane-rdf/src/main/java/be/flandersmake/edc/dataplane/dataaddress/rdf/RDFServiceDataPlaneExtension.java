package be.flandersmake.edc.dataplane.dataaddress.rdf;

//import org.eclipse.edc.connector.dataplane.spi.manager.DataPlaneManager;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.runtime.metamodel.annotation.Extension;
import org.eclipse.edc.runtime.metamodel.annotation.Inject;
import org.eclipse.edc.spi.system.ServiceExtension;
import org.eclipse.edc.spi.system.ServiceExtensionContext;
import be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.RDFServiceDataSourceFactory;
import be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.query.rdfdatasink.RDFEndpointDataSinkFactory;

@Extension(RDFServiceDataPlaneExtension.NAME)
public class RDFServiceDataPlaneExtension implements ServiceExtension {

    public static final String NAME = "Data Plane RDF";

    @Inject
    private PipelineService pipeline;

    @Override
    public void initialize(ServiceExtensionContext context) {

        //PipelineService pipeline = context.getService(PipelineService.class);

        // Source for RDFService
        //Note: current implementation uses a single DataSourceFactory for all types 
        pipeline.registerFactory(new RDFServiceDataSourceFactory());

        // Sinks - Sink for inserting RDF into an endpoint
        pipeline.registerFactory(new RDFEndpointDataSinkFactory());

    }
}
