# ED-NODE - ARCHITECTURE


![EclipseDataSpace-Node_v3](./img/EclipseDataSpace-Node_v3.png)

![TrustedDataExchange-Architecture_v3](./img/TrustedDataExchange-Architecture_v3.png)



# ED-NODE - Deployment Steps

## PREREQUISITES

1. A `Keycloak` instance must be running with a client assigned to the ED-NODE. See the `README.md` file in the `./test/keycloak` folder. This folder provides a deployment of a Keyrock instance (based on Docker Compose) for testing and a guide for creating a Keycloak Client. 

2. A `Issuer Service for Verifiable Credentials` (VCs) acquisition must be running. See the `README.md` file in the `./test/issuerservice-vc` folder. This folder provides of a development of an issuerservice instance for testing.

#  0 - Configuration

- Duplicate `.env-template` to `.env-<instanceName>`

```sh
cp .env-template .env-<instanceName>
#For instance: cp .env-template .env-connector1
```

Where `<instanceName>` is the Instance Name of the ED-Node (`ED_NODE_INSTANCE_NAME` parameter of `.env-<instanceName>` file)


## 1 - Create Private/Public Key, DID & Vault Config File


To deploy the `ed-node`, you must first create the private and public keys for the DID document. This folder offers a script (`generateKeysPrivPubVault`) to generate test keys. You can run this script by simply replacing <edNodeHost> with your IP and defining <instanceName>

```sh
cd test/issuerservice-vc;
./generateKeysPrivPubVault.sh <edNodeHost>:9876 <edNodeHost>:7191 <edNodeHost>:7192 <edNodeHost>:8192 <instanceName>;
# ex: ./generateKeysPrivPubVault.sh localhost:9876 localhost:7191 localhost:7192 localhost:8192 connector1;
```

Where:

- <hostDID:portDID>: host & port where DID related to the ED-Node can be resolved.
- <hostCredentialService:portCredentialService>: host & port exposed by ED-Node to where `/api/credentials/` endpoints are accessible (see `IH_HOST` and `IH_CREDENTIALS_PORT` parameters of `.env` file).
- <hostIdentityHub:portIdentityHub>: host & port exposed by ED-Node to where `/api/identity` endpoints are accessible (see `IH_HOST` and `IH_IDENTITY_PORT` parameters of `.env` file).
- <hostConnectorDSP:portConnectorDSP>: host & port exposed by ED-Node to where `/api/dsp` endpoints are accessible (see `CONNECTOR_HOST` and `CONNECTOR_CALLBACK_PORT` parameters of `.env` file).
- <instanceName>: Instance name (see `ED_NODE_INSTANCE_NAME` parameter of `.env` file).


Running the script should create two new folders, `assets` and `vault`, with the following contents:

```
deployment/assets/
├── dids/
│   ├── connector1/
│   │   └── .well-known/
│   │       └── did.json
└── keysPrivPub/
        └── connector1/
            ├── connector1_private.pem
            └── connector1_public.pem
deployment/vault/
     └── connector1/
        └── vault-connector1-config.json

```

# 2 - Deploy

Deploy ED-Node with:

```sh
./deploy.sh <instanceName> or ./deploy.sh <instanceName> recreate
#For instance: ./deploy.sh connector1
```

**NOTE:** For simulating `docker-compose down -v` executes:
```sh
./deploy.sh <instanceName> removeall
#For instance: ./deploy.sh connector1 removeall
```

**NOTE:** For simulating `docker-compose down -v; docker-compose build; docker-compose up -d` executes:
```sh
./deploy.sh <instanceName> fromzero
#For instance: ./deploy.sh connector1 fromzero
```

## 3 - Validation ED-NODE

Deployed container:

- edc-did-server-<instanceName> --> RUNNING
- edc-postgres-<instanceName> --> RUNNING
- edc-vault-<instanceName> --> RUNNING
- edc-vault-init-<instanceName> --> Exited (0)
- edc-ih-<instanceName> --> RUNNING
- edc-ih-seed-<instanceName> --> Exited (0)
- edc-connector-<instanceName> --> RUNNING

Accessing to the IdentityHub LOGS:
```sh
docker-compose --env-file .env-<instanceName> -f docker-compose-<instanceName>.yml logs -f ih-connector
#docker-compose --env-file .env-connector1 -f docker-compose-connector1.yml logs -f ih-connector
```

