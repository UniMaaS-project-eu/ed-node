package be.flandersmake.edc.dataplane.dataaddress.rdf.utils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;

public class SerializationConversionImpl implements SerializationConversion {

    @Override
        public OutputStream convert(InputStream inStream, String sourceLang, String targetLang) {

        Lang src = RDFLanguages.nameToLang(sourceLang.toUpperCase());
        Lang tgt = RDFLanguages.nameToLang(targetLang.toUpperCase());
        if (src == null) {
            throw new IllegalArgumentException("Unsupported source serialization: " + sourceLang);
        }
        if (tgt == null) {
            throw new IllegalArgumentException("Unsupported target serialization: " + targetLang);
        }
        // Create an empty Jena model
        System.err.println("Converting from " + src.toString() + " to " + tgt.toString());    

        Model model = ModelFactory.createDefaultModel();
        // Read RDF from input stream
        RDFDataMgr.read(model, inStream, src);//reading here has a bug
        // Write RDF to output stream in target format
        OutputStream out = new ByteArrayOutputStream();
        RDFDataMgr.write(out, model, tgt);
        return out;
    }



}
