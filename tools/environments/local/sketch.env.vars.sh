export ENV=local

# Secrets defined in `secrets/sketch.secrets.env.vars.sh`
# Also, see `docker-compose.yml` and `DockerDatabaseConfig.scala`

export HTTP_SERVER_PORT=8080
export HTTP_REQ_RES_LOG_ENABLED=true
export HTTP_CORS_ALLOWS_ORIGIN="http://localhost:5173|http://localhost:8181"

export DOCUMENT_MB_SIZE_LIMIT=35

export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=sketch
export DB_USER=sketch.dev #same db as 'dev'
