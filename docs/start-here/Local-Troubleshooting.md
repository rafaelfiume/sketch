
# Dev Environment Troubleshooting


**Table of Contents**
1.  [Docker Debugging](#1-docker-debugging)
2.  [Docker Cleanup Commands](#2-docker-cleanup-commands)
3.  [Docker Build & Run](#3-docker-build--run)
4.  [Ports and Network](#4-ports-and-network)
5.  [OS Process Management](#5-os-process-management)
6.  [Sbt Workflow](#6-sbt-workflow)
7.  [Other Useful Tools](#7-other-useful-tools)
    -   [Debug with `jq`](#debug-with-jq)


## 1. Docker Debugging

List all containers, running or stopped:
```bash
docker ps -a
```

View a specific container's logs:
```bash
docker logs <container-id>
```

Access a container shell:
```bash
docker exec -it <container-id> /bin/bash
```

See detailed container configuration and network info:
```bash
docker inspect <container-id>
```

List Docker networks:
```bash
docker network ls
```

Debug network issues:
```bash
docker network inspect <network-id>
```

Show disk usage by Docker objects (images, containers, volumes, caches):
```bash
docker system df
```

List Docker volumes:
```bash
docker volume ls
```

Show a Docker volume info:
```bash
docker volume inspect <volume-name>
```

## 2. Docker Cleanup Commands

Remove all stopped containers:
```bash
docker rm $(docker ps -a -q)
```

Remove all images:
```bash
docker rmi  $(docker images -a -q)
```

Remove all unused networks:
```bash
docker network prune
```

Remove all unused volumes:
```bash
docker volume prune
```

Cleanup unused containers, networks, images and intermediate layers:
```bash
docker system prune
```

## 3. Docker Build & Run

Build and publish the app's Docker image locally:
```bash
sbt docker:publishLocal
```

Manually build the image:
```bash
docker build -t sketch:dev .
```

Run the image directly, exposing por `8080`:
```bash
docker run --rm -p8080:8080 sketch:dev
```

Start only the database container:
```bash
docker compose -f tools/stack/docker-compose.yaml up sketch-database
```

Stop and remove containers created by docker-compose directly:
```bash
docker compose down
```

## 4. Ports and Network

Check network connectivity to a host:
```bash
ping <hostname>
```

Check if a port is already in use (for example, when the app can't bind to `8080`):
```bash
lsof -i tcp:8080
```

If you suspect Postgres issues:
```bash
lsof -nP -iTCP:5432 | grep LISTEN
```

View **all** open ports:
```bash
ss -tulpn
```

Debug DNS resolution:
```bash
dig <hostname>
nslookup <hostname>
```

## 5. OS Process Management

Find info about running processes, e.g. Java:
```bash
ps aux | grep java
```

Force-kill a process:
```bash
kill -9 <pid>
```

## 6. Sbt Workflow

Recompile while coding:
```bash
sbt ~compile
```

💡 Combine commands, for example, `sbt clean test it:test`.

## 7. Other Useful Tools

Debug environment variables:
```bash
env [| grep <KEY>]
```

Find previously typed command:
```bash
history [| grep <command>]
```

Check application status endpoint:
```bash
curl -v http://localhost:8080/status
```

Fetch only Http headers:
```bash
curl -I http://localhost:8080
```

#### Debug with `jq`

⚡ Coming Soon: a real-world debug scenario with jq.

For example, to pretty-print a json response:
```bash
curl -s http://localhost:8080/status | jq
```

To extract Docker container IP:
```bash
docker inspect <container-id> | jq '.[0].NetworkSettings.IPAddress'
```
