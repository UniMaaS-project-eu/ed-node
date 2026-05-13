package be.flandersmake.edc.dataplane.dataaddress.rdf.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface SerializationConversion {

    public OutputStream convert(InputStream inStream, String sourceLang, String targetLang);

} 

