package be.flandersmake.edc.controlplane.dataaddress.rdf.validator;

import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.validator.spi.ValidationResult;
import org.eclipse.edc.validator.spi.Validator;
import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFSinkDataAddressSchema.*;
import static org.eclipse.edc.validator.spi.Violation.violation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;


public class RDFSinkDataAddressValidator implements Validator<DataAddress>{

    @Override
    public ValidationResult validate(DataAddress input) {

        String store_type = input.getStringProperty(STORE_TYPE);
        if(store_type.isBlank() || store_type == null){
            var v = violation(
                "DataAddress of type %s must indicate the store type Sparql or File defined".formatted(RDF_DATA_SINK_TYPE),
                 STORE_TYPE,
            store_type
            );
            return ValidationResult.failure(v);
        }else if(!store_type.equalsIgnoreCase("sparql") && !store_type.equalsIgnoreCase("file") ){//&& !store_type.equalsIgnoreCase("api")){
            var v = violation(
                "DataAddress of type %s must indicate the store type Sparql or File defined".formatted(RDF_DATA_SINK_TYPE),
                 STORE_TYPE,
            store_type
            );
            return ValidationResult.failure(v);
        }

        String endpoint_url = input.getStringProperty(SINK_ENDPOINT_URL);
        if((endpoint_url == null|| endpoint_url.isBlank())){
            var v = violation(
                "DataAddress of type %s must indicate the endpoint url defined".formatted(RDF_DATA_SINK_TYPE),
                 SINK_ENDPOINT_URL,
            endpoint_url
            );
            return ValidationResult.failure(v);
        }else if(store_type.equalsIgnoreCase("file")){
            try{
                Path path = Paths.get(endpoint_url);
                if(!Files.isDirectory(path)){
                var v = violation(
                    "DataAddress of type %s must indicate valid path defined".formatted(RDF_DATA_SINK_TYPE),
                    SINK_ENDPOINT_URL,
                    endpoint_url);
                return ValidationResult.failure(v);
                }

            }catch (Exception e){
                var v = violation(
                    "DataAddress of type %s must indicate valid path defined".formatted(SINK_ENDPOINT_URL),
                    SINK_ENDPOINT_URL,
                    endpoint_url);
                return ValidationResult.failure(v);
                } 
            }
        return ValidationResult.success();

    }

}
