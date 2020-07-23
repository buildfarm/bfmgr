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
rel=<RELEASE_NUMBER>
wget https://github.com/80degreeswest/bfmgr/releases/download/$rel/bfmgr-$rel.jar
java -jar bfmgr-$rel.jar
```

Go to http://localhost and click Create

#### Deploying in AWS

Run below CloudFormation template in your AWS account

```
https://github.com/80degreeswest/bfmgr/blob/master/src/main/resources/bfmgr-aws.json
```

Alternatively, manually launch an EC2 instance with the IAM Role with specified policies and run the following

```
yum install java -y
rel=<RELEASE_NUMBER>
region=us-east-1
wget -N https://github.com/80degreeswest/bfmgr/releases/download/$rel/bfmgr-$rel.jar
mkdir /var/log/bfmgr && chmod 0777 /var/log/bfmgr
java -jar bfmgr-$rel.jar --region $region &
disown
```

Go to http://<INSTANCE_IP> and click Create</br>
Enter desired Subnet ID and Security Group and click Create<br/>
You should have a working Buildfarm cluster setup in under 5 minutes<br/>