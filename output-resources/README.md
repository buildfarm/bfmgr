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
rel=${version}
wget https://github.com/80degreeswest/bfmgr/releases/download/$rel/bfmgr-$rel.jar
java -jar target/bfmgr-$rel.jar
```

Go to http://localhost and click Create

#### Deploying in AWS

Run below CloudFormation template in your AWS account

```
https://github.com/80degreeswest/bfmgr/blob/master/src/main/resources/bfmgr-aws.json
```

Go to http://<INSTANCE_IP> and click Create
Enter desired Subnet ID and Security Group and click Create
You should have a working Buildfarm cluster setup in under 5 minutes