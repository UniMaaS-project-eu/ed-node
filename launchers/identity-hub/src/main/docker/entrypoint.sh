#!/bin/sh

echo "=== DEBUG: Starting IdentityHub entrypoint ==="

if [ -z "$EDC_VAULT_HASHICORP_URL" ]; then
    echo "❌ EDC_VAULT_HASHICORP_URL not defined"
    exit 1
fi

RESPONSE=$(curl -s $EDC_VAULT_HASHICORP_URL/v1/sys/health)
echo "$RESPONSE"

# Wait for the Vault API to be available
echo "Waiting for Vault API to be available..."
until RESPONSE=$(curl -s $EDC_VAULT_HASHICORP_URL/v1/sys/health) && echo "$RESPONSE" | grep -q '"sealed":false'; do
    echo "Waiting for Vault at $EDC_VAULT_HASHICORP_URL..."
    echo "Vault response:"
    echo "$RESPONSE"
    sleep 5
done

echo "✅ Vault API is ready and 'sealed':false."

# Wait for the IdentityHub token to be available
echo "Waiting for Vault token for IdentityHub..."
until [ -f /vault-identityhub-token/identityhub-token ]; do
    echo "Waiting for the Vault token for IdentityHub to be available..."
    sleep 5
done

# Check that the token is not empty
if [ ! -s /vault-identityhub-token/identityhub-token ]; then
    echo "ERROR: The identityhub-token file exists but is empty"
    # Try to use the main token as fallback
    if [ -f /vault-token/token ] && [ -s /vault-token/token ]; then
        echo "FALLBACK: Using main Vault token"
        VAULT_TOKEN=$(cat /vault-token/token)
    else
        echo "ERROR: No tokens available"
        exit 1
    fi
else
    # Read the specific IdentityHub token
    VAULT_TOKEN=$(cat /vault-identityhub-token/identityhub-token)
fi

echo "✅ Vault token for IdentityHub successfully read (length: ${#VAULT_TOKEN})"

echo "=== DEBUG: Starting Java with the following configurations ==="
echo "- Vault token configured: YES"
echo "- Configuration file: ./config.properties"  
echo "- Log level: debug"

# Show some relevant environment variables for debugging
echo "=== Relevant IdentityHub environment variables ==="
env | grep EDC_ | head -10

echo "=== Starting IdentityHub ==="

# Use the same pattern as your EDC connector
exec java \
    -Dedc.vault.hashicorp.token="$VAULT_TOKEN" \
    -Djava.util.logging.config.file=/app/logging.properties \
    -Djava.security.egd=file:/dev/urandom \
    -Dedc.fs.config=./config.properties \
    -jar identity-hub.jar --log-level=debug
