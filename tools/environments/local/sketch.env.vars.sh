export ENV=local

# Secrets defined in `secrets/sketch.secrets.env.vars.sh`
# Also, see `docker-compose.yml` and `DockerDatabaseConfig.scala`

export DB_URL=jdbc:postgresql://localhost:5432/sketch
export DB_USER=sketch.dev #same db as 'dev'
