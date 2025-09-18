# Scripting Guidelines

Scripts are foundational components of this project infrastructure. They go beyond automating common tasks by working as:

 * **Scaffolding of complex workflows**, such as CI/CD pipelines
 * **Executable documentation**
 * **Formalised admin processes** (see 12-Factor: [Admin Processes](/docs/devops/Admin.md))


**Table of Contents**

1. [Project Practices](#1-project-practices)
    - 1.1 [`stdout` vs. `stderr` Streams](#11-stdout-vs-stderr-streams)
    - 1.2 [Context-Aware Logging](#12-context-aware-logging)
2. [Further Reading](#2-further-reading)


## 1. Project Practices

The following project-specific practices complement the comprehensive guidelines found in the [Futher Reading](#2-further-reading) section.

### 1.1 `stdout` vs. `stderr` Streams

Scripts must strictly separate machine-parsable outputs from human-readble messages.

Run the following command to verify that the script sends:
  * Intended output to  `output.txt` (`stdout`)
  * Logs to `errors.txt`  (`stderr`).

```
$ ./your_script.sh > output.txt 2> errors.txt
```

### 1.2 Context-Aware Logging

Scripts log verbosity must match the environment to balance debuggability and clarity:

| Environment       | Max Log Level         | Rationale                                        |
|-------------------|-----------------------|--------------------------------------------------|
| Prod              | `INFO`                | High-level execution flow only                   |
| Development       | `DEBUG` or `TRACE`    | Full details for development and troubleshooting |

---

The `debug` and `trace` levels are disabled by default. Enable them explicitly with flags:

```
# Example: enabling trace-level logging
$ ./tools/stack/stop-local.sh --trace
```

> See the shared [log.sh](/tools/utilities/logs.sh) utility for more details.


## 2. Further Reading

For essential principles and best-practices for creating effective CLI scripts, refer to:

 * [12 Factor CLI Apps](https://medium.com/@jdxcode/12-factor-cli-apps-dd3c227a0e46): Design guidelines for useful and intuitive CLI tools
 * [Bash best practices](https://bertvv.github.io/cheat-sheets/Bash.html): Best practices for writing maintainable and robust scripts
 * [Minimal Safe Bash Script Template](https://betterdev.blog/minimal-safe-bash-script-template/): A battle-tested template to jumpstart new scripts
