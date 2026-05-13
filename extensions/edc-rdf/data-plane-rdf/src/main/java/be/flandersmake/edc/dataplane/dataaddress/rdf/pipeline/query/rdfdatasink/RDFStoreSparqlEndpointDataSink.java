package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.query.rdfdatasink;

import java.io.InputStream;
import java.net.http.HttpClient;
import java.util.concurrent.CompletableFuture;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdfconnection.RDFConnection;
import org.apache.jena.rdfconnection.RDFConnectionRemote;
import org.apache.jena.rdfconnection.RDFConnectionRemoteBuilder;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;
import java.net.Authenticator;
import java.net.PasswordAuthentication;

public class RDFStoreSparqlEndpointDataSink implements DataSink {

    private final String endpoint_url;
    private final String serialization;
    private final String credential_user;
    private final String credential_password;
    private final String graph;

    public RDFStoreSparqlEndpointDataSink(String endpoint_url, String serialization, String credential_user,
            String credential_password, String graph) {
        this.credential_user = credential_user;
        this.credential_password = credential_password;
        this.graph = graph;
        this.endpoint_url = endpoint_url;
        if(serialization.isBlank()|| serialization == null){
            this.serialization = "TTL"; //by default it assumes that the data transferred is a turtle file
        }else{
            this.serialization = serialization; //otherwise, for example, in the case of source files, this serialization is registered in the source and then at the sink this is transformed
        }
    }

    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataSource source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                var parts = source.openPartStream().getContent().toList();

                if (parts.isEmpty()) {
                    return StreamResult.error("No parts found in DataSource");
                }

                var part = parts.get(0);

                byte[] bytes;
                try (InputStream in = part.openStream()) {
                    bytes = in.readAllBytes();
                }

                Model combined = ModelFactory.createDefaultModel();
                combined.read(new java.io.ByteArrayInputStream(bytes), null, serialization);

                System.err.print("Model graph read");

                try (RDFConnection conn = buildRDFConnection()) {
                    if (graph.isBlank() || graph == null) {
                        conn.load(combined);
                    } else {
                        conn.load(graph, combined);
                    }
                    conn.commit();
                    conn.close();
                }

                System.err.println(" model graph uploaded to endpoint");

                return StreamResult.success(bytes);

            } catch (Exception e) {
                return StreamResult.error("Failed to process RDF: " + e.getMessage());
            }
        });
    }

    private RDFConnection buildRDFConnection() {

        RDFConnectionRemoteBuilder builder = RDFConnectionRemote.newBuilder()
                .destination(endpoint_url);

        if (credential_user != null && !credential_user.isBlank()) {

            Authenticator authenticator = new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(
                            credential_user,
                            credential_password.toCharArray());
                }
            };

            HttpClient client = HttpClient.newBuilder()
                    .authenticator(authenticator)
                    .build();

            builder.httpClient(client);
        }

        return builder.build();
    }

}
