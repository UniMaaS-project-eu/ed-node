import os
import time
import uuid
import json
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, ValidationError
import jwt  # PyJWT
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend


# -----------------------
# Configuration
# -----------------------
ISSUER_DID = os.getenv("ISSUER_DID", "did:web:localhost%3A9876:issuerservice")
PRIVATE_KEY_PATH = os.getenv("PRIVATE_KEY_PATH", "/app/keys/issuer_private.pem")
EXPIRY_DAYS = int(os.getenv("EXPIRY_DAYS", "365"))

# Globals
_private_key = None
_initialized = False
_start_time = time.time()


def load_private_key(path: str):
    with open(path, "rb") as f:
        pem_data = f.read()
    key = serialization.load_pem_private_key(pem_data, password=None, backend=default_backend())
    return key


def initialize():
    global _private_key, _initialized
    try:
        _private_key = load_private_key(PRIVATE_KEY_PATH)
        _initialized = True
        print(f"[INFO] Service initialized successfully with issuer DID: {ISSUER_DID}")
    except Exception as e:
        _initialized = False
        print(f"[ERROR] Failed to load private key: {e}")


# -----------------------
# Models
# -----------------------
class SignCredentialRequest(BaseModel):
    participantDid: str = Field(..., description="DID of the subject/participant")
    credential: Dict[str, Any] = Field(..., description="VC content to be signed")


class SignCredentialResponse(BaseModel):
    signedJwt: Optional[str] = None
    error: Optional[str] = None


class IssueCredentialRequest(BaseModel):
    participantDid: str = Field(..., description="DID of the subject/participant")
    holderDid: str = Field(..., description="DID of the holder/receiver")
    credential: Dict[str, Any] = Field(..., description="VC content to be signed")

    class Config:
        # Allow additional fields without error
        extra = "ignore"


# -----------------------
# App
# -----------------------
app = FastAPI(
    title="Issuer Service - Signer VC",
    version="1.1.0",
    description="Eclipse EDC compatible Verifiable Credentials signer (alg=EdDSA)"
)


@app.on_event("startup")
def on_startup():
    initialize()


# Middleware for requests logging (optional)
@app.middleware("http")
async def log_requests(request: Request, call_next):
    print(f"[DEBUG] {request.method} {request.url}")
    print(f"[DEBUG] Content-Type: {request.headers.get('content-type')}")
    
    response = await call_next(request)
    print(f"[DEBUG] Response status: {response.status_code}")
    return response


# -----------------------
# Endpoint 1: Sign credential
# -----------------------
@app.post("/api/v1/sign-credential", response_model=SignCredentialResponse)
async def sign_credential(payload: SignCredentialRequest):
    print(f"[DEBUG] sign_credential participantDid: {payload.participantDid}")
    
    if not _initialized or _private_key is None:
        return JSONResponse(status_code=503, content=SignCredentialResponse(
            error="Service not initialized").dict())

    now = datetime.now(tz=timezone.utc)
    exp = now + timedelta(days=EXPIRY_DAYS)

    claims = {
        "iss": ISSUER_DID,
        "sub": payload.participantDid,
        "aud": payload.participantDid,
        "vc": payload.credential,
        "iat": int(now.timestamp()),
        "exp": int(exp.timestamp()),
    }

    headers = {
        "kid": f"{ISSUER_DID}#key-1",
        "typ": "JWT",
    }

    try:
        token = jwt.encode(
            claims,
            key=_private_key,
            algorithm="EdDSA",
            headers=headers
        )
        return SignCredentialResponse(signedJwt=token)
    except Exception as e:
        print(f"[ERROR] Error signing credential: {e}")
        return JSONResponse(status_code=500, content=SignCredentialResponse(
            error=f"Internal server error: {str(e)}").dict())


