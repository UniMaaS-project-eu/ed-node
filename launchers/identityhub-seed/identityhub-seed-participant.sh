#!/bin/bash

#set -e

echo "🚀 Starting Identity Seed Participant Script"

# Configuration variables
PARTICIPANT_ID="${PARTICIPANT_ID}"
PARTICIPANT_PROTOCOL="${PARTICIPANT_PROTOCOL}"
PARTICIPANT_HOST="${PARTICIPANT_HOST}"
PARTICIPANT_CALLBACK_PORT="${PARTICIPANT_CALLBACK_PORT}"
PARTICIPANT_IH_HOST="${PARTICIPANT_IH_HOST}"
PARTICIPANT_IH_API_KEY="${PARTICIPANT_IH_API_KEY}"
PARTICIPANT_IH_API_PORT="${PARTICIPANT_IH_API_PORT}"
PARTICIPANT_IH_CREDENTIALS_PORT="${PARTICIPANT_IH_CREDENTIALS_PORT}"
PARTICIPANT_IH_IDENTITY_PORT="${PARTICIPANT_IH_IDENTITY_PORT}"

# Service URLs
IDENTITYHUB_PARTICIPANTS_URL="http://"${PARTICIPANT_IH_HOST}":${PARTICIPANT_IH_IDENTITY_PORT}/api/identity/v1alpha/participants/"

echo "📋 Configuration:"
echo "  - Participant DID: $PARTICIPANT_ID"

# Function to wait until a service is available
wait_for_service() {
    local url=$1
    local service_name=$2
    local max_attempts=60
    local attempt=1
    
    echo "⏳ Waiting for $service_name to be ready..."
    
    while [ $attempt -le $max_attempts ]; do
        if curl -sf "$url/api/check/health" > /dev/null 2>&1; then
            echo "✅ $service_name is ready!"
            return 0
        fi
        
        echo "   Attempt $attempt/$max_attempts - $service_name not ready yet..."
        sleep 5
        attempt=$((attempt + 1))
    done
    
    echo "❌ $service_name failed to become ready after $max_attempts attempts"
    return 1
}

# Function to create participant context
create_participant_context() {
    local participant_id=$1
    local did=$2
    local public_key_file=$3
    local key_id=$4
    local private_key_alias=$5
    local service_endpoints=$6
    local roles=$7
    
    echo "🔑 Reading public key from $public_key_file..."
    if [ ! -f "$public_key_file" ]; then
        echo "❌ Public key file not found: $public_key_file"
        return 1
    fi
    
    local public_key_pem
    public_key_pem=$(sed -E ':a;N;$!ba;s/\r{0,1}\n/\\n/g' "$public_key_file")
    
    echo "📝 Creating participant context for: $participant_id"
    
    local data
    data=$(jq -n \
        --arg participantId "$participant_id" \
        --arg did "$did" \
        --arg keyId "$key_id" \
        --arg privateKeyAlias "$private_key_alias" \
        --arg publicKeyPem "$public_key_pem" \
        --argjson serviceEndpoints "$service_endpoints" \
        --argjson roles "$roles" \
        '{
            "roles": $roles,
            "serviceEndpoints": $serviceEndpoints,
            "active": true,
            "participantId": $participantId,
            "did": $did,
            "key": {
                "keyId": $keyId,
                "privateKeyAlias": $privateKeyAlias,
                "publicKeyPem": $publicKeyPem
            }
        }')

    # Run curl capturing body and HTTP code
    local response_file="/tmp/participant_response.json"
    http_code=$(curl -s -w "%{http_code}" -o "$response_file" \
        --location "$IDENTITYHUB_PARTICIPANTS_URL" \
        --header 'Content-Type: application/json' \
        --header "x-api-key: $PARTICIPANT_IH_API_KEY" \
        --data "$data")
    
    echo "5"

    local body
    body=$(cat "$response_file")

    if [[ "$http_code" =~ ^2 ]]; then
        echo "✅ Participant context created successfully for: $participant_id"
        local client_secret
        client_secret=$(echo "$body" | jq -r '.clientSecret // empty')
        if [ -n "$client_secret" ]; then
            echo "🔑 Client Secret retrieved: $client_secret"
            echo "$client_secret"
        else
            echo "⚠️  No clientSecret returned in response."
        fi
        return 0
    else
        echo "❌ Failed to create participant context for: $participant_id"
        echo "HTTP code: $http_code"
        echo "Response body: $body"
        return 1
    fi
}


# Wait for services to be ready
wait_for_service "http://"${PARTICIPANT_IH_HOST}":${PARTICIPANT_IH_API_PORT}" "IdentityHub" || exit 1

echo ""
echo "🎯 Starting participant context creation..."

# Create Participant context
echo ""
echo "2️⃣  Creating Participant context..."

participant_service_endpoints=$(jq -n \
    --arg credentialEndpoint "${PARTICIPANT_PROTOCOL}://${PARTICIPANT_HOST}:${PARTICIPANT_IH_CREDENTIALS_PORT}/api/credentials/v1/participants/$(echo -n "$PARTICIPANT_ID" | base64 -w 0)" \
    --arg dspEndpoint "${PARTICIPANT_PROTOCOL}://${PARTICIPANT_HOST}:${PARTICIPANT_CALLBACK_PORT}/api/dsp" \
    '[
        {
            "type": "CredentialService",
            "serviceEndpoint": $credentialEndpoint,
            "id": "participant-credentialservice-1"
        },
        {
            "type": "ProtocolEndpoint", 
            "serviceEndpoint": $dspEndpoint,
            "id": "participant-dsp"
        }
    ]')

participant_roles='[]'

participant_client_secret=$(create_participant_context \
    "$PARTICIPANT_ID" \
    "$PARTICIPANT_ID" \
    "/vault/keys/certificate.pem" \
    "${PARTICIPANT_ID}#key-1" \
    "key-1" \
    "$participant_service_endpoints" \
    "$participant_roles")

if [ $? -ne 0 ]; then
    echo "❌ Failed to create Participant context"
    exit 1
fi

echo ""
echo "🎉 Identity seeding completed successfully!"
echo ""
echo "📋 Summary:"
echo "  ✅ Participant context created" 
echo ""
echo "🔑 Important Information:"
echo "  - Participant DID: $PARTICIPANT_ID"
echo ""
