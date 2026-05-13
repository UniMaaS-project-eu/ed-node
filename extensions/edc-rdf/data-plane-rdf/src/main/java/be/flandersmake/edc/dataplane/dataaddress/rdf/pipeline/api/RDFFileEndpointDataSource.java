package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.api;

import java.io.InputStream;
import java.util.stream.Stream;

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;

public class RDFFileEndpointDataSource implements DataSource {

    private final String file_endpoint;
    private final String credential_user;
    private final String credential_password;
    private InputStream openInputStream;

    public RDFFileEndpointDataSource(String file_endpoint, String serialization, String credential_user, String credential_password){
        this.file_endpoint = file_endpoint;
        this.credential_user = credential_user;
        this.credential_password = credential_password;
    }

    @Override
    public void close() throws Exception {
        if(openInputStream != null){
            openInputStream.close();
        }
    }

    @Override
    public StreamResult<Stream<Part>> openPartStream() {
        try{
            FileEndpointPart fileEndpointPart = new FileEndpointPart(file_endpoint, credential_user, credential_password);
            openInputStream  = fileEndpointPart.openStream();
            return StreamResult.success(Stream.of(fileEndpointPart));
        }catch (Exception e){
            return StreamResult.error("File not found");
        }
    }

}