# -----------------------
# Endpoint 2: Issue credential
# -----------------------
@app.post("/api/v1/issue-credential")
async def issue_credential(request: Request):
    print(f"[DEBUG] issue_credential endpoint")
    
    if not _initialized or _private_key is None:
        raise HTTPException(status_code=503, detail="Service not initialized")

    try:
        # Read body
        body = await request.body()
        if not body:
            print("[ERROR] Empty request body")
            raise HTTPException(status_code=400, detail="Empty request body")
        
        # Decode and parse JSON
        try:
            body_str = body.decode('utf-8')
            print(f"[DEBUG] Raw body: {body_str[:500]}...")
            json_data = json.loads(body_str)
            print(f"[DEBUG] JSON parsed successfully. Fields: {list(json_data.keys())}")
        except json.JSONDecodeError as e:
            print(f"[ERROR] Error parsing JSON: {e}")
            raise HTTPException(status_code=400, detail=f"Invalid JSON: {str(e)}")
        
        # Validate required fields manually
        required_fields = ["participantDid", "holderDid", "credential"]
        for field in required_fields:
            if field not in json_data:
                print(f"[ERROR] Missing required field: {field}")
                raise HTTPException(status_code=400, detail=f"Missing required field: {field}")
        
        # Create payload manually (safer than automatic Pydantic)
        payload = IssueCredentialRequest(
            participantDid=json_data["participantDid"],
            holderDid=json_data["holderDid"],
            credential=json_data["credential"]
        )
        
        print(f"[DEBUG] Created Payload: participantDid={payload.participantDid}")
        print(f"[DEBUG] Credential type: {payload.credential.get('type', 'N/A')}")
        
    except ValidationError as ve:
        print(f"[ERROR] Validation error: {ve}")
        raise HTTPException(status_code=422, detail=f"Validation error: {str(ve)}")
    except HTTPException:
        # Re-raise HTTP exceptions
        raise
    except Exception as e:
        print(f"[ERROR] Error processing request: {e}")
        raise HTTPException(status_code=500, detail=f"Error processing request: {str(e)}")

    # Create JWT
    now = datetime.now(tz=timezone.utc)
    exp = now + timedelta(days=EXPIRY_DAYS)

    claims = {
        "iss": ISSUER_DID,
        "sub": payload.participantDid,
        "aud": payload.holderDid,
        "vc": payload.credential,
        "iat": int(now.timestamp()),
        "exp": int(exp.timestamp()),
    }
    
    headers = {
        "kid": f"{ISSUER_DID}#key-1",
        "typ": "JWT",
    }

    try:
        token = jwt.encode(
            claims,
            key=_private_key,
            algorithm="EdDSA",
            headers=headers
        )
        print(f"[DEBUG] JWT token created successfully (length: {len(token)})")
    except Exception as e:
        print(f"[ERROR] Error creating JWT: {e}")
        raise HTTPException(status_code=500, detail=f"Error creating JWT: {str(e)}")

    # Process issue date
    issuance_date_iso = payload.credential.get("issuanceDate")
    issuance_epoch = None
    if issuance_date_iso:
        try:
            # Convert ISO string to epoch timestamp
            issuance_epoch = datetime.fromisoformat(
                issuance_date_iso.replace("Z", "+00:00")
            ).timestamp()
            print(f"[DEBUG] IssuanceDate converted: {issuance_date_iso} -> {issuance_epoch}")
        except Exception as e:
            print(f"[WARNING] Error parsing issuanceDate '{issuance_date_iso}': {e}")
            issuance_epoch = None

    # Build response in EDC format
    response = {
        "id": str(uuid.uuid4()),
        "participantContextId": payload.participantDid,
        "timestamp": int(now.timestamp() * 1000),  # Timestamp milliseconds
        "issuerId": ISSUER_DID,
        "holderId": payload.holderDid,
        "state": 500,  # credential issued
        "issuancePolicy": None,
        "reissuancePolicy": None,
        "verifiableCredential": {
            "format": "VC1_0_JWT",
            "rawVc": token,
            "credential": {
                "credentialSubject": [
                    {
                        "claims": payload.credential.get("credentialSubject")
                    }
                ],
                "id": payload.credential.get("id"),
                "type": payload.credential.get("type"),
                "issuer": {
                    "id": payload.credential.get("issuer"),
                    "additionalProperties": {}
                },
                "issuanceDate": issuance_epoch,
                "expirationDate": None,
                "credentialStatus": None,
                "description": None,
                "name": None,
            }
        }
    }
    
    print(f"[DEBUG] Answer prepared successfully")
    return response


# -----------------------
# Utils Endpoints
# -----------------------
@app.get("/api/v1/health")
def health():
    status = "UP" if _initialized else "DOWN"
    return {
        "status": status,
        "message": "JWT Signer VC Server is running" if _initialized else "Service not properly initialized",
        "issuerDid": ISSUER_DID,
        "timestamp": int(time.time() * 1000),
        "uptimeSeconds": int(time.time() - _start_time)
    }


@app.get("/api/v1/info")
def info():
    return {
        "service": "Issuer Service - Signer VC",
        "version": "1.1.0",
        "description": "Eclipse EDC compatible Verifiable Credentials signer (alg=EdDSA)",
        "issuerDid": ISSUER_DID,
        "initialized": _initialized
    }


# Additional Endpoint for testing
@app.post("/api/v1/test")
async def test_endpoint(request: Request):
    """Testing endpoint"""
    body = await request.body()
    return {
        "method": request.method,
        "url": str(request.url),
        "headers": dict(request.headers),
        "body_length": len(body),
        "body_preview": body.decode('utf-8')[:200] if body else None
    }

