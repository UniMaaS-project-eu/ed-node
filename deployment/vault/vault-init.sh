#!/bin/sh

# Verifica que se hayan proporcionado 4 parámetros:
# 1. VAULT_ADDR
# 2. DID
# 3. POSTGRES_ADDR
if [ "$#" -ne 3 ]; then
  echo "Error: Debes proporcionar los 3 parámetros requeridos."
  echo "Uso: $0 <VAULT_ADDR> <DID> <POSTGRES_ADDR> "
  echo "Ejemplo: $0 http://edc-vault-connector:8200 did:web:localhost%3A9876:connector edc-postgres-connector:5432"
  exit 1
fi

VAULT_ADDR="$1"
export VAULT_ADDR
echo "Usando VAULT_ADDR: $VAULT_ADDR"

DID="$2"
#export DID
echo "Usando DID: $DID"

POSTGRES_ADDR="$3"
#export POSTGRES_ADDR
echo "Usando POSTGRES_ADDR: $POSTGRES_ADDR"

# --- función para generar un secreto aleatorio (varios fallback) ---
generate_secret() {

  # 1) openssl si existe
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

  # 4) tr + head (alnum) — muy portable (busybox tiene tr & head)
  if command -v tr >/dev/null 2>&1 && command -v head >/dev/null 2>&1; then
    tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32
    return 0
  fi

  # 5) xxd si existe (hex)
  if command -v xxd >/dev/null 2>&1; then
    xxd -l 32 -p /dev/urandom
    return 0
  fi

  echo "ERROR: no hay forma de generar un secreto (openssl/dd/head/tr/xxd no disponibles)" >&2
  return 1
}

# Espera a que Vault esté disponible
until curl -s $VAULT_ADDR/v1/sys/health | grep -q '"sealed":'; do
  echo "Esperando a Vault en $VAULT_ADDR..."
  sleep 2
done

