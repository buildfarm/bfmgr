package com.eightydegreeswest.bfmgr.service.impl;

import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsyncClientBuilder;
import com.amazonaws.services.cloudformation.model.Capability;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackSummary;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstanceTypesRequest;
import com.amazonaws.services.ec2.model.InstanceTypeInfo;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ec2.model.Vpc;
import com.eightydegreeswest.bfmgr.model.BfInstance;
import com.eightydegreeswest.bfmgr.model.BuildfarmCluster;
import com.eightydegreeswest.bfmgr.model.CreateClusterRequest;
import com.eightydegreeswest.bfmgr.service.BfMgrCtrl;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

@Service
public class BfMgrCtrlAws implements BfMgrCtrl {
  private static AmazonCloudFormation awsCloudFormationClient;
  private static AmazonEC2 awsEc2Client;

  @Value("${container.repo.server}")
  private String serverRepo;

  @Value("${container.repo.worker}")
  private String workerRepo;

  @Value("${remote.config.server}")
  private String remoteServerConfig;

  @Value("${remote.config.worker}")
  private String remoteWorkerConfig;

  @Value("${tag.buildfarm}")
  private String buildfarmTag;

  @Value("${default.aws.ami}")
  private String defaultAmi;

  @Value("${default.aws.instance.redis}")
  private String defaultRedisInstanceType;

  @Value("${default.aws.instance.server}")
  private String defaultServerInstanceType;

  @Value("${default.aws.instance.worker}")
  private String defaultWorkerInstanceType;

  public BfMgrCtrlAws() {
    String region = getRegion();
    awsCloudFormationClient = AmazonCloudFormationAsyncClientBuilder.standard().withRegion(region).build();
    awsEc2Client = AmazonEC2ClientBuilder.standard().withRegion(region).build();
  }

  @Override
  public List<BuildfarmCluster> getBuildfarmClusters() {
    List<BuildfarmCluster> buildfarmClusters = new ArrayList<>();
    for (StackSummary stack : awsCloudFormationClient.listStacks().getStackSummaries()) {
      if (stack.getTemplateDescription().equalsIgnoreCase("Buildfarm deployment using bfmgr")) {
        BuildfarmCluster buildfarmCluster = new BuildfarmCluster();
        buildfarmCluster.setClusterName(stack.getStackName());
        buildfarmCluster.setEndpoint(getLoadBalancerEndpoint(buildfarmCluster.getClusterName()));
        buildfarmCluster.setSchedulers(getSchedulers(buildfarmCluster.getClusterName()));
        buildfarmCluster.setWorkers(getWorkers(buildfarmCluster.getClusterName()));
        buildfarmClusters.add(buildfarmCluster);
      }
    }
    return buildfarmClusters;
  }

  @Override
  @Async
  public void createCluster(CreateClusterRequest createClusterRequest) {
    CreateStackRequest createRequest = new CreateStackRequest();
    createRequest.setStackName(createClusterRequest.getClusterName());
    createRequest.setTemplateBody(loadCloudFormationJson());
    createRequest.setParameters(getCloudFormationParameters(createClusterRequest));
    createRequest.setTags(getTags(createClusterRequest.getClusterName()));
    awsCloudFormationClient.createStack(createRequest);
  }

  @Override
  @Async
  public void terminateCluster(String clusterName) {
    DeleteStackRequest deleteStackRequest = new DeleteStackRequest().withStackName(clusterName);
    awsCloudFormationClient.deleteStack(deleteStackRequest);
  }

