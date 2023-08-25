# Releases

## App Versioning

App Version should conform to the following rules: 
1) Use `${circleci_build_number}` build number when branch is `main`
1) Use `${branch.name}.${circleci_build_number}` when in a branch other than `main`
1) Use `snapshot` when building it locally. 

For instance: version `105` (main), `branch.105` (feature branch) or `snapshot` (local).

The docker image tag should correspond to the App Version.

Note that this logic has been implemented both in `tools/scripts/version.sh` and `build.sbt`. The latter is necessary so `sbt-native-packager` can properly tag the docker image created with `docker:publishLocal` or `docker:publish`.