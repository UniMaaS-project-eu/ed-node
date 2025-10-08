#!/bin/sh

# Auto-Unseal Script for HashiCorp Vault (initialize=true and sealed=false)

set -e

VAULT_ADDR="${VAULT_ADDR:-http://edc-vault-connector:8200}"
CHECK_INTERVAL="${CHECK_INTERVAL:-10}"
UNSEAL_KEY_FILE="/vault/unseal.key"
MAX_RETRIES=5
RETRY_DELAY=5

export VAULT_ADDR

echo "======================================"
echo "Vault Auto-Unseal Monitor Starting"
echo "======================================"
echo "Vault Address: $VAULT_ADDR"
echo "Check Interval: ${CHECK_INTERVAL}s"
echo "Unseal Key File: $UNSEAL_KEY_FILE"
echo "======================================"

# Vault API available
wait_for_vault() {
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] Waiting for Vault to be available..."
    local retries=0
    
    while [ $retries -lt $MAX_RETRIES ]; do

        if curl -s "$VAULT_ADDR/v1/sys/health" >/dev/null 2>&1; then
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✅ Vault is responding"
            return 0
        fi
        
        retries=$((retries + 1))
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] Vault not ready yet (attempt $retries/$MAX_RETRIES)..."
        sleep $RETRY_DELAY
    done
    
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] ⚠️ Vault is not responding after $MAX_RETRIES attempts"
    return 1
}

is_vault_sealed() {
    
#    #OPTION 1
#    local STATUS_JSON
#    local IS_SEALED
#
#    STATUS_JSON=$(vault status -format=json 2>/dev/null || cat <<< '{"sealed":true}')
#
#    IS_SEALED=$(echo "$STATUS_JSON" | jq -r '.sealed')
#
#    if [[ "$IS_SEALED" == "true" ]]; then
#        echo "FAIL: Vault is sealed."
#        return 0
#    else
#        echo "SUCCESS: Vault is NOT sealed."
#        return 1
#    fi

    #OPTION 2
    local HEALTH_JSON
    local IS_SEALED

    HEALTH_JSON=$(curl -s "$VAULT_ADDR/v1/sys/health")

    IS_SEALED=$(echo "$HEALTH_JSON" | jq -r '.sealed')

    if [[ "$IS_SEALED" == "true" ]]; then
        echo "FAIL: Vault is sealed."
        return 0
    else
        echo "SUCCESS: Vault is NOT sealed."
        return 1
    fi

}

