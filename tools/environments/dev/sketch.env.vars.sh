export ENV=dev

# Secrets defined in `secrets/sketch.secrets.env.vars.sh`
# Also, see `docker-compose.yml` and `DockerDatabaseConfig.scala`

export DB_URL=jdbc:postgresql://database/sketch
export DB_USER=sketch.dev
