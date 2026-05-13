package be.flandersmake.edc.dataplane.dataaddress.rdf.services;

import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;

import be.flandersmake.edc.dataplane.dataaddress.rdf.model.QueryResponse;
import be.flandersmake.edc.dataplane.dataaddress.rdf.spi.RDFServiceDataAddress;

public interface RDFService {

    String getEndpointDefinition(); //endpoint registered within this asset

    String getQuery(); //query defined within this asset

  //  String getCredentials();//credentials for accessing endpoint

   // String getSerializationFormat(); //serialization format of the query response

    QueryResponse runQuery ( DataFlowStartMessage request, RDFServiceDataAddress address);
    
}

