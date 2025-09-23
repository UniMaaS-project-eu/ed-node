#!/bin/sh

# Verify that 3 parameters have been provided:
# 1. VAULT_ADDR
# 2. DID
# 3. POSTGRES_ADDR
if [ "$#" -ne 3 ]; then
  echo "Error: You must provide the 3 required parameters."
  echo "Usage: $0 <VAULT_ADDR> <DID> <POSTGRES_ADDR> "
  echo "Example: $0 http://edc-vault-connector:8200 did:web:localhost%3A9876:connector edc-postgres-connector:5432"
  exit 1
fi

VAULT_ADDR="$1"
export VAULT_ADDR
echo "Using VAULT_ADDR: $VAULT_ADDR"

DID="$2"
#export DID
echo "Using DID: $DID"

POSTGRES_ADDR="$3"
#export POSTGRES_ADDR
echo "Using POSTGRES_ADDR: $POSTGRES_ADDR"

# --- function to generate a random secret (multiple fallbacks) ---
generate_secret() {

  # 1) openssl if available
  if command -v openssl >/dev/null 2>&1; then
    openssl rand -base64 32
    return 0
  fi

  # 2) dd + base64
  if command -v dd >/dev/null 2>&1 && command -v base64 >/dev/null 2>&1; then
    dd if=/dev/urandom bs=32 count=1 2>/dev/null | base64
    return 0
  fi

  # 3) head + base64
  if command -v head >/dev/null 2>&1 && command -v base64 >/dev/null 2>&1; then
    head -c 32 /dev/urandom | base64
    return 0
  fi

  # 4) tr + head (alnum) — very portable (busybox has tr & head)
  if command -v tr >/dev/null 2>&1 && command -v head >/dev/null 2>&1; then
    tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32
    return 0
  fi

  # 5) xxd if available (hex)
  if command -v xxd >/dev/null 2>&1; then
    xxd -l 32 -p /dev/urandom
    return 0
  fi

  echo "ERROR: no available way to generate a secret (openssl/dd/head/tr/xxd not available)" >&2
  return 1
}

# Wait until Vault is available
until curl -s $VAULT_ADDR/v1/sys/health | grep -q '"sealed":'; do
  echo "Waiting for Vault at $VAULT_ADDR..."
  sleep 2
done

