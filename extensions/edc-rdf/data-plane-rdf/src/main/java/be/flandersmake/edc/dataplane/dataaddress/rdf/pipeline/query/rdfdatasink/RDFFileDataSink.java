package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.query.rdfdatasink;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSink;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;

public class RDFFileDataSink implements DataSink {

    private final String endpoint_file;
    private final String serialization_source;
    private final String processId;
    private final String serialization_target;
    private final String file_name;

    public RDFFileDataSink(String endpoint_file, String processId, String serialization_source, String serialization_target, String file_name) {
        this.endpoint_file = endpoint_file;
        this.processId = processId;
        this.file_name = file_name;
        if(serialization_source.isBlank() || serialization_source == null){
            this.serialization_source = "TTL";
        }else{
            this.serialization_source = serialization_source;
        }
        if(serialization_target.isBlank() || serialization_target == null){
            this.serialization_target = this.serialization_source;
        }else{
            this.serialization_target = serialization_target;
        }     
    }

    @Override
    public CompletableFuture<StreamResult<Object>> transfer(DataSource source) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String fileName;
                if(file_name == null || file_name.isBlank()){
                    fileName = processId + "." + getFileExtension(serialization_target);
                }else{
                    fileName = file_name + "." + getFileExtension(serialization_target);
                }
                
                var partStream = source.openPartStream();

                var parts = partStream.getContent().toList();
                if (parts.isEmpty()) {
                    throw new RuntimeException("No parts found in Datasource");
                    //return StreamResult.error("No parts found in DataSource");
                }

                var part = parts.get(0);

                try (InputStream in = part.openStream()) {
                    Path dirPath = Paths.get(endpoint_file).toAbsolutePath().normalize();
                    Files.createDirectories(dirPath);
                    Path outputPath = dirPath.resolve(fileName);    
                    try(var out = Files.newOutputStream(outputPath)){
                            in.transferTo(out);//cases in which the serialization format is not explicit in one of the sides, e.g. source for sparqlendpoints then this transformation is handled by the sparql endpoint in itself (RDFServiceDataSourceFactory class) 
                            return StreamResult.success(outputPath.toString());
                    }
                }
            } catch (Exception e) {
                //throw new RuntimeException("Failed to write RDF file: " + e.getMessage(), e);
                return StreamResult.error("Failed to write RDF file: " + e.getMessage());
            }
        });
    }

    private String getFileExtension(String lang) {
    if (lang == null) return "dat";
    switch (lang.toUpperCase()) {
        case "TTL":
        case "TURTLE":
            return "ttl";
        case "RDF/XML":
            return "xml";
        case "JSON-LD":
            return "jsonld";
        default:
            return "ttl"; // fallback
    }
}



}
