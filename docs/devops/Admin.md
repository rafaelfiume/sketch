# Admin Processes (The 12th Factor)

The 12-Factors [Admin Processes principle](https://12factor.net/admin-processes) says:

   _"Run admin/management tasks as one-off processes in an identical environment as the regular long-running processes of the app"_.


## 1. Goals

The scripts that perform the admin/management tasks should:
 * Automate operational tasks
 * Run as a one-off process - executed once needed
 * Lives the same codebase - so it is always in sync with the current version of the app
 * Run in the same execution environment as the app (dev, prod, etc.).

---

**Benefits:**

  * Repeatability: works in the same way regardless of the environment
  * Versioned: tooling evolves in sync with the codebase, avoiding drift
  * Single source of truth: no tribal knowledge
  * Auditable: changes can be reviewed like the rest of the code
  * Automatable: can be run as part of the e.g. CD pipeline.


## 2. Authentication Ops

| Name | Path | Description | How to Execute |
|------|------|-------------|----------------|
| **Create User Account** | [create-user-account.sh](../../tools/admin/users/create-user-account.sh) | Adds a new user account to the system. | From terminal: <br>```./tools/admin/users/create-user-account.sh alice supersecret<br>``` |
| **EC Key Pair PEM Generator** | [EcKeyPairPemGenScript.scala](../../auth/src/main/scala/org/fiume/sketch/auth/scripts/EcKeyPairPemGenScript.scala) | Generates an Elliptic Curve key pair in PEM format for JWT signing. Must be run for each environment (dev, staging, prod) to securely provision keys. | **Option 1: sbt** <br>```bash<br>sbt "auth/runMain org.fiume.sketch.auth.scripts.EcKeyPairPemGenScript"<br>```<br>**Option 2: IDE** <br>Run the `EcKeyPairPemGenScript` object directly from IntelliJ or another Scala IDE. |