# Check if Vault is already initialized
if vault status | grep -q "Initialized.*false"; then
  echo "Initializing Vault..."
  vault operator init -key-shares=1 -key-threshold=1 -format=json > /vault/init-output.json

  # Extract values
  UNSEAL_KEY=$(cat /vault/init-output.json | jq -r '.unseal_keys_b64[0]')
  ROOT_TOKEN=$(cat /vault/init-output.json | jq -r '.root_token')

  echo "Unseal key: $UNSEAL_KEY"
  echo "Root token: $ROOT_TOKEN"

  # Save keys for later use
  echo "$UNSEAL_KEY" > /vault/unseal.key
  echo "$ROOT_TOKEN" > /vault/root.token

  # Unseal Vault
  vault operator unseal $UNSEAL_KEY

  # Login
  vault login $ROOT_TOKEN

  # Enable KV v2 secrets engine (important for EDC)
  vault secrets enable -version=2 -path=secret kv

  # Prepare DB URL and credentials (EDC)
  DB_URL_POSTGRES_EDC="jdbc:postgresql://"$POSTGRES_ADDR"/edc"
  DB_USER_POSTGRES_EDC="edc_user"
  DB_PASSWORD_POSTGRES_EDC="edc_password"

  echo "Uploading individual secrets into separate paths..."

  # ==========================================
  # Secrets for EDC Connector (keeping compatibility)
  # ==========================================
  vault kv put secret/edc.datasource.default.url content="$DB_URL_POSTGRES_EDC"
  vault kv put secret/edc.datasource.default.user content="$DB_USER_POSTGRES_EDC"
  vault kv put secret/edc.datasource.default.password content="$DB_PASSWORD_POSTGRES_EDC"

  vault kv put secret/edc.datasource.asset.url content="$DB_URL_POSTGRES_EDC"
  vault kv put secret/edc.datasource.asset.user content="$DB_USER_POSTGRES_EDC"
  vault kv put secret/edc.datasource.asset.password content="$DB_PASSWORD_POSTGRES_EDC"

  vault kv put secret/edc.datasource.contractdefinition.url content="$DB_URL_POSTGRES_EDC"
  vault kv put secret/edc.datasource.contractdefinition.user content="$DB_USER_POSTGRES_EDC"
  vault kv put secret/edc.datasource.contractdefinition.password content="$DB_PASSWORD_POSTGRES_EDC"

  vault kv put secret/edc.datasource.policy.url content="$DB_URL_POSTGRES_EDC"
  vault kv put secret/edc.datasource.policy.user content="$DB_USER_POSTGRES_EDC"
  vault kv put secret/edc.datasource.policy.password content="$DB_PASSWORD_POSTGRES_EDC"

  vault kv put secret/edc.datasource.contractnegotiation.url content="$DB_URL_POSTGRES_EDC"
  vault kv put secret/edc.datasource.contractnegotiation.user content="$DB_USER_POSTGRES_EDC"
  vault kv put secret/edc.datasource.contractnegotiation.password content="$DB_PASSWORD_POSTGRES_EDC"

  vault kv put secret/edc.datasource.transferprocess.url content="$DB_URL_POSTGRES_EDC"
  vault kv put secret/edc.datasource.transferprocess.user content="$DB_USER_POSTGRES_EDC"
  vault kv put secret/edc.datasource.transferprocess.password content="$DB_PASSWORD_POSTGRES_EDC"

  echo "✅ Database secrets for EDC uploaded separately"

  # ==========================================
  # IdentityHub-specific secrets
  # ==========================================
  echo "Configuring secrets for IdentityHub..."

  # Prepare DB URL and credentials (IdentityHub)
  DB_URL_POSTGRES_IDENTITYHUB="jdbc:postgresql://"$POSTGRES_ADDR"/identityhub"
  DB_USER_POSTGRES_IDENTITYHUB="identityhub_user"
  DB_PASSWORD_POSTGRES_IDENTITYHUB="identityhub_password"

  vault kv put secret/edc.datasource.identityhub.url content="$DB_URL_POSTGRES_IDENTITYHUB"
  vault kv put secret/edc.datasource.identityhub.user content="$DB_USER_POSTGRES_IDENTITYHUB"
  vault kv put secret/edc.datasource.identityhub.password content="$DB_PASSWORD_POSTGRES_IDENTITYHUB"
  
  # TODO_JUAN not sure what these are used for or if they are in the right key
  ## Encryption and signing keys for IdentityHub
  ##IH_JWT_SIGNING_KEY=$(openssl rand -base64 32)
  #IH_JWT_SIGNING_KEY=$(generate_secret) || exit 1
  #vault kv put secret/identityhub.jwt.signing.key content="$IH_JWT_SIGNING_KEY"

  ##IH_CREDENTIAL_ENCRYPTION_KEY=$(openssl rand -base64 32)
  #IH_CREDENTIAL_ENCRYPTION_KEY=$(generate_secret) || exit 1
  #vault kv put secret/identityhub.credential.encryption.key content="$IH_CREDENTIAL_ENCRYPTION_KEY"

  ## Client secret for IdentityHub STS
  ##IH_STS_CLIENT_SECRET=$(openssl rand -base64 32)
  #IH_STS_CLIENT_SECRET=$(generate_secret) || exit 1
  #vault kv put secret/identityhub.sts.client.secret content="$IH_STS_CLIENT_SECRET"

  ## API Key for internal operations
  ##IH_API_KEY=$(openssl rand -base64 32)
  #IH_API_KEY=$(generate_secret) || exit 1
  #vault kv put secret/identityhub.api.key content="$IH_API_KEY"

  echo "✅ IdentityHub-specific secrets configured"

  # ==========================================
  # Certificates and shared keys (keeping compatibility)
  # ==========================================
  # Upload certificates if available
  if [ -f "/vault/keys/private-key.pem" ] && [ -f "/vault/keys/certificate.pem" ]; then
      # Extract JWK from Ed25519 private key
      PRIVATE_KEY_PATH="/vault/keys/private-key.pem"
      CERTIFICATE_PATH="/vault/keys/certificate.pem"
      JWK_JSON_PATH="private-key_jwk.json"

      # Extract 'd' from private key (private-key.pem)
      PRIVATE_D=$(awk '/BEGIN PRIVATE KEY/{flag=1;next}/END PRIVATE KEY/{flag=0}flag' "$PRIVATE_KEY_PATH" | \
        base64 -d | tail -c 32 | base64 | tr '+/' '-_' | tr -d '=')
      echo "- PRIVATE_JWK (d): $PRIVATE_D"

      # Extract 'x' from Ed25519 private key
      PRIVATE_JWK=$(openssl pkey -in "$PRIVATE_KEY_PATH" -pubout -outform DER | tail -c 32 | base64 | tr '+/' '-_' | tr -d '=')
      echo "- PRIVATE_JWK (x): $PRIVATE_JWK"
      
      # Generate JWK JSON
    cat > "$JWK_JSON_PATH" <<EOF
{
  "kty": "OKP",
  "crv": "Ed25519",
  "x": "${PRIVATE_JWK}",
  "d": "${PRIVATE_D}"
}
EOF

      echo "✅ JWK JSON (private-key) generated at $JWK_JSON_PATH"

      # Upload JWK to Vault
      vault kv put secret/key-1 content="$(cat "$JWK_JSON_PATH")"
      echo "✅ JWK uploaded to Vault as secret/key-1"

      JWK_JSON_PATH="certificate_jwk.json"

      # Upload public key to Vault
      if [ -f "$CERTIFICATE_PATH" ]; then
        # Extract 'x' from Ed25519 public key
        PUBLIC_JWK=$(openssl pkey -pubin -in "$CERTIFICATE_PATH" -pubout -outform DER | tail -c 32 | base64 | tr '+/' '-_' | tr -d '=')
        echo "- PUBLIC_JWK (x): $PUBLIC_JWK"

        # Generate JWK JSON
        cat > "$JWK_JSON_PATH" <<EOF
{
  "kty": "OKP",
  "crv": "Ed25519",
  "x": "${PUBLIC_JWK}"
}
EOF

        echo "✅ JWK JSON (certificate) generated at $JWK_JSON_PATH"
        vault kv put secret/certificate content="$(cat "$JWK_JSON_PATH")"
        
        echo "✅ Public key uploaded to Vault as secret/certificate"

      else
        # If no file exists, use public part derived from private
        cat > "$JWK_JSON_PATH" <<EOF
{
  "kty": "OKP",
  "crv": "Ed25519",
  "x": "${PRIVATE_JWK}"
}
EOF
        
        vault kv put secret/key-1 content="$(cat "$JWK_JSON_PATH")"
        
        echo "⚠️ Public key not found; uploaded derived from private key"
      fi

      echo "✅ Process complete: JWK and public key available in Vault"

  else
      echo "⚠️ Key files not found in /vault/keys/"
  fi

  # Verify secrets were uploaded
  echo "Verifying stored secrets:"
  vault kv get secret/edc
  # TODO_JUAN: not sure if these will be used
  #echo "Verifying IdentityHub secrets:"
  #vault kv get secret/identityhub.jwt.signing.key

  echo "✅ Basic configuration completed"

  # ==========================================
  # Security Policies
  # ==========================================
  echo "Configuring policies for EDC and IdentityHub..."

  # Policy for ALL (EDC+IdentityHub)
  cat > /vault/all-policy.hcl << 'EOF'
