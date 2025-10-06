import os
import time
import uuid
import json
from datetime import datetime, timedelta, timezone
from typing import Any, Dict, Optional

from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse
from pydantic import BaseModel, Field, ValidationError
from typing import List, Optional
import jwt  # PyJWT
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.backends import default_backend


# -----------------------
# Configuration
# -----------------------
ISSUER_DID = os.getenv("ISSUER_DID", "did:web:localhost%3A9876:issuerservicevc")
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

class IssueGaiaxCredentialRequest(BaseModel):
    participantDid: str = Field(..., description="DID of the subject/participant")
    legalName: str = Field(..., description="Legal name of the participant")
    countryCode: str = Field(..., description="Country code (e.g., ES)")
    vatNumber: Optional[str] = Field(None, description="VAT registration number")
    addressCode: Optional[str] = Field(None, description="Address subdivision code")
    streetAddress: Optional[str] = Field(None, description="Street address")
    postalCode: Optional[str] = Field(None, description="Postal code")
    roles: Optional[List[str]] = Field(None, description="List of participant roles (e.g., ['DataProvider', 'DataConsumer'])")


    class Config:
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
        "message": "Issuer Service Signer VC Server is running" if _initialized else "Service not properly initialized",
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

@app.post("/api/v1/issue-gaiax-credential-jwt")
async def issue_gaiax_credential(payload: IssueGaiaxCredentialRequest):
    """
    Issues a GAIA-X 22.06/24.11 compliant credential in JWT format
    """
    print(f"[DEBUG] issue_gaiax_credential for: {payload.participantDid}")
    
    if not _initialized or _private_key is None:
        raise HTTPException(status_code=503, detail="Service not initialized")

    now = datetime.now(tz=timezone.utc)
    exp = now + timedelta(days=EXPIRY_DAYS)
    credential_id = f"https://issuer.example.com/credentials/{uuid.uuid4()}"

    # Build credential claims
    credential_subject = {
        "id": payload.participantDid,
        "gx-participant:legalName": payload.legalName,
        "gx-participant:legalAddress": {
            "gx-participant:addressCountryCode": payload.countryCode,
            "gx-participant:addressCode": payload.addressCode or "",
            "gx-participant:streetAddress": payload.streetAddress or "",
            "gx-participant:postalCode": payload.postalCode or ""
        }
    }

    if payload.vatNumber:
        credential_subject["gx-participant:registrationNumber"] = {
            "gx-participant:registrationNumberType": "VAT",
            "gx-participant:registrationNumberNumber": payload.vatNumber
        }
    
    # Include participant roles if provided
    if payload.roles:
        credential_subject["gx:participantRole"] = payload.roles

    # JWT Claims
    jwt_claims = {
        "iss": ISSUER_DID,
        "sub": payload.participantDid,
        "aud": payload.participantDid,
        "nbf": int(now.timestamp()),
        "iat": int(now.timestamp()),
        "exp": int(exp.timestamp()),
#        "jti": credential_id,
        "vc": {
            "@context": [
                "https://www.w3.org/2018/credentials/v1",
#                "https://registry.gaia-x.eu/v2206/api/shape"
                "https://registry.lab.gaia-x.eu/main/context/2411"
            ],
            "id": credential_id,
            "type": ["VerifiableCredential", "LegalPerson"],
            "credentialSubject": credential_subject,
            "credentialSchema": [
                {
#                    "id": "https://registry.gaia-x.eu/v2206/api/shape",
                    "id": "https://registry.lab.gaia-x.eu/main/context/2411",
                    "type": "JsonSchemaValidator2018"
                }
            ],
            "issuer": ISSUER_DID,
            "issuanceDate": now.isoformat().replace("+00:00", "Z"),
            "expirationDate": exp.isoformat().replace("+00:00", "Z")
        }
    }

    # Sign JWT with EdDSA
    try:
        import jwt as pyjwt
        from cryptography.hazmat.primitives.asymmetric import ed25519
        
        if not isinstance(_private_key, ed25519.Ed25519PrivateKey):
            raise ValueError("Private key must be Ed25519")
        
        # Convert to PEM for PyJWT
        from cryptography.hazmat.primitives import serialization
        private_pem = _private_key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption()
        )
        
        # Sign JWT with EdDSA algorithm
        jwt_token = pyjwt.encode(
            jwt_claims,
            private_pem,
            algorithm="EdDSA",
            headers={"kid": f"{ISSUER_DID}#key-1"}
        )
        
        print(f"[DEBUG] GAIA-X JWT credential signed successfully")
        
    except Exception as e:
        print(f"[ERROR] Error signing JWT: {e}")
        import traceback
        traceback.print_exc()
        raise HTTPException(status_code=500, detail=f"Error signing credential: {str(e)}")

    # Return in EDC IdentityHub format
    response = {
        "id": str(uuid.uuid4()),
        "participantContextId": payload.participantDid,
        "timestamp": int(now.timestamp() * 1000),
        "issuerId": ISSUER_DID,
        "holderId": payload.participantDid,
        "state": 500,
        "verifiableCredential": {
            "format": "VC1_0_JWT",
            "rawVc": jwt_token,  # JWT string
            "credential": jwt_claims["vc"]  # VC payload for reference
        }
    }
    
    return response

