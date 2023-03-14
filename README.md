# sketch

Just some useful commands to start with.

Most commonly used ones:

Sbt:
- `$ sbt scalafmtAll`
- `$ sbt scalafixAll`
- `$ sbt docker:publishLocal`

Acceptance tests tasks:
- `$ sbt testAcceptance/test`
- `$ sbt testAcceptance/scalafixAll`

Docker:
 - `$ docker stop $(docker ps -a -q)`
 - `$ docker rm $(docker ps -a -q)`
 - `$ docker rmi $(docker images -a -q)`
 - `$ docker ps -a`
 - `$ docker logs <container-id>`
 - `$ docker exec -it <container-id> /bin/bash`
 - `$ docker build -t sketch:dev .`
 - `$ docker run --rm -p8080:8080 sketch:dev`
 - `$ docker network prune`

## Pipeline
 - [GitHub](https://github.com/rafaelfiume/sketch)
 - [CircleCI](https://app.circleci.com/pipelines/github/rafaelfiume/sketch)
 - [Docker Hub](https://hub.docker.com/repository/docker/rafaelfiume/sketch/tags?page=1&ordering=last_updated)

## Workstation

This is a non-exhaustive list of tools you might need installed locally to work with this project:
- JDK
- Docker
- [Docker Compose](https://docs.docker.com/compose/)
- [Postman](https://www.postman.com/)