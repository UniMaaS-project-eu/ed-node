#!/bin/bash

# Script to generate DIDs and keys for Eclipse EDC connectors

set -e

# Verify that arguments have been provided
if [ $# -lt 2 ]; then
    echo "Use (Proxy): $0 <instanceName> <hostDID:portDID>"
    echo "Example (Proxy): $0 connector1 http://localhost:9876"
    echo "Use: $0 <instanceName> <hostDID:portDID> <hostCredServ:portCredServ> <identityHost:identityPort> <dspHost:dspPort>"
    echo "Example: $0 connector1 http://localhost:9876 http://localhost:7191 http://localhost:7192 http://localhost:8192"
    exit 1
fi

# Verify that at least 2 arguments have been provided
if [ $# -lt 2 ]; then
    echo "Use: $0 <instanceName> <hostDID:portDID> [<hostCredServ:portCredServ> <identityHost:identityPort> <dspHost:dspPort>]"
    exit 1
fi

PARTICIPANT=$1
HOST_PORT=$2

if [ $# -eq 2 ]; then
    HOST_PORT_CS=$2
    HOST_PORT_IH=$2
    HOST_PORT_DSP=$2
elif [ $# -eq 5 ]; then
    HOST_PORT_CS=$3
    HOST_PORT_IH=$4
    HOST_PORT_DSP=$5
else
    echo "Error: Provide either 2 or 5 parameters."
    exit 1
fi

HOST_PORT_PROTOCOL="http"
# Verify if HOST_PORT contains "://"
if [[ "$HOST_PORT" =~ ^https?:// ]]; then
  # If contains "://"
  HOST_PORT_PROTOCOL="${HOST_PORT%://*}"
  HOST_PORT="${HOST_PORT##*://}"
fi

HOST_PORT_CS_PROTOCOL="http"
# Verify if HOST_PORT_CS contains "://"
if [[ "$HOST_PORT_CS" =~ ^https?:// ]]; then
  # If contains "://"
  HOST_PORT_CS_PROTOCOL="${HOST_PORT_CS%://*}"
  HOST_PORT_CS="${HOST_PORT_CS##*://}"
fi

HOST_PORT_IH_PROTOCOL="http"
# Verify if HOST_PORT_IH contains "://"
if [[ "$HOST_PORT_IH" =~ ^https?:// ]]; then
  # If contains "://"
  HOST_PORT_IH_PROTOCOL="${HOST_PORT_IH%://*}"
  HOST_PORT_IH="${HOST_PORT_IH##*://}"
fi

HOST_PORT_DSP_PROTOCOL="http"
# Verify if HOST_PORT_DSP contains "://"
if [[ "$HOST_PORT_DSP" =~ ^https?:// ]]; then
  # If contains "://"
  HOST_PORT_DSP_PROTOCOL="${HOST_PORT_DSP%://*}"
  HOST_PORT_DSP="${HOST_PORT_DSP##*://}"
fi

# URL-encode of host:port for DID
DID_HOST=$(echo "$HOST_PORT" | sed 's/:/%3A/g')
DID_ID="did:web:${DID_HOST}:${PARTICIPANT}"

# Create base folder to DIDs y keys
ASSETS_DIR="deployment/assets"
VAULT_DIR="deployment/vault"

DIDS_DIR="$ASSETS_DIR/dids"
KEYS_DIR="$ASSETS_DIR/keysPrivPub"
KEYS_PARTICIPANT_DIR="$KEYS_DIR/$PARTICIPANT"

mkdir -p "$ASSETS_DIR"
mkdir -p "$DIDS_DIR"
mkdir -p "$KEYS_DIR"
mkdir -p "$KEYS_PARTICIPANT_DIR"

# Function to convert Ed25519 PEM public key to base64url (for x in JWK)
pem_to_base64url() {
    local pem_file=$1
    # Extract the public key in DER format, skip the ASN.1 header and convert to base64url
    openssl pkey -pubin -in "$pem_file" -outform DER | tail -c 32 | base64 | tr '+/' '-_' | tr -d '='
}

# Function to generate DID and keys for a connector
generate_participant_did() {

    local private_key_file="$KEYS_PARTICIPANT_DIR/${PARTICIPANT}_private.pem"
    local public_key_file="$KEYS_PARTICIPANT_DIR/${PARTICIPANT}_public.pem"
    local did_dir="$DIDS_DIR/$PARTICIPANT/.well-known"
    local did_file="$did_dir/did.json"
    
    echo "Creating DID document and keys for: $PARTICIPANT"
    
    # Create DID folder
    mkdir -p "$did_dir"
    
    echo "  Creating keys Ed25519..."
    openssl genpkey -algorithm ed25519 -out "$private_key_file"
    openssl pkey -in "$private_key_file" -pubout -out "$public_key_file"
    
    echo "  Extract public key for JWK..."
    X_VALUE=$(pem_to_base64url "$public_key_file")
    
    echo "  Creating DID document: $DID_ID"
    cat > "$did_file" << EOF
{
    "service": [
        {
            "id": "${DID_ID}#dsp-api",
            "type": "DataspaceConnector",
            "serviceEndpoint": "${HOST_PORT_DSP_PROTOCOL}://${HOST_PORT_DSP}/api/dsp"
        },
        {
            "id": "${DID_ID}#credential-service",
            "type": "CredentialService",
            "serviceEndpoint": "${HOST_PORT_CS_PROTOCOL}://${HOST_PORT_CS}/api/credentials/v1/participants/$(echo -n "$DID_ID" | base64 -w 0)"
        },
        {
            "id": "${DID_ID}#identity-hub",
            "type": "IdentityHub",
            "serviceEndpoint": "${HOST_PORT_IH_PROTOCOL}://${HOST_PORT_IH}/api/identity"
        }
    ],
    "verificationMethod": [
        {
            "id": "${DID_ID}#key-1",
            "type": "JsonWebKey2020",
            "controller": "${DID_ID}",
            "publicKeyMultibase": null,
            "publicKeyJwk": {
                "kty": "OKP",
                "crv": "Ed25519",
                "x": "${X_VALUE}"
            }
        }
    ],
    "authentication": ["key-1"],
    "id": "${DID_ID}",
    "@context": [
        "https://www.w3.org/ns/did/v1",
        {
            "@base": "${DID_ID}"
        }
    ]
}
EOF
    
    echo "  ✓ DID create: $did_file"
    echo "  ✓ Private key: $private_key_file"
    echo "  ✓ Public key: $public_key_file"
    echo ""
}

echo "Creating DIDs to host: $HOST_PORT"
echo "Participant: $PARTICIPANT"
echo ""

generate_participant_did

generate_vault_config() {

    VAULT_PARTICIPANT_DIR="$VAULT_DIR/$PARTICIPANT"
    mkdir -p "$VAULT_PARTICIPANT_DIR"

    VAULT_CONFIG_FILE="$VAULT_PARTICIPANT_DIR/vault-${PARTICIPANT}-config.json"

    cat > "$VAULT_CONFIG_FILE" << EOF
{
    "storage": {
        "postgresql": {
            "connection_url": "postgresql://vault_user:vault_password@edc-postgres-${PARTICIPANT}:5432/vault?sslmode=disable",
            "table": "vault_kv_store",
            "ha_table": "vault_ha_locks",
            "max_parallel": 128
        }
    },
    "default_lease_ttl": "168h",
    "max_lease_ttl": "720h",
    "ui": true,
    "api_addr": "http://0.0.0.0:8200",
    "listener": {
        "tcp": {
            "address": "0.0.0.0:8200",
            "tls_disable": true
        }
    }
}
EOF
    echo "  ✓ Vault config file created: $VAULT_CONFIG_FILE"
    echo ""

}

echo "Creating Vault Config File"
echo ""

generate_vault_config

echo "=== RESUMEN ==="
echo "Host: $HOST_PORT"
echo "DIDs created: ${CONNECTORS[*]}"
echo ""
echo "Generated file structure:"
echo "$ASSETS_DIR/"
echo "├── dids/"
echo "│   ├── $PARTICIPANT/"
echo "│   │   └── .well-known/"
echo "│   │       └── did.json"
echo "└── keysPrivPub/"
echo "        └── ${PARTICIPANT}/"
echo "            ├── ${PARTICIPANT}_private.pem"
echo "            └── ${PARTICIPANT}_public.pem"
echo "$VAULT_DIR/"
echo "     └── ${PARTICIPANT}/"
echo "        └── vault-${PARTICIPANT}-config.json"
echo ""
echo "URLs DID-Resolver:"
echo "  curl $HOST_PORT_PROTOCOL://$HOST_PORT/$PARTICIPANT/.well-known/did.json"

echo ""
echo "docker-compose configuration:"
echo "  nginx:"
echo "    volumes:"
echo "      - ./$DIDS_DIR/:/var/www/:ro"
echo ""
echo "Suggested environment variables:"

#did_host_encoded=$(echo "$HOST_PORT" | sed 's/:/%3A/g')
echo "  # $PARTICIPANT"
#echo "  EDC_PARTICIPANT_ID: did:web:${did_host_encoded}:${PARTICIPANT}"
#echo "  EDC_IAM_ISSUER_ID: did:web:${did_host_encoded}:${PARTICIPANT}"
echo "  EDC_PARTICIPANT_ID: ${DID_ID}"
echo "  EDC_IAM_ISSUER_ID: ${DID_ID}"
echo "  EDC_IAM_DID_WEB_USE_HTTPS: false"
echo ""
