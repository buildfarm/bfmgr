# Buildfarm Manager

This repository hosts the [Buildfarm](https://github.com/bazelbuild/bazel-buildfarm) deployment and administration application.

### Quick Start

#### Local Testing

Make sure Docker is running

Create log directory

```
sudo mkdir /var/log/bfmgr && sudo chmod 0777 /var/log/bfmgr
```

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

Create a new IAM role

```

```

Launch a new EC2 instance (include above role)

Setup EC2 instance, download and run a binary release

```
yes | sudo yum install java
mkdir /var/log/bfmgr
chmod 0777 /var/log/bfmgr
rel=<REL NUMBER>
wget https://bfmgr.s3.amazonaws.com/bfmgr-$rel.jar
java -jar bfmgr-$rel.jar
```

Go to http://<INSTANCE_IP>:8080 and click Create.