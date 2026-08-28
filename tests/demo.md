# Set up
- keycloak clients
  - did:web:edc-proxy-connector1%3A80:connector1
    - secret EqdQ8TRr4AbROpFrXDr3a4r8pEzQQjN7
  - did:web:edc-proxy-connector2%3A80:connector2
    - secret 5UBhXDOzRvQpu3CiUPXNTn2tXxEAqlje
  - did:web:edc-proxy-connectorcp%3A80:connectorcp
    - secret: 6QU85OYXvSIu2gqs4p9c548nnUWxL5tM  
- network
  - internal ports
    - credentilas: 7091
    - identity: 7092
    - management: 8081
    - dsp: 8082
    - catalog: 8084
    - public: 10011
  - issuerservice
    - ports 7000:80
  - connector1
    - ports 9876:80
  - connector2
    - ports 9877:80
  - connectorcp
    - ports 9879:80

# datasets

Connector1

# helpers

## Issuer service

```sh
cd tests/issuerservice-vc;

. .env

. ./generateIssuerFiles-KeysDIDVault.sh ${ISSUERSERVICEVC_INSTANCE_NAME} ${ISSUERSERVICEVC_HOST}:${ISSUERSERVICEVC_DID_PORT};

. ./deploy.sh $ISSUERSERVICEVC_INSTANCE_NAME 
```

## Connector

```sh
. .env-connector1
. .env-connector2
. .env-connectorcp

. ./generateEDNodeFiles-KeysDIDVault.sh ${ED_NODE_INSTANCE_NAME} ${BASE_HOST}:${NGINX_INTERNAL_PORT};

. ./deploy.sh ${ED_NODE_INSTANCE_NAME} 
. ./deploy.sh ${ED_NODE_INSTANCE_NAME} restart
. ./deploy.sh ${ED_NODE_INSTANCE_NAME} down-v
```


## Standalone federated catalog server

From central kg root

```sh
./deploy.sh up central-platform-postgres
./deploy.sh down central-platform-postgres
./deploy.sh up central-platform-federated-catalog | tee log/central-platform-federated-catalog-run1.log
./deploy.sh down central-platform-federated-catalog
```







