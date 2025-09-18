# Release Guidelines

**Table of Contents**

1. [Goals](#1-goals)
2. [Promotable `main`](#2-promotable-main)
3. [Application Versioning](#3-application-versioning)
    - 3.1 [Docker Image Tags](#31-docker-image-tags)
4. [CI Pipeline](#4-ci-pipeline)
    - 4.1 [Environment Variables & Secrests](#41-environment-variables--secrets)
    - 4.2 [Tools for Reliable Automation](#42-tools-for-reliable-automation)
5. [Further Reading](#5-further-reading)


## 1. Goals

The release process produces immutable, versioned, promotable artifacts. It must be:

* **Reliable**: Automation removes repetitive and error-prone steps, reducing mistakes
* **Deterministic**: Same actions, same results
* **Fast**: Short release cycles encourage frequent relases and provide quick feedback.


## 2. Promotable `main`

* **`main` is always production-ready**: Every commit to main must be promotable to production
* **No separate QA or staging environments**: All validation must happen through automated checks during the CI build
* **Environment Parity**: The stack must run identically on a developer machine, CI or production.


## 3. Application Versioning

| Context         | Version Format                             | Example                   |
|-----------------|--------------------------------------------|---------------------------|
| `main` branch   | `${circleci_build_number}`                 | `105`                     |
| Local build     | `snapshot`                                 | `snapshot`                |
| Feature branch  | `${branch.name}.${circleci_build_number}`  | `sign.jwt.105`            |

> **Note:** This logic exists in both [version.sh](/tools/pipeline/version.sh) and [build.sbt](/build.sbt), with `build.sbt` required for Docker image tagging with `docker:publishLocal` or `docker:publish` via `sbt-native-packager`.

### 3.1 Docker Image Tags

| Tag                        | Description                              | Purpose                          |
|----------------------------|------------------------------------------|----------------------------------|
| `stable`                   | Latest successful build on `main`        | Production-ready releases        |
| `latest`                   | Latest successful build from any branch  | Development and testing          |
| `${circleci_build_number}` | Successful build corresponding to a specific CI run | Reverting releases to exact previous state |


## 4. CI Pipeline

### 4.1 Environment Variables & Secrets

The [tools/environments/dev/](/tools/environments/dev/) directory is the source of truth for environment configuration, for both local development and the CI pipeline:

  * **Configuration values** are version-controlled and automatically loaded
  * **Secrets** are not stored in Git. They must be set in the [CircleCI project settings](https://app.circleci.com/settings/project/github/rafaelfiume/sketch/environment-variables?return-to=https%3A%2F%2Fapp.circleci.com%2Fpipelines%2Fgithub%2Frafaelfiume%2Fsketch).

> **Note:** For initial local setup, follow the [Onboarding](/docs/start-here/Onboarding.md) guide.

**CircleCI-specific variables:**

| Variable                         | Purpose                                        |
|----------------------------------|------------------------------------------------|
| `PRIVATE_KEY_BASE64_ONE_LINER`   | Base64 version of `PRIVATE_KEY` compatible with CircleCI |
| `PUBLIC_KEY_BASE64_ONE_LINER`    | Base64 version of `PUBLIC_KEY` compatible with CircleCI  |

These are generated from `PRIVATE_KEY` and `PUBLIC_KEY` using [generate_one_liner_keys.sh](/tools/pipeline/generate_one_liner_keys.sh) script.

> For the **full list of required secrets**, see [Onboarding - Required Secrets](/docs/start-here/Onboarding.md#143-required-secrets).


### 4.2 Tools for Reliable Automation

The `tools` directory organises scripts and configuration that power automated, deterministic builds.

It is designed around the following principles:

  * **Portability**: Works the same locally and in CI
  * **Functional Structure**: Scripts grouped by functionality (`environments`, `pipeline`, `stack`), not technology
  * **Composability**: Complex scripts are composed from smaller and reusable scripts, e.g. defined in `utilities/`
  * **Self-Documentation**
      - **Path == Domain**, e.g. [tools/pipeline/docker/](/tools/pipeline/docker/)
      - **File Name == Intent**, e.g. [publish-image.sh](/tools/pipeline/docker/publish-image.sh)
      - **Code == Executable documentation**.

```
tools/
├── deploy/                                     # (Future) Deployment scripts for prod
├── environments/dev/                           # Environment configuration and secrets
├── pipeline/                                   # CI automation scripts (versioning, docker image tagging, publishing)
├── stack/                                      # Application stack management (start/stop)
├── tests/                                      # Acceptance and load testing scripts
├── users/                                      # Admin scripts for user account management (run ad-hoc, not CI related)
├── utilities/                                  # Shared shell function library (logging, env vars, etc.)
```


## 5. Further Reading

* [Project CI Configuration](/.circleci/config.yml)
* [CircleCI - Workflows](https://circleci.com/docs/workflows/)
* [Scripting Guidelines](Scripting.md)


Feito com ❤️.
