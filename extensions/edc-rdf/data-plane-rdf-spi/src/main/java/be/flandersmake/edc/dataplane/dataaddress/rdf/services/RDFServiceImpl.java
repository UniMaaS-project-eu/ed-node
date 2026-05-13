package be.flandersmake.edc.dataplane.dataaddress.rdf.services;

import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFServiceDataAddressSchema.QUERY;
import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFServiceDataAddressSchema.SPARQL_ENDPOINT;

import java.sql.ResultSet;

import javax.management.Query;

import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;

import be.flandersmake.edc.dataplane.dataaddress.rdf.model.QueryResponse;
import be.flandersmake.edc.dataplane.dataaddress.rdf.spi.RDFServiceDataAddress;

public class RDFServiceImpl implements RDFService{

    @Override
    public QueryResponse runQuery(DataFlowStartMessage request, RDFServiceDataAddress address) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'runQuery'");
    }

    @Override
    public String getEndpointDefinition() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getEndpointDefinition'");
    }

    @Override
    public String getQuery() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getQuery'");
    }

   /*  //running queries in the sparql endpoint - using Apache Jena services for this
    @Override
    public QueryResponse runQuery(DataFlowStartMessage request, RDFServiceDataAddress address) {
        String endpoint = request.getSourceDataAddress().getStringProperty(SPARQL_ENDPOINT);
        String query = request.getSourceDataAddress().getStringProperty(QUERY);


try {
            // Parse the query
            Query query = QueryFactory.create(sparqlQuery);

            // Create a QueryExecution object for the remote endpoint
            try (QueryExecution qexec = QueryExecutionFactory.sparqlService(endpointUrl, query)) {
                // Optional: Set timeout (ms)
                qexec.setTimeout(10_000, 30_000);

                // Execute SELECT queries
                if (query.isSelectType()) {
                    ResultSet results = qexec.execSelect();

                    if (!results.hasNext()) {
                        System.out.println("No results found.");
                        return;
                    }

                    // Print results in table format
                    ResultSetFormatter.out(System.out, results, query);
                }
                // Handle ASK queries
                else if (query.isAskType()) {
                    boolean exists = qexec.execAsk();
                    System.out.println("ASK result: " + exists);
                }
                // Handle CONSTRUCT/DESCRIBE queries
                else if (query.isConstructType() || query.isDescribeType()) {
                    Model model = query.isConstructType() ? qexec.execConstruct() : qexec.execDescribe();
                    model.write(System.out, "TTL"); // Output in Turtle format
                }
                else {
                    System.err.println("Unsupported query type.");
                }
            }
        }
        catch (QueryParseException e) {
            System.err.println("Invalid SPARQL query: " + e.getMessage());
        }
        catch (QueryExceptionHTTP e) {
            System.err.println("HTTP error: " + e.getMessage());
        }
        catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }

    }*/

}
