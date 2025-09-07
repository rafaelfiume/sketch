
# `dev` vs. `local` and Secrets

## Development Environments Overview

| Environment | Purpose                                     | Example                                |
|-------------|---------------------------------------------|----------------------------------------|
| **`dev`**   | Running the full stack locally              | Starting services via `start-local.sh` |
| **`local`** | Running tests that interact with the stack  | `acc-tests.sh`, `load-tests.sh`        |

## Secrets

Secrets **must never be committed** to a repository, even for `dev` or `local` environments.
This prevents accidental leaks and security breaches.

Instead, store secrets as environment variables in:

    `tools/environments/${dev|local}/secrets/${file_name}.sh`.

These files are automatically **sourced by scripts**, such as [start-local.sh](../stack/start-local.sh), during execution.

Example file:

    `tools/environments/local/secrets/sketch.secrets.env.vars.sh`

```bash
DB_PASS=mysecretpass
DOCKER_LOGIN=myuser
DOCKER_PWD=mypassword
```

See the [Onboarding](../../docs/start-here/Onboarding.md) for a full list of required secrets.
