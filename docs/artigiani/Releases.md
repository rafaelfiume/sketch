# Release Guidelines

Solid engineering practices aim for:
 * Determinism: same actions, same results
 * Specification: straightforward to learn, and reduced or no ambiguities
 * Speed: the quickest we do our job, the faster we can are free to focus on the other things that matter to us
 * Correction: preventing repetitive, boring manual tasks which are error prone
 * Criativity: save mental energy for non-repetitive interesting tasks.

A solid release pipeline will point us towards the above essential properties above.

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
