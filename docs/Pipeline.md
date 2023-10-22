# Pipeline (& Infra) & Stack

Solid engineering practices aim for:
 * Determinism: same actions, same results
 * Specification: straightforward to learn, reduced or no ambiguities
 * Speed: quickest we do our job, the faster we can do the other things that matter to us in our short lives
 * Correction: repetitive, boring manual tasks are error prone
 * Criativity: spend time with interesting tasks, and avoid the repetitive boring ones.

Automation will give us all the essential properties described above.

## Pipeline (& Infra coming soon) Tools Directory Structure

```
tools/
├── infra/                                      # (Proposal) Infrastructure setup (scripts, terraform modules)
│   ├── ...
├── pipeline/                                   # Power the pipeline. See `.circleci/config.yaml`
│   ├── version.sh
│   ├── deploy.sh                               # (Proposal) Deployment script for the pipeline
│   └── ...
├── stack/                                      # All related to start/stop sketch stack locally or during release pipeline
│   ├── environment/
│   │   ├── dev/
│   │   │   ├── secrets/                        # See `.gitginore`
│   |   |   |   ├── private_key.pem
│   |   |   |   ├── public_key.pem
│   │   │   |   └── ...
│   │   │   └── ...
│   │   ├── env-vars-loader.sh
│   ├── logs/                                   # Log output from the scripts in the 'stacks' directory.
│   |   └── ...                                 # See `.gitginore`
│   ├── docker-compose.yml
│   ├── start-local.sh
│   └── stop-local.sh
├── utilities/                                  # General utility functions
│   ├── logs.sh
│   └── ...
└── ...
```

## Pipeline & CircleCI

### Environment Variables

All the environment variables necessary to run Sketch should be defined in [../tools/stack/environment/dev/](../tools/stack/environment/dev/), unless:
 * You've just cloned `sketch` repository. In that case, you might want to refer to [first.read.this.txt](../tools/stack/environment/dev/secrets/first.read.this.txt) to define the secrets Sketch stack depends on;
 * Sketch stack is running during the release pipeline.

There are a few environment variables that should be [manually configured when setting up the pipeline](https://app.circleci.com/settings/project/github/rafaelfiume/sketch/environment-variables?return-to=https%3A%2F%2Fapp.circleci.com%2Fpipelines%2Fgithub%2Frafaelfiume%2Fsketch): service secrets and authentication to 3rd party services.

#### Service Secrets

 - `PRIVATE_KEY_BASE64_ONE_LINER` and `PUBLIC_KEY_BASE64_ONE_LINER`. Please, refer to `load_keys_pair_from_pem_files_if_not_set` funtion in [../tools/stack/environment/env-vars-loader.sh](../tools/stack/environment/env-vars-loader.sh) file for more information.

#### Authentication to 3rd Parties

 - Docker: `DOCKER_LOGIN` and `DOCKER_PWD` are required so the pipeline has access to [Docker Hub](https://hub.docker.com/repository/docker/rafaelfiume/sketch/general).

...

# Useful Resources
 - [CircleCI - Workflows](https://circleci.com/docs/workflows/)
 - [Scripting Guidelines](artigiani/Scripting)


Feito com ❤️ por Artigiani.