path "*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}
EOF

  vault policy write all-policy /vault/all-policy.hcl

  # ==========================================
  # Tokens for each service
  # ==========================================
  export VAULT_TOKEN="$ROOT_TOKEN"
  
  echo "Creating EDC token with all-policy..."
  EDC_TOKEN=$(vault token create -policy=all-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')

  if [ -n "$EDC_TOKEN" ] && [ "$EDC_TOKEN" != "null" ]; then
    echo "$EDC_TOKEN" > /vault/token
    echo "✅ EDC token created and saved: $(echo $EDC_TOKEN | head -c 20)..."
    
    if [ -f "/vault/token" ]; then
      echo "✅ File /vault/token verified (size: $(wc -c < /vault/token) bytes)"
    else
      echo "❌ Error: /vault/token file was not created"
      exit 1
    fi
  else
    echo "❌ Error: Could not create EDC token"
    exit 1
  fi

  echo "Creating IdentityHub token with all-policy..."
  IH_TOKEN=$(vault token create -policy=all-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')
  
  if [ -n "$IH_TOKEN" ] && [ "$IH_TOKEN" != "null" ]; then
    echo "$IH_TOKEN" > /vault/identityhub-token
    echo "✅ Renewable IdentityHub token with 'all-policy' created: $(echo $IH_TOKEN | head -c 20)..."
    
    if [ -f "/vault/identityhub-token" ]; then
      echo "✅ File /vault/identityhub-token verified (size: $(wc -c < /vault/identityhub-token) bytes)"
    else
      echo "❌ Error: /vault/identityhub-token file was not created"
      exit 1
    fi
  else
    echo "❌ Error: Could not create IdentityHub token"
    exit 1
  fi

else
  echo "Vault is already initialized. Unsealing..."

  # Read existing unseal key
  if [ -f "/vault/unseal.key" ]; then
    UNSEAL_KEY=$(cat /vault/unseal.key)
    vault operator unseal $UNSEAL_KEY
    echo "✅ Vault unsealed successfully"
  else
    echo "❌ Error: unseal.key file not found"
    exit 1
  fi

  # Ensure tokens are available
  if [ -f "/vault/root.token" ]; then
      ROOT_TOKEN=$(cat /vault/root.token)
      export VAULT_TOKEN="$ROOT_TOKEN"

      echo "Creating new tokens with specific policies..."
      
      EDC_TOKEN=$(vault token create -policy=all-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')
      if [ -n "$EDC_TOKEN" ] && [ "$EDC_TOKEN" != "null" ]; then
        echo "$EDC_TOKEN" > /vault/token
        echo "✅ Renewable EDC token with 'all-policy' created: $(echo $EDC_TOKEN | head -c 20)..."
        
        if [ -f "/vault/token" ]; then
          echo "✅ File /vault/token verified (size: $(wc -c < /vault/token) bytes)"
        else
          echo "❌ Error: /vault/token file was not created"
          exit 1
        fi
      else
        echo "❌ Error: Could not create EDC token"
        exit 1
      fi

      IH_TOKEN=$(vault token create -policy=all-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')
      if [ -n "$IH_TOKEN" ] && [ "$IH_TOKEN" != "null" ]; then
        echo "$IH_TOKEN" > /vault/identityhub-token
        echo "✅ Renewable IdentityHub token with 'all-policy' created: $(echo $IH_TOKEN | head -c 20)..."
        
        if [ -f "/vault/identityhub-token" ]; then
          echo "✅ File /vault/identityhub-token verified (size: $(wc -c < /vault/identityhub-token) bytes)"
        else
          echo "❌ Error: /vault/identityhub-token file was not created"
          exit 1
        fi
      else
        echo "❌ Error: Could not create IdentityHub token"
        exit 1
      fi
  else
      echo "❌ Error: root.token file not found"
      exit 1
  fi
fi

echo "🎉 Vault initialization completed with support for EDC and IdentityHub"
