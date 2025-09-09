# Onboarding

You should have the all the basic develoment tools installed and be able to have the system running locally once you complete the steps in this doc.

**Table of Contents**

1. [From 0 to Your First Successful API Request](#1-from-0-to-your-first-successful-api-request)
    - 1.1 [Install Required Tools](#12-install-required-tools)
    - 1.2 [Clone the Repo](#11-clone-the-repo)
    - 1.3 [Run Tests & Other Basic Tasks with Sbt](#13-run-tests-and-other-basic-tasks-with-sbt)
    - 1.4 [Configure Secrets](#14-configure-secrets)
       - 1.4.1 [Environment Folders](#141-environment-folders)
       - 1.4.2 [Generate Jwt Signing Keys](#142-generate-jwt-signing-keys)
       - 1.4.3 [Required Secrets](#143-required-secrets)
    - 1.5 [Run the App on Your Machine](#15-run-the-app-on-your-machine)
       - 1.5.1 [Start the Stack](#151-start-the-stack)
       - 1.5.2 [Stop the Stack](#152-stop-the-stack)
       - 1.5.3 [Troubleshooting Your Local Dev Environment](#153-troubleshooting-your-local-dev-environment)
    - 1.6 [Send Your First Request](#16-send-your-first-request)
2. [Next Steps](#2-next-steps)
    - 2.1 [Run Acceptance Tests](#21-run-acceptance-tests)
    - 2.2 [Run Load Tests](#22-run-load-tests)
3. [Optional Steps](#3-optional-steps)
    - 3.1 [Install Visual Studio Code](#31-install-visual-studio-code)


## 1. From 0 to Your First Successful API Request

### 1.1 Install Required Tools
    
* [Git](https://git-scm.com/)
* [Docker](https://www.docker.com/) + [Docker Compose](https://docs.docker.com/compose/) + [Docker Desktop](https://docs.docker.com/desktop/setup/install/linux/ubuntu/)
* JDK (version: see `dockerBaseImage` in [build.sbt](../build.sbt))
* [coreutils](https://www.gnu.org/software/coreutils/) - required by scripts, e.g. [tools/pipeline/generate_one_liner_keys.sh](../tools/pipeline/generate_one_liner_keys.sh).

### 1.2 Clone the Repo

```bash
git clone https://github.com/rafaelfiume/sketch.git
```

**Optional - Gatling for Load Testing:**

You need to install [Gatling](https://docs.gatling.io/tutorials/scripting-intro/) and its [sbt plugin](https://docs.gatling.io/integrations/build-tools/sbt-plugin/) before running load tests.


### 1.3 Run Tests and Other Basic Tasks with Sbt

**Core Commands:**

| Command        | Purpose |
|----------------|---------|
| `test`         | Run **unit tests** across all modules |
| `it:test`      | Run **integration tests**             |

Code Quality:**

| Command         | Purpose |
|-----------------|---------|
| `scalafmtAll`   | Format the entire codebase |
| `scalafixAll`   | Run linter across all modules |

💡 Add a module prefix to limit scope. For example, `auth/test` runs only unit tests for the `auth` module.


### 1.4 Configure Secrets

To run the application or execute acceptance and load tests locally, you need to configure **secrets**.

⚠️ Secrets **must never be commited** to the repository.

See [Environments README](../../tools/environments/README.md) for details on `dev` vs. `local` environments and security guidelines.

#### 1.4.1 Environment Folders

Secretes are stored as environment variables in:

    `tools/environments/${dev|local}/secrets/${file_name}.sh`.

Developer environments are:
 - **local** - Used for acceptance and load tests against the stack running locally
 - **dev** - Used for running the application stack locally.

Scripts like [start-local.sh](../../tools/stack/start-local.sh) source the environment variable files automatically.

#### 1.4.2 Generate Jwt Signing Keys

1. Run `EcKeyPairPemGenScript` to create `private_key.pem` and `public_key.pem` (see Auth.md for details).
1. Move both files from [resources](../../auth/src/main/resources/) folder to `tools/environments/${local|dev}/secrets`.

#### 1.4.3 Required Secrets

Add the following environment variables to:

    `tools/environments/local/secrets/sketch.secrets.env.vars.sh`:

| Variable         | Purpose                                  | Example                           |
|------------------|------------------------------------------|-----------------------------------|
| `DB_PASS`        | Set the password for local database      | `mysecretpass`                    |
| `DOCKER_LOGIN`   | Authenticate in DockerHub                | `myuser`                          |
| `DOCKER_PWD`     | Authenticate in DockerHub                | `mypassword`                      |
| `PRIVATE_KEY`    | Used by the auth module to sign tokens   | Exported automatically by /.start-local.sh after completing [Section 1.4.2](#142-generate-keys) |
| `PUBLIC_KEY`     | Used by the auth module to verify tokens | Exported automatically by /.start-local.sh after completing [Section 1.4.2](#142-generate-keys) |


### 1.5 Run the App on Your Machine

#### 1.5.1 Start the stack:

```bash
./tools/stack/start-local.sh
```

If everything goes as expected, you should see the following messages:
```
> Checking 'sketch:latest' is healthy...
> All services have started up like a charm!
```

#### 1.5.2 Stop the stack:

```bash
./tools/stack/stop-local.sh
```

You should see:
```bash
> Services have stopped successfully. Have a good day!
```

#### 1.5.3 Troubleshooting Your Local Dev Environment

If you find problems starting the app or running tests, see the [Dev Environment Troubleshooting](Local-Troubleshooting.md) guide for common Docker commands, port checks and Sbt tips.

Check the [logs](../../tools/stack/logs/) directory for logs generated by Docker Composer, including the app, databases and other stack services.


### 1.6 Send Your First Request

⚡ Coming Soon: an improved Step-by-step guide to sending your first request.

  1. Open Postman - soon [Bruno](https://www.usebruno.com/)
  1. Import the collection: ...
  1. Pick one from the collection and fire a request to the server.


## 2. Next Steps

### 2.1 Run Acceptance Tests

Run all **acceptance tests** with:
```bash
tools/tests/acc-tests.sh
```

### 2.2 Run Load Tests

Run all **load tests** with:
```bash
tools/tests/load-tests.sh
```

To ensure code quality on acceptance and load tests:
```bash
testAcceptance/scalafmtAll  # formatting
testAcceptance/scalafixAll  # linter
```

## 3. Optional Steps

### 3.1 Install Visual Studio Code
  - [Visual Studio Code](https://code.visualstudio.com/)
  - [Scala Syntax (official)](https://marketplace.visualstudio.com/items?itemName=scala-lang.scala)
  - [Scala (Metals)](https://marketplace.visualstudio.com/items?itemName=scalameta.metals)
