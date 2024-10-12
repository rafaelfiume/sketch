
## `dev` vs. `local` Environments

Use `dev` when running the stack.
Use `local` when triggering commands via scripts. For examples, see `acc-tests.sh`, `load-tests.sh`.

## Secrets

Secrets shouldn't be stored in a GitHub repository even if they are for `dev` or `local` environments
(i.e. workstation and release pipeline).
This is to decrease the possibility that secrets will eventually leak and pose a breach of security.

This is the list of files or evironment vars that are expected in a `secrets` directory in order to run the stack:
 * `private_key.pem` (see `docs/Authentication.md`)
 * `public_key.pem` (see `docs/Authentication.md`)
 * `DB_PASS` (for instace, in a `sketch.secrets.envs.vars.sh`)

You should use `EcKeyPairPemGenScript` in the `auth` module to generate both the private and publick keys pem files.
Just make sure to move them to this (`secrets`) directory.
