package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.file;

import java.io.InputStream;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource;
import org.eclipse.edc.connector.dataplane.spi.pipeline.StreamResult;

public class RDFFileDataSource implements DataSource {

    private Path path;
    private String serialization_source;
    private InputStream openedStream;
    private String serialization_target;

    public RDFFileDataSource(String path, String serialization, String target_serialization) {
        this.path = Path.of(path).toAbsolutePath().normalize();;
        this.serialization_source = serialization;
        this.serialization_target = target_serialization;
    }

    @Override
    public void close() throws Exception {
        if (openedStream != null) {
            openedStream.close();
        }
    }

    @Override
    public StreamResult<Stream<Part>> openPartStream() {
        try {
                FilePart part = new FilePart(path, serialization_source, serialization_target);
                openedStream = part.openStream();
                return StreamResult.success(Stream.of(part));
        } catch (Exception e) {
            return StreamResult.error("File path not found");
        }
    }

}