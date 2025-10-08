# Trustframework expected format (GAIA-X 22.06)

```json
{
  "@context": [
    "https://www.w3.org/2018/credentials/v1",
    "https://registry.gaia-x.eu/v2206/api/shape"
  ],
  "id": "https://example.com/credentials/12345",
  "type": [
    "VerifiableCredential",
    "LegalPerson"
  ],
  "issuer": {
    "id": "did:web:issuer.example.com"
  },
  "issuanceDate": "2025-01-10T00:00:00Z",
  "expirationDate": "2026-01-10T00:00:00Z",
  "credentialSubject": {
    "id": "did:web:localhost%3A7083",
    "gx-participant:name": "Test company",
    "gx-participant:legalName": "Test company S.L.",
    "gx-participant:registrationNumber": {
      "gx-participant:registrationNumberType": "VAT",
      "gx-participant:registrationNumberNumber": "ESB12345678"
    },
    "gx-participant:headquarterAddress": {
      "gx-participant:addressCountryCode": "ES",
      "gx-participant:addressCode": "ES-M",
      "gx-participant:streetAddress": "Street Example 123",
      "gx-participant:postalCode": "28001"
    },
    "gx-participant:legalAddress": {
      "gx-participant:addressCountryCode": "ES",
      "gx-participant:addressCode": "ES-M",
      "gx-participant:streetAddress": "Street Legal 456",
      "gx-participant:postalCode": "28002"
    },
    "gx-participant:termsAndConditions": "https://example.com/terms"
  },
  "credentialSchema": [
    {
      "id": "https://registry.gaia-x.eu/v2206/api/shape",
      "type": "JsonSchemaValidator2018"
    }
  ],
  "proof": {
    "type": "JsonWebSignature2020",
    "created": "2025-01-10T00:00:00Z",
    "proofPurpose": "assertionMethod",
    "verificationMethod": "did:web:issuer.example.com#key-1",
    "jws": "eyJhbGciOiJFUzI1NiIsImI2NCI6ZmFsc2UsImNyaXQiOlsiYjY0Il19..MEUCIQDKkn..."
  }
}
```

## Mandatory fields

```json
"@context": [
  "https://www.w3.org/2018/credentials/v1",
  "https://registry.gaia-x.eu/v2206/api/shape"  // ← CRITIC
]
```

```json
"type": [
  "VerifiableCredential",
  "LegalPerson"  // ← CRITIC: must be "LegalPerson"
]
```

```json
"credentialSchema": [{
  "id": "https://registry.gaia-x.eu/v2206/api/shape",  // ← CRITIC
  "type": "JsonSchemaValidator2018"
}]
```

```json
"credentialSubject": {
  "id": "did:web:localhost%3A7083",  // ← Participant DID
  "gx-participant:legalName": "...",  // ← Mandatory JSON field name
  "gx-participant:legalAddress": {  // ← Mandatory JSON field name
    "gx-participant:addressCountryCode": "..."  // ← Mandatory JSON field name
  }
}
```

# GAIA-X API (obtain context, shapes...)

https://gitlab.com/gaia-x/lab/credentials-events-service

https://registry.lab.gaia-x.eu/main/docs

https://gitlab.com/gaia-x/lab/compliance/gx-compliance

https://compliance.lab.gaia-x.eu/v1/docs/#/credential-offer/CommonController_issueVC

https://registry.lab.gaia-x.eu/main/docs#/Trusted-Shape-registry/TrustedShapeRegistry_getShape

https://registry.lab.gaia-x.eu/development/api/trusted-shape-registry/v1/shapes/jsonld/trustframework#
