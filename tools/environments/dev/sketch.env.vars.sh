export ENV=dev

# Secrets defined in `secrets/sketch.secrets.env.vars.sh`
# Also, see `docker-compose.yml` and `DockerDatabaseConfig.scala`

export DB_HOST=database
export DB_PORT=5432
export DB_NAME=sketch
export DB_USER=sketch.dev
