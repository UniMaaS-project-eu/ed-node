# ISSUERSERVICE - Deployment Steps

# 0 - Configuration

Configure `.env` file, define the host, and exposed ports.

## 1 - Create Private/Public Key, DID & Vault Config File

To deploy the `issuerservice`, you must first create the private and public keys for the DID document. This folder offers a script (`generateIssuerFiles-KeysDIDVault`) to generate test keys.

```sh
cd tests/issuerservice-vc;
./generateIssuerFiles-KeysDIDVault.sh <instanceName> <issuerServiceDIDHost>:<issuerServiceDIDPort>;
# ex: ./generateIssuerFiles-KeysDIDVault.sh issuerservice localhost:9878;
```
Where:

- <instanceName>: Instance name.
- <edNodeDIDHost:edNodeDIDPort>: host & port where DID related to the ED-Node can be resolved.

Running the script should create two new folders, `assets` and `vault`, with the following contents:

```
assets/
├── dids/
│   ├── issuerservice/
│   │   └── .well-known/
│   │       └── did.json
└── keysPrivPub/
        └── issuerservice/
            ├── issuerservice_private.pem
            └── issuerservice_public.pem
vault/
     └── issuerservice/
        └── vault-issuerservice-config.json
```

# 2 - Deploy

```sh
cd tests/issuerservice-vc/; 
docker-compose build; docker-compose up -d
```

# 3 - LOGS Monitor

```sh
sleep 4; docker logs -f issuerservice-vc
```

# 4 - Testing


## 4.1 - DID Resolution is working

```sh
curl http://localhost:9878/issuerservice/.well-known/did.json
```

## 4.2 - IssuerService API

### Check Health
```sh
curl http://localhost:8500/api/v1/health
```

### Info

```sh
curl http://localhost:8500/api/v1/info
```

### Endpoints

Obtain "verifiableCredential.rawVc"

```sh
curl -X POST http://localhost:8500/api/v1/sign-credential \
  -H "Content-Type: application/json" \
  -d '{
  "participantDid": "did:web:localhost%3A9876:connector1",
  "credential": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://w3id.org/security/suites/jws-2020/v1",
      "https://www.w3.org/ns/did/v1",
      {
        "unimaas-credentials": "https://w3id.org/unimaas/credentials/",
        "contractVersion": "unimaas-credentials:contractVersion",
        "level": "unimaas-credentials:level"
      }
    ],
    "id": "http://org.yourdataspace.com/credentials/2347",
    "type": [
      "VerifiableCredential",
      "DataProcessorCredential"
    ],
    "issuer": "did:web:localhost%3A9878:issuerservice",
    "issuanceDate": "2023-08-18T00:00:00Z",
    "credentialSubject": {
      "id": "did:web:localhost%3A9876:connector1",
      "contractVersion": "1.0.0",
      "level": "processing"
    }
  }
}'
```

Obtain full signed Verifiable Credential:

```sh
curl -X POST http://localhost:8500/api/v1/issue-credential \
  -H "Content-Type: application/json" \
  -d '{
  "participantDid": "did:web:localhost%3A9876:connector1",
  "holderDid": "did:web:localhost%3A9876:connector1",
  "credential": {
    "@context": [
      "https://www.w3.org/2018/credentials/v1",
      "https://w3id.org/security/suites/jws-2020/v1",
      "https://www.w3.org/ns/did/v1",
      {
        "unimaas-credentials": "https://w3id.org/unimaas/credentials/",
        "contractVersion": "unimaas-credentials:contractVersion",
        "level": "unimaas-credentials:level"
      }
    ],
    "id": "http://org.yourdataspace.com/credentials/2347",
    "type": [
      "VerifiableCredential",
      "DataProcessorCredential"
    ],
    "issuer": "did:web:localhost%3A9878:issuerservice",
    "issuanceDate": "2023-08-18T00:00:00Z",
    "credentialSubject": {
      "id": "did:web:localhost%3A9876:connector1",
      "contractVersion": "1.0.0",
      "level": "processing"
    }
  }
}'
```
