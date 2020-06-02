# Buildfarm Manager

This repository hosts the [Buildfarm](https://github.com/bazelbuild/bazel-buildfarm) deployment and administration application.

### Quick Start

#### Local Testing

Build and run from source

```
./mvnw clean package && java -jar target/bfmgr-<REL NUMBER>.jar
```

Download and run a binary release

```
rel=<REL NUMBER>
wget https://github.com/80degreeswest/bfmgr/releases/download/$rel/bfmgr-$rel.jar
java -jar target/bfmgr-$rel.jar
```

Go to http://localhost:8080 and click Create.

#### Deploying in AWS

Prerequisites:

Create a new IAM role with the following permissions

```

```

Download and run a binary release

```
rel=<REL NUMBER>
wget https://github.com/80degreeswest/bfmgr/releases/download/$rel/bfmgr-$rel.jar
java -jar target/bfmgr-$rel.jar
```

Go to http://<INSTANCE_IP>:8080 and click Create.