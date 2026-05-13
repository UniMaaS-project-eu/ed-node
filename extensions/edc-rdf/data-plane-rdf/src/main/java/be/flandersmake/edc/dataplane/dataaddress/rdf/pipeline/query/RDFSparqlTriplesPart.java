package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.query;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.jena.rdf.model.Model;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource.Part;

public class RDFSparqlTriplesPart implements Part{

    private Model model;
    private String serializationFormat;

    public RDFSparqlTriplesPart(Model model, String serializationFormat){
        this.model = model;
        this.serializationFormat = serializationFormat;
    }

    @Override
    public String name() {
        return model.toString();
    }

    @Override
    public InputStream openStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        System.err.println("Ouptut serialization format" + serializationFormat);
        if(serializationFormat.endsWith("TTL") || serializationFormat.endsWith("TURTLE")){
            model.write(out, "TTL");
        }else if(serializationFormat.endsWith("XML")){
            model.write(out, "RDF/XML");
        }else{
           model.write(out, "JSON-LD");
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

}
