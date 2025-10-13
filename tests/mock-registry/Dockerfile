FROM python:3.11-slim
WORKDIR /app
RUN pip install --no-cache-dir fastapi uvicorn[standard]
RUN mkdir -p /app/contexts
COPY mock-registry.py /app/
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8080/health')"
CMD ["python", "mock-registry.py"]


#FROM python:3.11-slim
#
#WORKDIR /app
#
## Instala FastAPI y Uvicorn
#RUN pip install --no-cache-dir fastapi uvicorn[standard]
#
## Crear directorio para contextos
#RUN mkdir -p /app/contexts
#
## Copiar el código de la app
#COPY mock-registry.py /app/
#
## Crear certificados auto-firmados
#RUN apt-get update && apt-get install -y openssl && \
#    openssl req -x509 -nodes -days 365 \
#        -newkey rsa:2048 \
#        -keyout /app/server.key \
#        -out /app/server.crt \
#        -subj "/CN=registry.lab.gaia-x.eu"
#
## Exponer HTTPS
#EXPOSE 443
#
## Healthcheck
#HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
#    CMD python -c "import urllib.request; urllib.request.urlopen('https://localhost/health')"
#
## Ejecutar Uvicorn con HTTPS
#CMD ["uvicorn", "mock-registry:app", "--host", "0.0.0.0", "--port", "443", "--ssl-keyfile", "/app/server.key", "--ssl-certfile", "/app/server.crt"]
