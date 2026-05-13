package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Base64;

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource.Part;

public class FileEndpointPart implements Part{

    private String file_endpoint;
    private String credential_user;
    private String credential_password;

    public FileEndpointPart(String file_endpoint, String credential_user, String credential_password){
        this.file_endpoint = file_endpoint;
        this.credential_user = credential_user;
        this.credential_password = credential_password;
    }

    @Override
    public String name() {
        return file_endpoint;
    }

    @Override
    public InputStream openStream() {
        URL url;
        try{
            url = new URI(file_endpoint).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", "EDC-HttpFileDataSource/1.0");
            // Add basic auth for credentials
            if (credential_user != null) {
                 String credentials = credential_user + ":" + credential_password;
                String encodedAuth = Base64.getEncoder().encodeToString(credentials.getBytes());
                conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }
            conn.setInstanceFollowRedirects(true);
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new IOException("Failed to download file. HTTP code: " + responseCode);
            }
        return conn.getInputStream();
        } catch (URISyntaxException | IOException e){
            e.printStackTrace();
            return null;
        }
    }

}