#!/bin/sh

echo "=== DEBUG: Starting EDC connector entrypoint ==="

# Wait for the token to be available
echo "Waiting for Vault token..."
until [ -f /vault-token/token ]; do
  echo "Waiting for the Vault token to be available..."
  sleep 5
done

# Check that the token is not empty
if [ ! -s /vault-token/token ]; then
  echo "ERROR: The token file exists but is empty"
  exit 1
fi

# Read the token generated in the volume
VAULT_TOKEN=$(cat /vault-token/token)
echo "✅ Vault token successfully read (length: ${#VAULT_TOKEN})"

echo "=== DEBUG: Starting Java with the following configurations ==="
echo "- Vault token configured: YES"
echo "- Configuration file: ./config.properties"
echo "- Log level: debug"

# Show some relevant environment variables for debugging
echo "=== Relevant EDC environment variables ==="
env | grep EDC_ | head -10

echo "=== Starting EDC connector ==="

exec java \
  -Dedc.vault.hashicorp.token="$VAULT_TOKEN" \
  -Djava.util.logging.config.file=/app/logging.properties \
  -Djava.security.egd=file:/dev/urandom \
  -Dedc.fs.config=./config.properties \
  -jar connector.jar --log-level=debug