# Verifica si ya está inicializado
if vault status | grep -q "Initialized.*false"; then
  echo "Inicializando Vault..."
  vault operator init -key-shares=1 -key-threshold=1 -format=json > /vault/init-output.json

  # Extrae valores
  UNSEAL_KEY=$(cat /vault/init-output.json | jq -r '.unseal_keys_b64[0]')
  ROOT_TOKEN=$(cat /vault/init-output.json | jq -r '.root_token')

  echo "Unseal key: $UNSEAL_KEY"
  echo "Root token: $ROOT_TOKEN"

  # Guarda las claves para uso posterior
  echo "$UNSEAL_KEY" > /vault/unseal.key
  echo "$ROOT_TOKEN" > /vault/root.token

  # Desbloquea Vault
  vault operator unseal $UNSEAL_KEY

  # Login
  vault login $ROOT_TOKEN

  # Habilita el motor de secretos KV v2 (importante para EDC)
  vault secrets enable -version=2 -path=secret kv

  # Preparar la URL y credenciales de la base de datos (EDC)
  DB_URL_POSTGRES_EDC="jdbc:postgresql://"$POSTGRES_ADDR"/edc"
  DB_USER_POSTGRES_EDC="edc_user"
  DB_PASSWORD_POSTGRES_EDC="edc_password"

  echo "Subiendo secretos individuales en rutas separadas..."

  # ==========================================
  # Secretos para EDC Connector (manteniendo compatibilidad)
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

  echo "✅ Secretos de base de datos para EDC subidos por separado"

  # ==========================================
  # Secretos específicos para IdentityHub
  # ==========================================
  echo "Configurando secretos para IdentityHub..."

  # Preparar la URL y credenciales de la base de datos (IdentityHub)
  DB_URL_POSTGRES_IDENTITYHUB="jdbc:postgresql://"$POSTGRES_ADDR"/identityhub"
  DB_USER_POSTGRES_IDENTITYHUB="identityhub_user"
  DB_PASSWORD_POSTGRES_IDENTITYHUB="identityhub_password"

  vault kv put secret/edc.datasource.identityhub.url content="$DB_URL_POSTGRES_IDENTITYHUB"
  vault kv put secret/edc.datasource.identityhub.user content="$DB_USER_POSTGRES_IDENTITYHUB"
  vault kv put secret/edc.datasource.identityhub.password content="$DB_PASSWORD_POSTGRES_IDENTITYHUB"
  
  # PDTE_JUAN no se para que sirve: IH_JWT_SIGNING_KEY ni si está en la clave correcta
  ## Claves de cifrado y firma para IdentityHub
  ##IH_JWT_SIGNING_KEY=$(openssl rand -base64 32)
  #IH_JWT_SIGNING_KEY=$(generate_secret) || exit 1
  #vault kv put secret/identityhub.jwt.signing.key content="$IH_JWT_SIGNING_KEY"

  # PDTE_JUAN no se para que sirve: IH_CREDENTIAL_ENCRYPTION_KEY ni si está en la clave correcta
  ##IH_CREDENTIAL_ENCRYPTION_KEY=$(openssl rand -base64 32)
  #IH_CREDENTIAL_ENCRYPTION_KEY=$(generate_secret) || exit 1
  #vault kv put secret/identityhub.credential.encryption.key content="$IH_CREDENTIAL_ENCRYPTION_KEY"

  # PDTE_JUAN no se para que sirve: IH_STS_CLIENT_SECRET ni si está en la clave correcta
  ## Client secret para STS del IdentityHub
  ##IH_STS_CLIENT_SECRET=$(openssl rand -base64 32)
  #IH_STS_CLIENT_SECRET=$(generate_secret) || exit 1
  #vault kv put secret/identityhub.sts.client.secret content="$IH_STS_CLIENT_SECRET"

  # PDTE_JUAN no se para que sirve: IH_API_KEY ni si está en la clave correcta
  ## API Key para operaciones internas
  ##IH_API_KEY=$(openssl rand -base64 32)
  #IH_API_KEY=$(generate_secret) || exit 1
  #vault kv put secret/identityhub.api.key content="$IH_API_KEY"

  echo "✅ Secretos específicos para IdentityHub configurados"

  # ==========================================
  # Certificados y claves compartidas (manteniendo compatibilidad)
  # ==========================================
  # Subir certificados, si existen
  if [ -f "/vault/keys/private-key.pem" ] && [ -f "/vault/keys/certificate.pem" ]; then
      #PRIVATE_KEY=$(cat /vault/keys/private-key.pem | sed ':a;N;$!ba;s/\n/\\n/g')
      # Extraer el JWK a partir de la clave privada Ed25519
      PRIVATE_KEY_PATH="/vault/keys/private-key.pem"
      CERTIFICATE_PATH="/vault/keys/certificate.pem"
      JWK_JSON_PATH="private-key_jwk.json"

      # Extraer 'd' desde la clave privada (private-key.pem)
      PRIVATE_D=$(awk '/BEGIN PRIVATE KEY/{flag=1;next}/END PRIVATE KEY/{flag=0}flag' "$PRIVATE_KEY_PATH" | \
        base64 -d | tail -c 32 | base64 | tr '+/' '-_' | tr -d '=')
      echo "- PRIVATE_JWK (d): $PRIVATE_D"

      # Extraer 'x' desde la clave privada Ed25519
      PRIVATE_JWK=$(openssl pkey -in "$PRIVATE_KEY_PATH" -pubout -outform DER | tail -c 32 | base64 | tr '+/' '-_' | tr -d '=')
      echo "- PRIVATE_JWK (x): $PRIVATE_JWK"
      
      # Generar JSON JWK
    cat > "$JWK_JSON_PATH" <<EOF
{
  "kty": "OKP",
  "crv": "Ed25519",
  "x": "${PRIVATE_JWK}",
  "d": "${PRIVATE_D}"
}
EOF

      echo "✅ JSON del JWK (private-key) generado en $JWK_JSON_PATH"

      # Subir JWK al Vault
      vault kv put secret/key-1 content="$(cat "$JWK_JSON_PATH")"
      echo "✅ JWK subido a Vault como secret/key-1"

      JWK_JSON_PATH="certificate_jwk.json"

      # Subir clave pública al Vault
      if [ -f "$CERTIFICATE_PATH" ]; then

        #PUBLIC_KEY=$(cat "$CERTIFICATE_PATH" | sed ':a;N;$!ba;s/\n/\\n/g')
        #vault kv put secret/certificate content="$PUBLIC_KEY"

        # Extraer 'x' desde la clave publica Ed25519
        PUBLIC_JWK=$(openssl pkey -pubin -in "$CERTIFICATE_PATH" -pubout -outform DER | tail -c 32 | base64 | tr '+/' '-_' | tr -d '=')
        echo "- PUBLIC_JWK (x): $PUBLIC_JWK"

        # Generar JSON JWK
        cat > "$JWK_JSON_PATH" <<EOF
{
  "kty": "OKP",
  "crv": "Ed25519",
  "x": "${PUBLIC_JWK}"
}
EOF

        echo "✅ JSON del JWK (certificate) generado en $JWK_JSON_PATH"
        vault kv put secret/certificate content="$(cat "$JWK_JSON_PATH")"
        
        echo "✅ Clave pública subida a Vault como secret/certificate"

      else
        # Si no hay archivo, usar la parte pública derivada de la privada
        #vault kv put secret/certificate content="$PRIVATE_JWK"

        # Generar JSON JWK
        cat > "$JWK_JSON_PATH" <<EOF
{
  "kty": "OKP",
  "crv": "Ed25519",
  "x": "${PRIVATE_JWK}"
}
EOF
        
        vault kv put secret/key-1 content="$(cat "$JWK_JSON_PATH")"
        
        echo "⚠️ Clave pública no encontrada; se sube la derivada de la clave privada"
      fi

      echo "✅ Proceso completo: JWK y clave pública disponibles en Vault"

      # Subir certificado al Vault
      #CERTIFICATE=$(cat /vault/keys/certificate.pem | sed ':a;N;$!ba;s/\n/\\n/g')
      #CERTIFICATE=$(cat "$CERTIFICATE_PATH" | sed ':a;N;$!ba;s/\n/\\n/g')
      #vault kv put secret/certificate content="$CERTIFICATE"
      
      #echo "✅ Claves privadas y certificados encontrados"

      # Almacenar las claves con los alias esperados por EDC
      #vault kv put secret/key-1 content="$PRIVATE_KEY"
      # Subirlo al Vault como secreto
      #vault kv put secret/key-1 content="$(cat "$JWK_JSON_PATH")"
      #echo "✅ JWK subido a Vault como secret/key-1"

      #echo "✅ JWK y clave pública subidos a Vault con alias compatibles con EDC"
      
      #echo "✅ JWK y certificado subidos a Vault"

      #PDTE_JUAN: NO TENGO CLARO QUE STS_CLIENT_SECRET lo podamos definir así,
      #por lo que parece hay que obtener recuperado en el momento del registro
      #del participante (script identityhub-seed-participant.sh, identityhub-seed-issuer.sh)
      # Para el client secret de STS (formato esperado por EDC)
      #STS_CLIENT_SECRET=$(openssl rand -base64 32)
      #STS_CLIENT_SECRET=$(generate_secret) || exit 1
      #echo "✅STS_CLIENT_SECRET ($STS_CLIENT_SECRET)."
      #vault kv put secret/"$DID"-sts-client-secret content="$STS_CLIENT_SECRET"

      #echo "✅ Certificados y claves DID subidos"
  else
      echo "⚠️  Archivos de claves no encontrados en /vault/keys/"
  fi

  # Verificar que se subieron correctamente
  echo "Verificando secretos almacenados:"
  vault kv get secret/edc
  # PDTE_JUAN: no se si se usará o no...
  #echo "Verificando secretos de IdentityHub:"
  #vault kv get secret/identityhub.jwt.signing.key

  echo "✅ Configuración básica completada"

  # ==========================================
  # Políticas de seguridad
  # ==========================================
  echo "Configurando políticas para EDC e IdentityHub..."

