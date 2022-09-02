# sketch

Just some useful commands to start with.

Most commonly used ones:

Sbt:
`$ sbt scalafmtAll`
`$ sbt service/run`

CI/Pipeline:
`$ sbt docker:publishLocal`

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
