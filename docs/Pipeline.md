# Pipeline (& Infra)

Solid engineering practices aim for:
 * Determinism: same actions, same results
 * Specification: straightforward to learn, and reduced or no ambiguities
 * Speed: the quickest we do our job, the faster we can are free to focus on the other things that matter to us
 * Correction: preventing repetitive, boring manual tasks which are error prone
 * Criativity: save mental energy for non-repetitive interesting tasks.

Automation will give us all the essential properties described above.

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
│   ├── env-vars-loader.sh
├── infra/                                      # (Proposal) Infrastructure setup (scripts, terraform modules)
│   ├── ...
├── pipeline/                                   # Power the pipeline. See `.circleci/config.yaml`
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
├── users/                                      # Scripts to admin Sketch users
│   ├── ...
├── utilities/                                  # General utility functions
│   ├── logs.sh
│   └── ...
└── ...
```

## Pipeline & CircleCI

### Environment Variables

All the environment variables necessary to run Sketch should be defined in [../tools/environments/dev/](../tools/environments/dev/), unless:
 * You've just cloned `sketch` repository. In that case, you might want to refer to [first.read.this.txt](../tools/environments/dev/secrets/first.read.this.txt) to define the secrets Sketch stack depends on;
 * Sketch stack is running during the release pipeline (see session below).

#### Manually Configurable Environment Variables

There are a few environment variables that should be [manually configured when setting up the pipeline](https://app.circleci.com/settings/project/github/rafaelfiume/sketch/environment-variables?return-to=https%3A%2F%2Fapp.circleci.com%2Fpipelines%2Fgithub%2Frafaelfiume%2Fsketch): service secrets and authentication to 3rd party services.

Service Secrets:

 - `PRIVATE_KEY_BASE64_ONE_LINER` and `PUBLIC_KEY_BASE64_ONE_LINER`. Please, refer to `load_key_pair_from_pem_files_if_not_set` funtion in [../tools/pipeline/generate_one_liner_keys.sh](../tools/pipeline/generate_one_liner_keys.sh).

Authentication to 3rd Parties:

 - Docker: `DOCKER_LOGIN` and `DOCKER_PWD` are required so the pipeline has access to [Docker Hub](https://hub.docker.com/repository/docker/rafaelfiume/sketch/general).

...

# Useful Resources
 - [CircleCI - Workflows](https://circleci.com/docs/workflows/)
 - [Scripting Guidelines](artigiani/Scripting)


Feito com ❤️ por Artigiani.