#  # Política para EDC (manteniendo compatibilidad)
#  cat > /vault/edc-policy.hcl << 'EOF'
#path "secret/data/edc" {
#  capabilities = ["read"]
#}
#path "secret/metadata/edc" {
#  capabilities = ["read"]
#}
#path "secret/data/edc.*" {
#  capabilities = ["read"]
#}
#path "secret/metadata/edc.*" {
#  capabilities = ["read"]
#}
#path "secret/data/*" {
#  capabilities = ["read"]
#}
#path "secret/metadata/*" {
#  capabilities = ["read"]
#}
#path "auth/token/lookup-self" {
#  capabilities = ["read"]
#}
#path "auth/token/renew-self" {
#  capabilities = ["update"]
#}
#EOF

  # Política específica para IdentityHub
#  cat > /vault/identityhub-policy.hcl << 'EOF'
#path "secret/data/edc" {
#  capabilities = ["read"]
#}
#path "secret/metadata/edc" {
#  capabilities = ["read"]
#}
#path "secret/data/edc.*" {
#  capabilities = ["read"]
#}
#path "secret/metadata/edc.*" {
#  capabilities = ["read"]
#}
#path "secret/data/*" {
#  capabilities = ["read"]
#}
#path "secret/metadata/*" {
#  capabilities = ["read"]
#}
#path "secret/data/identityhub*" {
#  capabilities = ["read", "list"]
#}
#path "secret/metadata/identityhub*" {
#  capabilities = ["read", "list"]
#}
#path "secret/data/did:web:*" {
#  capabilities = ["read"]
#}
#path "secret/metadata/did:web:*" {
#  capabilities = ["read"]
#}
#path "secret/data/private-key" {
#  capabilities = ["read"]
#}
#path "secret/data/certificate" {
#  capabilities = ["read"]
#}
#path "auth/token/lookup-self" {
#  capabilities = ["read"]
#}
#path "auth/token/renew-self" {
#  capabilities = ["update"]
#}
#EOF

  # Política específica para TODO (EDC+IdentityHub)
  cat > /vault/all-policy.hcl << 'EOF'
