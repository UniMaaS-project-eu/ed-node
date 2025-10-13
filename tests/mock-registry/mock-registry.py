import os
from fastapi import FastAPI, Response
from fastapi.responses import JSONResponse
import uvicorn
import json
from pathlib import Path

app = FastAPI(title="GAIA-X Mock Registry")

CONTEXT_URL = os.environ.get("CONTEXT_URL", "http://localhost:8080/main/context/2411")

# Rutas a los archivos de contexto
CONTEXT_2206_PATH = Path("/app/contexts/gaiax-context-2206.jsonld")
CONTEXT_2411_PATH = Path("/app/contexts/gaiax-context-2411.jsonld")

def load_context(file_path: Path):
    """Carga un archivo JSON-LD de contexto"""
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            return json.load(f)
    except FileNotFoundError:
        return {
            "@context": {
                "gx": CONTEXT_URL + "#",
                "gx-participant": CONTEXT_URL + "#"
            }
        }

@app.get("/v2206/api/shape")
async def get_context_2206():
    """Endpoint para GAIA-X 22.06 context"""
    context = load_context(CONTEXT_2206_PATH)
    return JSONResponse(
        content=context,
        media_type="application/ld+json",
        headers={
            "Access-Control-Allow-Origin": "*",
            "Cache-Control": "public, max-age=86400"
        }
    )

@app.get("/main/context/2411")
async def get_context_2411():
    """Endpoint para GAIA-X 24.11 context"""
    context = load_context(CONTEXT_2411_PATH)
    return JSONResponse(
        content=context,
        media_type="application/ld+json",
        headers={
            "Access-Control-Allow-Origin": "*",
            "Cache-Control": "public, max-age=86400"
        }
    )

@app.get("/health")
async def health_check():
    """Health check endpoint"""
    return {
        "status": "healthy",
        "contexts": {
            "2206": CONTEXT_2206_PATH.exists(),
            "2411": CONTEXT_2411_PATH.exists()
        }
    }

@app.get("/")
async def root():
    """Root endpoint con información del servicio"""
    return {
        "service": "GAIA-X Mock Registry",
        "endpoints": {
            "gaiax_2206": "/v2206/api/shape",
            "gaiax_2411": "/main/context/2411",
            "health": "/health"
        }
    }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8080)

#if __name__ == "__main__":
#    uvicorn.run(
#        app,
#        host="0.0.0.0",
#        port=443,
#        ssl_certfile="/app/server.crt",
#        ssl_keyfile="/app/server.key"
#    )