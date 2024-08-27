# Workspace

## Required Tools

- [coreutils](https://www.gnu.org/software/coreutils/) - see [tools/pipeline/generate_one_liner_keys.sh](../tools/pipeline/generate_one_liner_keys.sh)
- JDK (version: see `dockerBaseImage` in [build.sbt](../build.sbt))
- [Docker](https://www.docker.com/)
- [Docker Compose](https://docs.docker.com/compose/)
- [Gatling](https://docs.gatling.io/tutorials/scripting-intro/)
- [Git](https://git-scm.com/)
- [Postman](https://www.postman.com/) as a convenient way of documenting and sending requests to Sketch endpoints. Just import its [collection](Sketch.postman_collection.json).


## Recomended Tools

- [Scala Syntax (official)](https://marketplace.visualstudio.com/items?itemName=scala-lang.scala)
- [Scala (Metals)](https://marketplace.visualstudio.com/items?itemName=scalameta.metals)
- [Visual Studio Code](https://code.visualstudio.com/)

## Useful Commands

#### sbt:

- `sbt scalafmtAll`
- `sbt scalafixAll`
- `sbt docker:publishLocal`

#### Acceptance tests:

- `sbt testAcceptance/test`
- `sbt testAcceptance/scalafixAll`
- `sbt testAcceptance/Gatling/test`

#### Scripts:

 `./tools/stack/start-local.sh`
 `./tools/stack/stop-local.sh`

#### Docker:

 - `docker build -t sketch:dev .`
 - `docker run --rm -p8080:8080 sketch:dev`
 - `docker stop $(docker ps -a -q)`
 - `docker rm $(docker ps -a -q)`
 - `docker rmi $(docker images -a -q)`
 - `docker ps -a`
 - `docker logs <container-id>`
 - `docker exec -it <container-id> /bin/bash`
 - `docker network prune`

Check postgres connection with: `lsof -nP -iTCP:5433 | grep LISTEN`


Feito com ❤️ por Artigiani.
