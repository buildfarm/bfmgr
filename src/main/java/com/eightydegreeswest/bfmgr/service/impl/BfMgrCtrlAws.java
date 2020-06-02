package com.eightydegreeswest.bfmgr.service.impl;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.autoscaling.AmazonAutoScaling;
import com.amazonaws.services.autoscaling.AmazonAutoScalingClientBuilder;
import com.amazonaws.services.cloudformation.AmazonCloudFormation;
import com.amazonaws.services.cloudformation.AmazonCloudFormationAsyncClientBuilder;
import com.amazonaws.services.cloudformation.model.CreateStackRequest;
import com.amazonaws.services.cloudformation.model.DeleteStackRequest;
import com.amazonaws.services.cloudformation.model.Parameter;
import com.amazonaws.services.cloudformation.model.StackSummary;
import com.amazonaws.services.cloudformation.model.Tag;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.DescribeSubnetsRequest;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClientBuilder;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
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
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.FileCopyUtils;

@Service
public class BfMgrCtrlAws implements BfMgrCtrl {
  private static final Logger logger = LoggerFactory.getLogger(BfMgrCtrlAws.class);

  private static AmazonCloudFormation awsCloudFormationClient;
  private static AmazonEC2 awsEc2Client;
  private static AmazonElasticLoadBalancing awsElbClient;
  private static AmazonAutoScaling awsAsgClient;

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

  @Autowired
  private Map<String, String> bfArgs;

  public BfMgrCtrlAws() {
    String region = getRegion();
    awsCloudFormationClient = AmazonCloudFormationAsyncClientBuilder.standard().withRegion(region).build();
    awsEc2Client = AmazonEC2ClientBuilder.standard().withRegion(region).build();
    awsElbClient = AmazonElasticLoadBalancingClientBuilder.standard().withRegion(region).build();
    awsAsgClient = AmazonAutoScalingClientBuilder.standard().withRegion(region).build();
  }

  @Override
  public List<BuildfarmCluster> getBuildfarmClusters() {
    List<BuildfarmCluster> buildfarmClusters = new ArrayList<>();
    for (StackSummary stack : awsCloudFormationClient.listStacks().getStackSummaries()) {
      if (stack.getTemplateDescription().equalsIgnoreCase("Buildfarm deployment using bfmgr") && !stack.getStackStatus().contains("DELETE")) {
        BuildfarmCluster buildfarmCluster = new BuildfarmCluster();
        buildfarmCluster.setClusterName(stack.getStackName());
        buildfarmCluster.setEndpoint(getLoadBalancerEndpoint(buildfarmCluster.getClusterName()));
        buildfarmCluster.setServers(getServers(buildfarmCluster.getClusterName()));
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
    createClusterRequest.setWorkerConfig(remoteWorkerConfig);
    createClusterRequest.setWorkerInstanceType(defaultWorkerInstanceType);
    createClusterRequest.setWorkerRepo(workerRepo);
    createClusterRequest.setWorkerTag(buildfarmTag);
    return createClusterRequest;
  }

  @Override
  public List<Subnet> getSubnets() {
    return awsEc2Client.describeSubnets().getSubnets();
  }

  @Override
  public List<SecurityGroup> getSecurityGroups() {
    return awsEc2Client.describeSecurityGroups().getSecurityGroups();
  }

  private String getLoadBalancerEndpoint(String clusterName) {
    try {
      return awsElbClient.describeLoadBalancers(new DescribeLoadBalancersRequest().withNames(clusterName)).getLoadBalancers().get(0).getDNSName();
      } catch (Exception e) {
      return "N/A";
    }
  }

  private List<BfInstance> getServers(String clusterName) {
    return getBfInstances(clusterName, "server");
  }

  private List<BfInstance> getWorkers(String clusterName) {
    return getBfInstances(clusterName, "worker");
  }

  private List<Instance> getAwsInstances(String clusterName, String instanceType) {
    List<Instance> instances = new ArrayList<>();
    DescribeInstancesResult instancesResult = awsEc2Client.describeInstances(
      new DescribeInstancesRequest().withFilters(new Filter().withName("tag-value").withValues(clusterName), new Filter().withName("tag-value").withValues(instanceType)));
    for (Reservation r : instancesResult.getReservations()) {
      for (Instance e : r.getInstances()) {
        instances.add(e);
      }
    }
    return instances;
  }

  private List<BfInstance> getBfInstances(String clusterName, String instanceType) {
    List<BfInstance> instances = new ArrayList<>();
    for (Instance i : getAwsInstances(clusterName, instanceType)) {
      BfInstance bfInstance = new BfInstance();
      bfInstance.setId(i.getInstanceId());
      bfInstance.setIpAddress(i.getPrivateIpAddress());
      bfInstance.setStartDate(i.getLaunchTime());
      bfInstance.setState(i.getState().getName());
      bfInstance.setType(i.getInstanceType());
      instances.add(bfInstance);
    }
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
    parameters.add(getParameter("ServerInstanceType", createClusterRequest.getServerInstanceType()));
    parameters.add(getParameter("WorkerInstanceType", createClusterRequest.getWorkerInstanceType()));
    parameters.add(getParameter("Vpc", getVpcId(createClusterRequest.getSubnet())));
    parameters.add(getParameter("SecurityGroup", createClusterRequest.getSecurityGroupId()));
    parameters.add(getParameter("SubnetPool", createClusterRequest.getSubnet()));
    parameters.add(getParameter("AmiImageId", createClusterRequest.getAmi()));
    parameters.add(getParameter("WorkerRepo", createClusterRequest.getWorkerRepo()));
    parameters.add(getParameter("WorkerTag", createClusterRequest.getWorkerTag()));
    parameters.add(getParameter("WorkerConfigFile", createClusterRequest.getWorkerConfig()));
    parameters.add(getParameter("ServerRepo", createClusterRequest.getServerRepo()));
    parameters.add(getParameter("ServerTag", createClusterRequest.getServerTag()));
    parameters.add(getParameter("ServerConfigFile", createClusterRequest.getServerConfig()));
    parameters.add(getParameter("ClusterName", createClusterRequest.getClusterName()));
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
    tag = new Tag().withKey("Resource").withValue("Buildfarm");
    tags.add(tag);
    tags.addAll(parseExtraTags());
    return tags;
  }

  private String getRegion() {
    return Regions.US_EAST_1.getName();
  }

  private String getVpcId(String subnetId) {
    return awsEc2Client.describeSubnets(new DescribeSubnetsRequest().withSubnetIds(subnetId)).getSubnets().get(0).getVpcId();
  }

  private List<Tag> parseExtraTags() {
    List<Tag> extraTags = new ArrayList<>();
    if (bfArgs.containsKey("--tags")) {
      for (String cmdTagPair : bfArgs.get("--tags").split(",")) {
        String[] cmdTag = cmdTagPair.split("=");
        Tag tag = new Tag().withKey(cmdTag[0]).withValue(cmdTag[1]);
        extraTags.add(tag);
      }
    }
    return extraTags;
  }
}