path "*" {
  capabilities = ["create", "read", "update", "delete", "list", "sudo"]
}
EOF

  #vault policy write edc-policy /vault/edc-policy.hcl
  #vault policy write identityhub-policy /vault/identityhub-policy.hcl
  vault policy write all-policy /vault/all-policy.hcl

  # ==========================================
  # Tokens específicos para cada servicio
  # ==========================================
  export VAULT_TOKEN="$ROOT_TOKEN"
  
  # Token para EDC (manteniendo compatibilidad)
  #echo "Creando token EDC con política edc-policy..."
  #EDC_TOKEN=$(vault token create -policy=edc-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')
  echo "Creando token EDC con política all-policy..."
  EDC_TOKEN=$(vault token create -policy=all-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')

  
  if [ -n "$EDC_TOKEN" ] && [ "$EDC_TOKEN" != "null" ]; then
    echo "$EDC_TOKEN" > /vault/token
    echo "✅ Token EDC creado y guardado: $(echo $EDC_TOKEN | head -c 20)..."
    
    # Verificar que el archivo se creó
    if [ -f "/vault/token" ]; then
      echo "✅ Archivo /vault/token verificado (tamaño: $(wc -c < /vault/token) bytes)"
    else
      echo "❌ Error: Archivo /vault/token no se creó"
      exit 1
    fi
  else
    echo "❌ Error: No se pudo crear el token EDC"
    exit 1
  fi

  # Token para IdentityHub
  #echo "Creando token IdentityHub con política identityhub-policy..."
  #IH_TOKEN=$(vault token create -policy=identityhub-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')
  echo "Creando token IdentityHub con política all-policy..."
  IH_TOKEN=$(vault token create -policy=all-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')
  
  if [ -n "$IH_TOKEN" ] && [ "$IH_TOKEN" != "null" ]; then
    echo "$IH_TOKEN" > /vault/identityhub-token
  #  echo "✅ Token renovable IdentityHub con política 'identityhub-policy' creado: $(echo $IH_TOKEN | head -c 20)..."
    echo "✅ Token renovable IdentityHub con política 'all-policy' creado: $(echo $IH_TOKEN | head -c 20)..."
    
    # Verificar que el archivo se creó
    if [ -f "/vault/identityhub-token" ]; then
      echo "✅ Archivo /vault/identityhub-token verificado (tamaño: $(wc -c < /vault/identityhub-token) bytes)"
    else
      echo "❌ Error: Archivo /vault/identityhub-token no se creó"
      exit 1
    fi
  else
    echo "❌ Error: No se pudo crear el token IdentityHub"
    exit 1
  fi

else
  echo "Vault ya está inicializado. Desbloqueando..."

  # Lee la clave de desbloqueo existente
  if [ -f "/vault/unseal.key" ]; then
    UNSEAL_KEY=$(cat /vault/unseal.key)
    vault operator unseal $UNSEAL_KEY
    echo "✅ Vault desbloqueado correctamente"
  else
    echo "❌ Error: Archivo unseal.key no encontrado"
    exit 1
  fi

  # Asegurar que los tokens estén disponibles
  if [ -f "/vault/root.token" ]; then
      ROOT_TOKEN=$(cat /vault/root.token)
      export VAULT_TOKEN="$ROOT_TOKEN"

      echo "Creando nuevos tokens con políticas específicas..."
      
      # Token EDC (manteniendo compatibilidad)
      #EDC_TOKEN=$(vault token create -policy=edc-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')
      EDC_TOKEN=$(vault token create -policy=all-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')
      if [ -n "$EDC_TOKEN" ] && [ "$EDC_TOKEN" != "null" ]; then
        echo "$EDC_TOKEN" > /vault/token
        #echo "✅ Token renovable EDC con política 'edc-policy' creado: $(echo $EDC_TOKEN | head -c 20)..."
        echo "✅ Token renovable EDC con política 'all-policy' creado: $(echo $EDC_TOKEN | head -c 20)..."
        
        if [ -f "/vault/token" ]; then
          echo "✅ Archivo /vault/token verificado (tamaño: $(wc -c < /vault/token) bytes)"
        else
          echo "❌ Error: Archivo /vault/token no se creó"
          exit 1
        fi
      else
        echo "❌ Error: No se pudo crear el token EDC"
        exit 1
      fi

      # Token IdentityHub
      #IH_TOKEN=$(vault token create -policy=identityhub-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')
      IH_TOKEN=$(vault token create -policy=all-policy -ttl=168h -renewable=true -format=json | jq -r '.auth.client_token')
      if [ -n "$IH_TOKEN" ] && [ "$IH_TOKEN" != "null" ]; then
        echo "$IH_TOKEN" > /vault/identityhub-token
      #  echo "✅ Token renovable IdentityHub con política 'identityhub-policy' creado: $(echo $IH_TOKEN | head -c 20)..."
        echo "✅ Token renovable IdentityHub con política 'all-policy' creado: $(echo $IH_TOKEN | head -c 20)..."
        
        # Verificar que el archivo se creó
        if [ -f "/vault/identityhub-token" ]; then
          echo "✅ Archivo /vault/identityhub-token verificado (tamaño: $(wc -c < /vault/identityhub-token) bytes)"
        else
          echo "❌ Error: Archivo /vault/identityhub-token no se creó"
          exit 1
        fi
      else
        echo "❌ Error: No se pudo crear el token IdentityHub"
        exit 1
      fi
  else
      echo "❌ Error: Archivo root.token no encontrado"
      exit 1
  fi
fi

echo "🎉 Inicialización de Vault completada con soporte para EDC e IdentityHub"