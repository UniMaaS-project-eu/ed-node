package be.flandersmake.edc.dataplane.dataaddress.rdf.spi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.util.*;
import static be.flandersmake.edc.controlplane.dataaddress.rdf.spi.RDFServiceDataAddressSchema.*;
import static java.util.Collections.emptyMap;

@JsonTypeName(RDF_SERVICE_TYPE)
@JsonDeserialize(builder = RDFServiceDataAddress.Builder.class) 
public class RDFServiceDataAddress extends DataAddress {

    private RDFServiceDataAddress() {
        super();
        setType(RDF_SERVICE_TYPE);
    }

    @JsonIgnore
    public String getCredentialUser(){
        return getStringProperty(CREDENTIAL_USER);
    }

    @JsonIgnore
    public String getCredentialPassword(){
        return getStringProperty(CREDENTIAL_PASSWORD);
    }

    @JsonIgnore
    public String getServiceType(){
        return getStringProperty(SERVICE_TYPE);
    }

    @JsonIgnore
    public String getEncoding(){
        return getStringProperty(ENCODING);
    }

    @JsonIgnore
    public String getSerialization(){
        return getStringProperty(SERIALIZATION);
    }

    @JsonIgnore
    public String getSparqlEndpoint() {
        return getStringProperty(SPARQL_ENDPOINT);
    }

    @JsonIgnore
    public String getFileEndpoint() {
        return getStringProperty(FILE_ENDPOINT);
    }

    @JsonIgnore
    public String getQuery(){
        return getStringProperty(QUERY);
    }

    @JsonIgnore
    public String getFile(){
        return getStringProperty(FILE);
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class Builder extends DataAddress.Builder<RDFServiceDataAddress, Builder> {

        private Builder() {
            super(new RDFServiceDataAddress());
        }

        @JsonCreator
        public static Builder newInstance() {
            return new Builder();
        }

        public Builder SparqlEndpoint(String urlString) {
            property(SPARQL_ENDPOINT, Objects.requireNonNull(urlString));
            return this;
        }

        public Builder FileEndpoint(String urlString) {
            property(FILE_ENDPOINT, Objects.requireNonNull(urlString));
            return this;
        }

        public Builder Query(String query){
            property(QUERY, Objects.requireNonNull(query));
            return this;
        }

        public Builder File(String file){
            property(FILE, Objects.requireNonNull(file));
            return this;
        }

        public Builder CredentialUser(String credential){
            property(CREDENTIAL_USER, Objects.requireNonNull(credential));
            return this;
        }

        public Builder CredentialPassword(String credential){
            property(CREDENTIAL_PASSWORD, Objects.requireNonNull(credential));
            return this;
        }

        public Builder Serialization(String serialization){
            property(SERIALIZATION, Objects.requireNonNull(serialization));
            return this;
        }

        public Builder Encoding(String encoding){
            property(ENCODING, Objects.requireNonNull(encoding));
            return this;
        }

        public Builder ServiceType(String serviceType){
            property(SERVICE_TYPE, Objects.requireNonNull(serviceType));
            return this;
        }

        /**
         * Copy *all* props from another DataAddress (e.g. during a decorate() in your source factory).
         */
        public Builder copyFrom(DataAddress other) {
            Optional.ofNullable(other)
                    .map(DataAddress::getProperties)
                    .orElse(emptyMap())
                    .forEach(this::property);
            return this;
        }

        @Override
        public RDFServiceDataAddress build() {
            type(RDF_SERVICE_TYPE);
            return address;
        }
    }
}
