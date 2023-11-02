export ENV=dev

# Secrets defined in `secrets/sketch.secrets.env.vars.sh`
# Also, see `docker-compose.yml` and `DockerDatabaseConfig.scala`

export HTTP_SERVER_PORT=8080
export HTTP_REQ_RES_LOG_ENABLED=true

export DOCUMENT_MB_SIZE_LIMIT=30

export DB_HOST=database
export DB_PORT=5432
export DB_NAME=sketch
export DB_USER=sketch.dev
