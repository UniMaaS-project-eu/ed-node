package be.flandersmake.edc.dataplane.dataaddress.rdf.pipeline.file;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import org.eclipse.edc.connector.dataplane.spi.pipeline.DataSource.Part;
import be.flandersmake.edc.dataplane.dataaddress.rdf.utils.SerializationConversion;
import be.flandersmake.edc.dataplane.dataaddress.rdf.utils.SerializationConversionImpl;

public class FilePart implements Part{

    SerializationConversion conversion;// = new SerializationConversionImpl();

    private Path path;
    private String serialization_source;
    private String serialization_target;

    public FilePart(Path path, String serialization_source, String serialization_target){
        this.path = path;
        System.err.print("Path Source:"+path);
        this.serialization_source = serialization_source;
        this.serialization_target = serialization_target;
        conversion = new SerializationConversionImpl();
    }

    @Override
    public String name() {
        return path.getFileName().toString();

    }

@Override
public InputStream openStream() {
    try {
        InputStream inputStream = new FileInputStream(path.toFile());

        if (serialization_source != null
                && !serialization_source.isBlank()
                && serialization_target != null
                && !serialization_target.isBlank()
                && !serialization_source.equalsIgnoreCase(serialization_target)) {

            OutputStream out = conversion.convert(
                    inputStream,
                    serialization_source,
                    serialization_target
            );

            inputStream.close(); // manually close original

            ByteArrayOutputStream baos = (ByteArrayOutputStream) out;
            return new ByteArrayInputStream(baos.toByteArray());
        }

        inputStream.close();
        return new FileInputStream(path.toFile());

    } catch (Exception e) {
        throw new RuntimeException(e);
    }
}
}
