# Release Pipeline (& Infra)

## Pipeline & CircleCI

### Environment Variables

All the environment variables necessary to run Sketch should be defined in [../tools/environments/dev/](../tools/environments/dev/), unless:
 * You've just cloned `sketch` repository. In that case, you might want to refer to [these instructions](../tools/environments/z.read.this.first.md) to define the secrets Sketch stack depends on;
 * Sketch stack is running during the release pipeline (see session below).

#### Manually Configurable Environment Variables

There are a few environment variables that should be [manually configured when setting up the pipeline](https://app.circleci.com/settings/project/github/rafaelfiume/sketch/environment-variables?return-to=https%3A%2F%2Fapp.circleci.com%2Fpipelines%2Fgithub%2Frafaelfiume%2Fsketch) and running scripts in the [tools](../tools) directory.

Service Secrets:

 - `PRIVATE_KEY_BASE64_ONE_LINER` and `PUBLIC_KEY_BASE64_ONE_LINER`

 Please, refer to `load_key_pair_from_pem_files_if_not_set` funtion in [../tools/pipeline/generate_one_liner_keys.sh](../tools/pipeline/generate_one_liner_keys.sh).

Authentication to 3rd Parties:

 - `DOCKER_LOGIN` and `DOCKER_PWD`

 Required so the pipeline and scripts have access to [Docker Hub](https://hub.docker.com/repository/docker/rafaelfiume/sketch/general).

## Pipeline (& Infra coming soon) Tools Directory Structure

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

# Useful Resources
 - [CircleCI - Workflows](https://circleci.com/docs/workflows/)
 - [Scripting Guidelines](artigiani/Scripting)


Feito com ❤️ por Artigiani.
