package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.query;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.jena.query.ResultSet;
import org.apache.jena.riot.ResultSetMgr;
import org.apache.jena.riot.resultset.ResultSetLang;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource.Part;

public class RDFSparqlQueryPart implements Part{

    private ResultSet queryResults;
    private String serializationFormat;

    public RDFSparqlQueryPart(ResultSet queryResults, String serializationFormat){
        this.queryResults = queryResults;
        this.serializationFormat = serializationFormat;
    }

    @Override
    public String name() {
        return queryResults.getResultVars().toString();
    }

    //serialization of the sparql Select queries
    @Override
    public InputStream openStream() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if(serializationFormat.endsWith("CSV")){
            ResultSetMgr.write(out, queryResults, ResultSetLang.RS_CSV);
        }else if(serializationFormat.endsWith("XML")){
            ResultSetMgr.write(out, queryResults, ResultSetLang.RS_XML);
        }else{
            ResultSetMgr.write(out, queryResults, ResultSetLang.RS_JSON);
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

}
