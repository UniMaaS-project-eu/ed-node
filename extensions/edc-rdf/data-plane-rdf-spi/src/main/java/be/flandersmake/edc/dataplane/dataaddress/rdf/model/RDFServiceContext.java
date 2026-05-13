package be.flandersmake.edc.dataplane.dataaddress.rdf.model;

public class RDFServiceContext {

    private String endpoint; 
    private String query; 
    private String serializationFormat;
    private String credentials;

    public RDFServiceContext(String endpoint, String query, String serializationFormat, String credentials) {
        this.endpoint = endpoint;
        this.query = query;
        this.serializationFormat = serializationFormat;
        this.credentials = credentials;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public String getQuery(){
        return query;
    }

    public String getSerializationFormat(){
        return serializationFormat;
    }

    public String getCredentials(){
        return credentials;
    }

}
