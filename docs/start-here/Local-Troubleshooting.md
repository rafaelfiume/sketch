
# Dev Environment Troubleshooting


**Table of Contents**

1. [Docker Debugging](#1-docker-debugging)
2. [Docker Cleanup Commands](#2-docker-cleanup-commands)
3. [Docker Build & Run](#3-docker-build--run)
4. [Ports and Network](#4-ports-and-network)
5. [OS Process Management](#5-os-process-management)
6. [Sbt Workflow](#6-sbt-workflow)
7. [Setting Environment Variables](#7-setting-environment-variables)
8. [Other Useful Tools](#8-other-useful-tools)
    - [Debug with `jq`](#debug-with-jq)


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


## 7. Setting Environment Variables

This section explains the different ways of setting environment variables and their tradeoffs using `JAVA_HOME` as example.

The examples below focus on Linux. Similar concepts apply to macOS and Windows, though tools and paths differ.

| Location                  | Loads for                | Scope    | Notes                         |
|---------------------------|--------------------------|----------|-------------------------------|
| `~/.bashrc` or `~/.zshrc` | **Shell sessions** only  | Users    | Start GUI apps from a terminal to inherit these variables |
| `/etc/profile.d/java.sh`  | **Shell and GUI apps**   | System   | - Clean and modular approach<br> - Effective after login<br> - The recommended approach |
| `/etc/environment`        | **Shell and GUI apps**   | System   | - Misconfigurations can prevent login<br> - Use `VARIABLE=value` syntax (no `export`) |

#### Before Setting the Variable: Find the Path

```bash
# On Linux,`update-alternatives` is a reliable way to find installed JDK and other binaries
update-alternatives --list java

# Example output:
# /usr/lib/jvm/java-21-openjdk-amd64/bin/java

# To set JAVA_HOME, use the parent directory:    
# /usr/lib/jvm/java-21-openjdk-amd64
```

#### Option 1: `~/.bashrc` (or `~/.zshrc`)

Use this when you need the variable in your own shell sessions, or for GUI applications launched from a terminal.

```bash
# Open your shell config file
nano ~/.bashrc
# nano ~/.zshrc   # For zsh users

# Add these lines at the end of the file
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Save and exit

# Reload the shell
source ~/.bashrc
```

#### Option 2: `/etc/profile.d/java.sh`

For when you need a configuration that works for both shell sessions and GUI apps.

```bash
# Create a new profile script
sudo nano /etc/profile.d/java.sh

# Add the following lines:
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH

# Save and exit

# Make the changes effective by either:
# * Start a new shell session with -l (login) flag: `bash -l`
# * Logging out and back in to make changes available system-wide
```

#### After Setting the Variable: Verification

```bash
echo $JAVA_HOME
java -version
which java
```

## 8. Other Useful Tools

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
