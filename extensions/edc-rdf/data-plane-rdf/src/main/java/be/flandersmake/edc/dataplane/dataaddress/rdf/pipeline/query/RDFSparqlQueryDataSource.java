package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.query;

import java.util.Base64;
import java.util.stream.Stream;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.sparql.engine.http.QueryExceptionHTTP;

public class RDFSparqlQueryDataSource implements DataSource{

    private final String endpoint;
    private final String query;
    //private final String serializationFormat;
    private final String credential_user;
    private final String credential_password;
    private final String sink_serialization;

    public RDFSparqlQueryDataSource(String endpoint, String query, String credential_user, String credential_password, String sink_serialization){
        this.query = query;
        this.endpoint = endpoint;
        //this.serializationFormat = serializationFormat;
        this.credential_user = credential_user;
        this.credential_password = credential_password;
        if(sink_serialization.isBlank() || sink_serialization == null){
            this.sink_serialization = "TTL"; //by default the data will transferred in TTL format, unless specified by the data transfer
        }else this.sink_serialization = sink_serialization;
    }

    @Override
    public void close() throws Exception {
        throw new UnsupportedOperationException("Unimplemented method 'close'");
    }

    @Override
    public StreamResult<Stream<Part>> openPartStream() {
        try{
            Query sparqlQuery = QueryFactory.create(query);
            //if(sparqlQuery.isConstructType()){
                System.err.println("Running a construct query");
                Model queryResults = runConstructQuery(sparqlQuery);
                System.err.println("Number of resulting triples:" + queryResults.size());
                RDFSparqlTriplesPart part = new RDFSparqlTriplesPart(queryResults, sink_serialization);//by default, serializes to TTL internally to do the data transfer
                System.err.println("Construct query successful");
                return StreamResult.success(Stream.of(part));
            //}//else { //Comment: Not used for now in Unimaass - this part will be moved in other data address to support SELECT queries 
                /*ResultSet queryResults = runSelectQuery(sparqlQuery);
                System.err.println("Number of query resulting rows:" + queryResults.getRowNumber());
                RDFSparqlQueryPart part = new RDFSparqlQueryPart(queryResults, serializationFormat);
                return StreamResult.success(Stream.of(part));*/
            //}
        }catch (Exception e){
            e.printStackTrace();
            return StreamResult.error("Issue on query execution");
        }
    }

    private QueryExecution buildQueryExecution(Query sparqlQuery) {
    // If credentials are provided, attach Basic Auth
    if (credential_user != null && !credential_user.isBlank()) {
        String auth = Base64.getEncoder()
                .encodeToString((credential_user + ":" + credential_password).getBytes());

        return QueryExecution.service(endpoint)
                .query(sparqlQuery)
                .httpHeader("Authorization", "Basic " + auth)
                .build();
    }
    return QueryExecution.service(endpoint)
            .query(sparqlQuery)
            .build();
    }

   public Model runConstructQuery(Query sparqlQuery) {
        try {
            QueryExecution qexec = buildQueryExecution(sparqlQuery);
            try (qexec) {
                return qexec.execConstruct();
            }
        } catch (QueryParseException e) {
            System.err.println("Invalid SPARQL query: " + e.getMessage());
        } catch (QueryExceptionHTTP e) {
            System.err.println("HTTP error: " + e.getMessage());
        }
        return ModelFactory.createDefaultModel(); //return empty model
    }

    /*public ResultSet runSelectQuery(Query sparqlQuery){
        try {
            QueryExecution qexec = buildQueryExecution(sparqlQuery);
            try (qexec) {
                return qexec.execSelect();
            }
        } catch (QueryParseException e) {
            throw new QueryParseException(e.getMessage(), e.getLine(), e.getColumn());
           // System.err.println("Invalid SPARQL query: " + e.getMessage());
        } catch (QueryExceptionHTTP e) {
            throw new QueryExceptionHTTP(e);
           // System.err.println("HTTP error: " + e.getMessage());
        }
        //return emptyResultSet();
    }*/

    //dummy method for creating empty result sets
    private ResultSet emptyResultSet() {
        Model emptyModel = ModelFactory.createDefaultModel();
        Query emptyQuery = QueryFactory.create("SELECT * WHERE {}");
        return ResultSetFactory.copyResults(
            ResultSetFactory.makeRewindable(
                    QueryExecutionFactory.create(emptyQuery, emptyModel).execSelect()
            )
        );
    }

        
}