# -----------------------
# Endpoint 3: Issue GAIA-X credential (JSON-LD format)
# -----------------------
# DEPRETATED IS NOT WORKING/TESTED, WAS REPLACED BY issue-gaiax-credential-jwt
#@app.post("/api/v1/issue-gaiax-credential-ldproof")
#async def issue_gaiax_credential(payload: IssueGaiaxCredentialRequest):
#    """
#    Issues a GAIA-X 22.06/24.11 compliant credential in JSON-LD format
#    Compatible with Eclipse EDC TrustFrameworkAdoption
#    """
#    print(f"[DEBUG] issue_gaiax_credential for: {payload.participantDid}")
#    
#    if not _initialized or _private_key is None:
#        raise HTTPException(status_code=503, detail="Service not initialized")#
#
#    now = datetime.now(tz=timezone.utc)
#    exp = now + timedelta(days=EXPIRY_DAYS)
#    credential_id = f"https://issuer.example.com/credentials/{uuid.uuid4()}"
#
#    # Build the credential in JSON-LD format (not JWT)
#    credential = {
#        "@context": [
#            "https://www.w3.org/2018/credentials/v1",
#            "https://registry.gaia-x.eu/v2206/api/shape"
#        ],
#        "id": credential_id,
#        "type": ["VerifiableCredential", "LegalPerson"],
#        "issuer": {
#            "id": ISSUER_DID
#        },
#        "issuanceDate": now.isoformat(),
#        "expirationDate": exp.isoformat(),
#        "credentialSubject": {
#            "id": payload.participantDid,
#            "gx-participant:legalName": payload.legalName,
#            "gx-participant:legalAddress": {
#                "gx-participant:addressCountryCode": payload.countryCode,
#                "gx-participant:addressCode": payload.addressCode or "",
#                "gx-participant:streetAddress": payload.streetAddress or "",
#                "gx-participant:postalCode": payload.postalCode or ""
#            }
#        },
#        "credentialSchema": [
#            {
#                "id": "https://registry.gaia-x.eu/v2206/api/shape",
#                "type": "JsonSchemaValidator2018"
#            }
#        ]
#    }
#
#    # Add optional fields
#    if payload.vatNumber:
#        credential["credentialSubject"]["gx-participant:registrationNumber"] = {
#            "gx-participant:registrationNumberType": "VAT",
#            "gx-participant:registrationNumberNumber": payload.vatNumber
#        }
#    # Include participant roles if provided
#    if payload.roles:
#        credential_subject["gx:participantRole"] = payload.roles
#
#    # Create proof using LDP (Linked Data Proofs)
#    # Serialize credential for signing
#    try:
#        import hashlib
#        import base64
#        
#        # Canonical JSON for signing
#        canonical = json.dumps(credential, sort_keys=True, separators=(',', ':'))
#        message_hash = hashlib.sha256(canonical.encode()).digest()
#        
#        # Sign with private key
#        from cryptography.hazmat.primitives.asymmetric import ed25519
#        if isinstance(_private_key, ed25519.Ed25519PrivateKey):
#            signature = _private_key.sign(message_hash)
#            signature_b64 = base64.b64encode(signature).decode('utf-8')
#        else:
#            raise ValueError("Private key must be Ed25519 for GAIA-X credentials")
#        
#        # Add proof to credential
#        credential["proof"] = {
#            "type": "Ed25519Signature2020",
#            "created": now.isoformat(),
#            "proofPurpose": "assertionMethod",
#            "verificationMethod": f"{ISSUER_DID}#key-1",
#            "proofValue": signature_b64
#        }
#        
#        print(f"[DEBUG] GAIA-X credential created and signed successfully")
#        
#    except Exception as e:
#        print(f"[ERROR] Error signing GAIA-X credential: {e}")
#        raise HTTPException(status_code=500, detail=f"Error signing credential: {str(e)}")
#
#    # Serialize the complete credential as rawVc
#    raw_vc = json.dumps(credential, separators=(',', ':'))
#
#    # Return in EDC IdentityHub format
#    response = {
#        "id": str(uuid.uuid4()),
#        "participantContextId": payload.participantDid,
#        "timestamp": int(now.timestamp() * 1000),
#        "issuerId": ISSUER_DID,
#        "holderId": payload.participantDid,
#        "state": 500,
#        "verifiableCredential": {
#            "format": "VC1_0_LD",  # CRITICAL: Not JWT format
#            "rawVc": raw_vc, # JSON (include proof)
#            "credential": credential  # Direct JSON-LD object
#        }
#    }
#    
#    return response