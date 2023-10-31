export ENV=local

# Secrets defined in `secrets/sketch.secrets.env.vars.sh`
# Also, see `docker-compose.yml` and `DockerDatabaseConfig.scala`

export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=sketch
export DB_USER=sketch.dev #same db as 'dev'
