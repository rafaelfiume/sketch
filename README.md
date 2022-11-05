# sketch

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/rafaelfiume/sketch/tree/main.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/rafaelfiume/sketch/tree/main)

Just some useful commands to start with.

Most commonly used ones:

Sbt:
  `$ sbt scalafmtAll`
  `$ sbt scalafixAll`
  `$ sbt docker:publishLocal`

Acceptance tests tasks:
  `$ sbt acceptance/test`
  `$ sbt acceptance/scalafixAll`

Docker:
  `$ docker stop $(docker ps -a -q)`
  `$ docker rm $(docker ps -a -q)`
  `$ docker rmi <image>`
  `$ docker ps -a`
  `$ docker logs <container-id>`
  `$ docker exec -it <container-id> /bin/bash`
  `$ docker build -t sketch:dev .`
  `$ docker run --rm -p8080:8080 sketch:dev`
  `$ docker network prune`

## Workstation

This is a non-exhaustive list of tools you might need installed locally to work with this project:
 * JDK
 * Docker
 * [Docker Compose](https://docs.docker.com/compose/)
 * [Postman](https://www.postman.com/)