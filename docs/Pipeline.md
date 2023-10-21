# Pipeline (& Infra) & Stack

Solid engineering practices aim for:
 * Determinism: same actions, same results
 * Specification: straightforward to learn, reduced or no ambiguities
 * Speed: quickest we do our job, the faster we can do the other things that matter to us in our short lives
 * Correction: repetitive, boring manual tasks are error prone
 * Criativity: spend time with interesting tasks, and avoid the repetitive boring ones.

Automation will give us all the essential properties described above.

```
tools/
├── infra/                                      # (Proposal) Infrastructure setup (scripts, terraform modules)
│   ├── ...
├── pipeline/                                   # Powers the pipeline. See also ${PROJECT_DIR}/.circleci/config.yaml
│   ├── version.sh
│   ├── deploy.sh                               # (Proposal) Deployment script for the pipeline
│   └── ...
├── stack/                                      # All related to start/stop sketch stack locally or during release pipeline
│   ├── environment/
│   │   ├── dev/
│   │   │   ├── secrets/                        # Must not be stored in the project repository. See `${PROJECT_DIR}/.gitginore`
│   |   |   |   ├── private_key.pem
│   |   |   |   ├── public_key.pem
│   │   │   └── ...
│   │   ├── load-keys-pair-if-not-set.sh
│   ├── logs/                                  # Logs output from the scripts in the 'stacks' directory. See `${PROJECT_DIR}/.gitginore`
│   |   └── ...
│   ├── docker-compose.yml
│   ├── start-local.sh
│   └── stop-local.sh
├── utilities/                                  # General utility functions
│   ├── logs.sh
│   └── ...
└── ...
```

## Useful Resources
 - [CircleCI - Workflows](https://circleci.com/docs/workflows/)
 - [Scripting Guidelines]


Feito com ❤️ por Artigiani.