Next information must be obtained (no errors and 2 credentials stored):
```
...
ih-connector1               | INFO 2025-09-19T12:58:02.69745306 64 service extensions started
ih-connector1               | INFO 2025-09-19T12:58:02.698246036 Runtime bc3e1d86-b6d4-4d50-812f-62e68ec09851 ready
ih-connector1               | WARNING 2025-09-19T12:58:05.829643521 [DidDocumentService] Updating DID documents after activating a KeyPair failed: Cannot publish DID 'did:web:*******%3A9876:connector1' for participant 'did:web:*******%3A9876:connector1' because the ParticipantContext state is not 'ACTIVATED', but 'CREATED'.
ih-connector1               | DEBUG 2025-09-19T12:58:06.706610404 [CredentialWatchdog] checking 2 credentials
```

Accessing to the Conector LOGS:
```sh
docker-compose --env-file .env-<instanceName> -f docker-compose-<instanceName>.yml logs -f connector
#docker-compose --env-file .env-connector1 -f docker-compose-connector1.yml logs -f connector
```
Next information must be obtained:
```
...
edc-connector-connector1    | INFO 2025-09-19T12:58:03.657159575 125 service extensions started
edc-connector-connector1    | INFO 2025-09-19T12:58:03.658233354 Runtime 8ffbda8f-ef59-49be-9438-6710f86bca75 ready
edc-connector-connector1    | DEBUG 2025-09-19T12:58:04.646835642 [DataPlaneSelectorManagerImpl] DataPlaneInstance 8ffbda8f-ef59-49be-9438-6710f86bca75 is now in state AVAILABLE
```

## 4 - Testing with other ED-Nodes

## 4.1 - DID Resolution is working

```sh
curl http://localhost:9876/connector1/.well-known/did.json
```

## 4.2 - Deploying a new ED-NODE (same machine)

- Create the new Keycloak client asigned to the new ED-Node.

- Duplicate `.env-template` to `.env-<instanceName>`

```sh
cp .env-template .env-<instanceName>
#For instance: cp .env-template .env-connector2
```

- Configure `.env-<instanceName>` file and change the next parameters:

    - `ED_NODE_INSTANCE_NAME:connector2` (new Instance Name).
    - `ED_NODE_BASE_HOST`: same machine of ED-Node (if the same machine)
    - `ED_NODE_DID_PORT=9877` (for new DID resolver services)

    - `Identity Provider (VCs): ISSUERSERVICE` and `Identity Provider (Autentication): KeyCloack` must be the same as configured for the first ED-Node intance (connetor1), **with the exception of**: `UNIMAAS_OIDC_IDP_CLIENT_ID` and `UNIMAAS_OIDC_IDP_CLIENT_SECRET` parameters that must include the information from the last Keycloak Client created.

    - To avoid used ports change the exposed port of `IdentityHub` and `Connector`:
        - `IH_API_PORT=7290`
        - `IH_CREDENTIALS_PORT=7291`
        - `IH_IDENTITY_PORT=7292`
        - `IH_DID_PORT=7293`
        - `IH_VERSION_PORT=7294`
        - `IH_STS_PORT=7295`
        - `CONNECTOR_API_PORT=8290`
        - `CONNECTOR_API_MANAGEMENT_PORT=8291`
        - `CONNECTOR_CALLBACK_PORT=8292`
        - `CONNECTOR_API_CONTROL_PORT=8293`
        - `CONNECTOR_CATALOG_PORT=8284`
        - `CONNECTOR_VERSION_PORT=8285`
        - `CONNECTOR_PUBLIC_API_PORT=12002`
        - `CONNECTOR_DATAPLANE_PUBLIC_PORT=9291`
        - `CONNECTOR_DATAPLANE_SIGNALING_PORT=9292`

- Create Private/Public Key, DID & Vault Config File.

For instance:
```sh
./generateKeysPrivPubVault.sh 192.168.1.110:9877 192.168.1.110:7291 192.168.1.110:7292 192.168.1.110:8292 connector2;
```

**NOTE:** Considering both ED-Nodes will be in same machine you will have to change exposed ports and asing a new `InstanceName` (`connector2`), else you need to change the host too.

- Deploy second ED-Node with:

```sh
./deploy.sh <instanceName>
#For instance: ./deploy.sh connector2
```

For comunication testing `Postman Collection` is available in `test/postman` folder, in this sense replace {{conector2IP}} with your IP or define that enviroment variable. This pattern is included in `CatalogRequest`, `ContractRequest` and `TransferRequestDto`.