  @Override
  public CreateClusterRequest getDefaultCreateClusterRequest() {
    CreateClusterRequest createClusterRequest = new CreateClusterRequest();
    createClusterRequest.setClusterName("buildfarm-test");
    createClusterRequest.setAmi(defaultAmi);
    createClusterRequest.setRedisInstanceType(defaultRedisInstanceType);
    createClusterRequest.setSecurityGroupId("");
    createClusterRequest.setServerConfig(remoteServerConfig);
    createClusterRequest.setServerInstanceType(defaultServerInstanceType);
    createClusterRequest.setServerRepo(serverRepo);
    createClusterRequest.setServerTag(buildfarmTag);
    createClusterRequest.setSubnet("");
    createClusterRequest.setVpcName("");
    createClusterRequest.setWorkerConfig(remoteWorkerConfig);
    createClusterRequest.setWorkerInstanceType(defaultWorkerInstanceType);
    createClusterRequest.setWorkerRepo(workerRepo);
    createClusterRequest.setWorkerTag(buildfarmTag);
    return createClusterRequest;
  }

  private List<Subnet> getSubnets() {
    return awsEc2Client.describeSubnets().getSubnets();
  }

  private List<Vpc> getVpcs() {
    return awsEc2Client.describeVpcs().getVpcs();
  }

  private List<SecurityGroup> getSecurityGroups() {
    return awsEc2Client.describeSecurityGroups().getSecurityGroups();
  }

  private List<InstanceTypeInfo> getInstanceTypes() {
    InstanceTypeInfo i = new InstanceTypeInfo();
    return awsEc2Client.describeInstanceTypes(new DescribeInstanceTypesRequest()).getInstanceTypes();
  }

  private String getLoadBalancerEndpoint(String clusterName) {
    return "";
  }

  private List<BfInstance> getSchedulers(String clusterName) {
    List<BfInstance> instances = new ArrayList<>();

    return instances;
  }

  private List<BfInstance> getWorkers(String clusterName) {
    List<BfInstance> instances = new ArrayList<>();

    return instances;
  }

  private String loadCloudFormationJson() {
    String template = null;
    try {
      Resource resource = new ClassPathResource("classpath:aws.json");
      InputStream inputStream = resource.getInputStream();
      byte[] bdata = FileCopyUtils.copyToByteArray(inputStream);
      template = new String(bdata, StandardCharsets.UTF_8);
    } catch (IOException e) {
    }
    return template;
  }

  private Collection<Parameter> getCloudFormationParameters(CreateClusterRequest createClusterRequest) {
    Collection<Parameter> parameters = new ArrayList<>();
    parameters.add(getParameter("RedisInstanceType", createClusterRequest.getRedisInstanceType()));
    parameters.add(getParameter("SchedulerInstanceType", createClusterRequest.getServerInstanceType()));
    parameters.add(getParameter("WorkerInstanceType", createClusterRequest.getWorkerInstanceType()));
    parameters.add(getParameter("Vpc", createClusterRequest.getVpcName()));
    parameters.add(getParameter("SecurityGroup", createClusterRequest.getSecurityGroupId()));
    parameters.add(getParameter("SubnetPool", createClusterRequest.getSubnet()));
    parameters.add(getParameter("AmiImageId", createClusterRequest.getAmi()));
    parameters.add(getParameter("WorkerRepo", createClusterRequest.getWorkerRepo()));
    parameters.add(getParameter("WorkerTag", createClusterRequest.getWorkerTag()));
    parameters.add(getParameter("WorkerConfigFile", createClusterRequest.getWorkerConfig()));
    parameters.add(getParameter("ServerRepo", createClusterRequest.getServerRepo()));
    parameters.add(getParameter("ServerTag", createClusterRequest.getServerTag()));
    parameters.add(getParameter("ServerConfigFile", createClusterRequest.getServerConfig()));
    return parameters;
  }

  private Parameter getParameter(String key, String val) {
    Parameter parameter = new Parameter();
    parameter.setParameterKey(key);
    parameter.setParameterValue(val);
    return parameter;
  }

  private Collection<Tag> getTags(String clusterName) {
    Collection<Tag> tags = new ArrayList<>();
    Tag tag = new Tag().withKey("Name").withValue(clusterName.toLowerCase());
    tags.add(tag);
    tag = new Tag().withKey("Created By").withValue("bfmgr");
    tags.add(tag);
    return tags;
  }

  private String getRegion() {
    return Regions.US_EAST_1.getName();
  }
}