unseal_vault() {
    if [ ! -f "$UNSEAL_KEY_FILE" ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ❌ ERROR: Unseal key file not found at $UNSEAL_KEY_FILE"
        return 1
    fi
    
    local unseal_key
    unseal_key=$(cat "$UNSEAL_KEY_FILE")
    
    if [ -z "$unseal_key" ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ❌ ERROR: Unseal key is empty"
        return 1
    fi
    
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 🔓 Attempting to unseal Vault..."
    
    if vault operator unseal "$unseal_key" >/dev/null 2>&1; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✅ Vault successfully unsealed!"
        return 0
    else
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ❌ Failed to unseal Vault"
        return 1
    fi
}

is_vault_initialized() {
#    #OPTION 1
#    local STATUS_JSON
#    local IS_INITIALIZED
#
#    STATUS_JSON=$(vault status -format=json 2>/dev/null || cat <<< '{"initialized":false}')
#
#    IS_INITIALIZED=$(echo "$STATUS_JSON" | jq -r '.initialized')
#
#    if [[ "$IS_INITIALIZED" == "true" ]]; then
#        echo "SUCCESS: Vault is initialized."
#        return 0
#    else
#        echo "FAIL: Vault is NOT initialized."
#        return 1
#    fi

    #OPTION 2
    local HEALTH_JSON
    local IS_INITIALIZED

    HEALTH_JSON=$(curl -s "$VAULT_ADDR/v1/sys/health")

    IS_INITIALIZED=$(echo "$HEALTH_JSON" | jq -r '.initialized')

    if [[ "$IS_INITIALIZED" == "true" ]]; then
        echo "SUCCESS: Vault is initialized."
        return 0
    else
        echo "FAIL: Vault is NOT initialized."
        return 1
    fi

}

# Recreate token if required
regenerate_tokens() {
    local root_token_file="/vault/root.token"
    
    if [ ! -f "$root_token_file" ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ⚠️ Root token file not found, skipping token regeneration"
        return 0
    fi
    
    local root_token
    root_token=$(cat "$root_token_file")
    
    if [ -z "$root_token" ]; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ⚠️ Root token is empty, skipping token regeneration"
        return 0
    fi
    
    export VAULT_TOKEN="$root_token"
    
    # Verify if tokens exist/valid
    if [ -f "/vault/token" ] && [ -f "/vault/identityhub-token" ]; then
        local edc_token
        local ih_token
        edc_token=$(cat /vault/token 2>/dev/null)
        ih_token=$(cat /vault/identityhub-token 2>/dev/null)
        
        # Verificar validez del token EDC
        if [ -n "$edc_token" ]; then
            VAULT_TOKEN="$edc_token" vault token lookup >/dev/null 2>&1
            if [ $? -eq 0 ]; then
                echo "[$(date '+%Y-%m-%d %H:%M:%S')] ℹ️ Existing tokens are valid"
                return 0
            fi
        fi
    fi
    
    echo "[$(date '+%Y-%m-%d %H:%M:%S')] 🔄 Regenerating service tokens..."
    
    # Recreate tokens EDC
    local edc_token
    edc_token=$(vault token create -policy=all-policy -ttl=168h -renewable=true -format=json 2>/dev/null | jq -r '.auth.client_token')
    
    if [ -n "$edc_token" ] && [ "$edc_token" != "null" ]; then
        echo "$edc_token" > /vault/token
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✅ EDC token regenerated"
    fi
    
    # Recreate tokens IdentityHub
    local ih_token
    ih_token=$(vault token create -policy=all-policy -ttl=168h -renewable=true -format=json 2>/dev/null | jq -r '.auth.client_token')
    
    if [ -n "$ih_token" ] && [ "$ih_token" != "null" ]; then
        echo "$ih_token" > /vault/identityhub-token
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✅ IdentityHub token regenerated"
    fi
}

# Main Monitoring Loop
echo "[$(date '+%Y-%m-%d %H:%M:%S')] Starting monitoring loop..."
echo ""

CONSECUTIVE_FAILURES=0
MAX_CONSECUTIVE_FAILURES=3

while true; do

    if ! wait_for_vault; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] Vault is not available, waiting ${CHECK_INTERVAL}s..."
        sleep "$CHECK_INTERVAL"
        continue
    fi
    
    if ! is_vault_initialized; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] ⚠️ Vault is not initialized yet, waiting..."
        sleep "$CHECK_INTERVAL"
        continue
    fi
    
    if is_vault_sealed; then
        echo "[$(date '+%Y-%m-%d %H:%M:%S')] 🔒 Vault is SEALED - attempting to unseal..."
        
        if unseal_vault; then
            CONSECUTIVE_FAILURES=0
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] 🎉 Vault is now unsealed and ready!"
            
            sleep 5
            
            regenerate_tokens
        else
            CONSECUTIVE_FAILURES=$((CONSECUTIVE_FAILURES + 1))
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] ⚠️ Unseal failed (consecutive failures: $CONSECUTIVE_FAILURES)"
            
            if [ $CONSECUTIVE_FAILURES -ge $MAX_CONSECUTIVE_FAILURES ]; then
                echo "[$(date '+%Y-%m-%d %H:%M:%S')] ❌ Too many consecutive failures, waiting longer..."
                sleep $((CHECK_INTERVAL * 3))
                CONSECUTIVE_FAILURES=0
            fi
        fi
    else

        if [ $(($(date +%s) % 60)) -eq 0 ]; then
            echo "[$(date '+%Y-%m-%d %H:%M:%S')] ✅ Vault is unsealed and healthy"
        fi
        CONSECUTIVE_FAILURES=0
    fi
    
    sleep "$CHECK_INTERVAL"
done