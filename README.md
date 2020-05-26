# Buildfarm Manager

This repository hosts the [Buildfarm](https://github.com/bazelbuild/bazel-buildfarm) deployment and administration application.

### Quick Start

#### Local Testing

All commandline options override corresponding config settings.

```
./mvnw clean package && java -jar target/bfmgr-<REL NUMBER>.jar
```

Go to http://localhost:8080 and click Create.
