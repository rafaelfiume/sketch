# Release Guidelines

## Goals

 * Determinism: same actions, same results
 * Specification: straightforward to learn, and reduced or no ambiguities
 * Speed: the quickest we do our job, the faster we can are free to focus on the other things that matter to us
 * Correction: preventing repetitive, boring manual tasks which are error prone
 * Criativity: save mental energy for non-repetitive interesting tasks.


## App Semantic Versioning Rules

App Version should conform to the following rules:
1) Use `${circleci_build_number}` when branch is `main`
1) Use `${branch.name}.${circleci_build_number}` when in a branch other than `main`
1) Use `snapshot` when building it locally.

For instance: version `105` (main), `branch.105` (feature branch) or `snapshot` (local).

Note about Scala services: the above logic has been implemented both in [version.sh](/tools/pipeline/version.sh) and [build.sbt](/build.sbt). The latter is necessary so `sbt-native-packager` can properly tag the docker image created with `docker:publishLocal` or `docker:publish`.

## Docker Image Tags

A Docker image tag should correspond to the App Version:

```
  docker-image-tag <==> app-version
```

Additionally, `stable` and `latest` tags should always exist.

### `stable` and `latest` Image Tags

A `stable` tag should be defined and point to the latest stable release, which must correspond to the latest commit in the `main` branch.
This is useful for whenever we want to ensure we are running the latest stable version of the service.

A `latest` tag should be defined and point to the latest build, including snapshots on a workstation.
This is useful in development mode when we want to work with the most recent version of a service.

## On Continuous Delivery, Trunk-based Development and Feature-branchs

1) `main` <==> `production` <==> `live`

Code in `main` should be the same as code live always.

1) There can only be a `production` environment besides development (`dev` and `local`).

In other words, no stagging, no QA environments. Our engineering practices and pipeline must ensure that any changes commited to `main` works as expected.

1) Stack should always be runnable anywhere

A service and the whole stack should be runnable anywhere, including:
 - pipeline
 - local workstation.

This will allow for improved developer ergonomics, fast feedback - by e.g. running load tests before commiting changes -, prototyping and more.


## Pipeline & CircleCI

### Environment Variables

All the environment variables necessary to run Sketch should be defined in [../tools/environments/dev/](../tools/environments/dev/), unless:
 * You've just cloned `sketch` repository. In that case, you might want to refer to [these instructions](../tools/environments/z.read.this.first.md) to define the secrets Sketch stack depends on;

**Manually Configurable Environment Variables:**

There are a few environment variables that should be [manually configured when setting up the pipeline](https://app.circleci.com/settings/project/github/rafaelfiume/sketch/environment-variables?return-to=https%3A%2F%2Fapp.circleci.com%2Fpipelines%2Fgithub%2Frafaelfiume%2Fsketch) and running scripts in the [tools](../tools) directory.

Service Secrets:

 - `PRIVATE_KEY_BASE64_ONE_LINER` and `PUBLIC_KEY_BASE64_ONE_LINER`

 Please, refer to `load_key_pair_from_pem_files_if_not_set` funtion in [../tools/pipeline/generate_one_liner_keys.sh](../tools/pipeline/generate_one_liner_keys.sh).

Authentication to 3rd Parties:

 - `DOCKER_LOGIN` and `DOCKER_PWD`

 Required so the pipeline and scripts have access to [Docker Hub](https://hub.docker.com/repository/docker/rafaelfiume/sketch/general).


### Pipeline Tools Directory Structure

```
tools/
├── environments/
│   ├── dev/
│   │   ├── secrets/                           # See `.gitginore`
|   |   |   ├── private_key.pem
|   |   |   ├── public_key.pem
│   │   |   └── ...
│   │   └── ...
├── infra/                                      # (Proposal) Infrastructure setup (scripts, terraform modules)
│   ├── ...
├── pipeline/                                   # Power the pipeline. See `.circleci/config.yaml`
│   ├── docker/                                 # Docker related scripts
│   |   └── ...                                 # See `.gitginore`
│   ├── version.sh
│   ├── deploy.sh                               # (Proposal) Deployment script for the pipeline
│   └── ...
├── stack/                                      # All related to start/stop sketch stack locally or during release pipeline
│   ├── logs/                                   # Log output from the scripts in the 'stacks' directory.
│   |   └── ...                                 # See `.gitginore`
│   ├── docker-compose.yml
│   ├── start-local.sh
│   └── stop-local.sh
├── tests/                                      # Scripts to run tests
│   ├── ...
├── users/                                      # Scripts to admin service users
│   ├── ...
├── utilities/                                  # General utility functions
│   ├── env-vars-loader.sh
│   ├── logs.sh
│   └── ...
└── ...
```

## Useful Resources
 - [CircleCI - Workflows](https://circleci.com/docs/workflows/)


Feito com ❤️.
