package com.eightydegreeswest.bfmgr.service.impl;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.ec2.model.KeyPairInfo;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.ec2.model.Subnet;
import com.amazonaws.services.ecr.AmazonECR;
import com.amazonaws.services.ecr.AmazonECRClientBuilder;
import com.amazonaws.services.ecr.model.AuthorizationData;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenRequest;
import com.amazonaws.services.ecr.model.GetAuthorizationTokenResult;
import com.eightydegreeswest.bfmgr.model.BfInstance;
import com.eightydegreeswest.bfmgr.model.BuildfarmCluster;
import com.eightydegreeswest.bfmgr.model.CreateClusterRequest;
import com.eightydegreeswest.bfmgr.service.BfMgrCtrl;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.DockerClientConfig;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.binary.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class BfMgrCtrlLocal implements BfMgrCtrl {
  private static final Logger logger = LoggerFactory.getLogger(BfMgrCtrlLocal.class);
  private static DockerClient dockerClient = DockerClientBuilder.getInstance().build();
  private static AmazonECR ecrClient;

  @Value("${container.name.server}")
  private String serverContainer;

  @Value("${container.name.worker}")
  private String workerContainer;

  @Value("${container.name.redis}")
  private String redisContainer;

  @Value("${container.repo.server}")
  private String serverRepo;

  @Value("${container.repo.worker}")
  private String workerRepo;

  @Value("${container.repo.redis}")
  private String redisRepo;

  @Value("${config.path}")
  private String configPath;

  @Value("${cas.path}")
  private String casPath;

  @Value("${remote.config.server}")
  private String remoteServerConfig;

  @Value("${remote.config.worker}")
  private String remoteWorkerConfig;

  @Value("${tag.redis}")
  private String redisTag;

  @Value("${tag.buildfarm}")
  private String buildfarmTag;

  @Value("${port.redis}")
  private int redisPortVal;

  @Value("${port.server}")
  private int serverPortVal;

  @Value("${port.worker}")
  private int workerPortVal;

  public BfMgrCtrlLocal() {
    try {
      String region = getRegion();
      ecrClient = AmazonECRClientBuilder.standard().withRegion(region).build();
    } catch (Exception e) {
      ecrClient = null;
    }
  }

  @Override
  public List<BuildfarmCluster> getBuildfarmClusters() throws UnknownHostException {
    List<BuildfarmCluster> buildfarmClusters = new ArrayList<>();
    List<Container> containers = dockerClient.listContainersCmd()
      .withShowAll(true)
      .exec();
    BuildfarmCluster buildfarmCluster = new BuildfarmCluster();
    buildfarmCluster.setClusterName("Local");
    buildfarmCluster.setEndpoint(InetAddress.getLocalHost().getHostName() + ":" + serverPortVal);
    for (Container container : containers) {
      String containerName = container.getNames()[0];
      if ("running".equals(container.getState()) && containerName.contains("buildfarm")) {
        switch (containerName) {
          case "/buildfarm-server":
            BfInstance server = new BfInstance();
            server.setId("localhost:" + serverPortVal);
            List servers = Collections.singletonList(new ArrayList().add(server));
            buildfarmCluster.setServers(servers);
          case "/buildfarm-worker":
            BfInstance worker = new BfInstance();
            worker.setId("localhost:" + workerPortVal);
            List workers = Collections.singletonList(new ArrayList().add(worker));
            buildfarmCluster.setWorkers(workers);
          case "/buildfarm-redis":
            buildfarmCluster.setRedis("localhost:" + redisPortVal);
        }
      }
    }
    if (buildfarmCluster.getRedis() != null
        && buildfarmCluster.getWorkers() != null
        && buildfarmCluster.getWorkers().size() > 0
        && buildfarmCluster.getServers() != null
        && buildfarmCluster.getServers().size() > 0) {
      buildfarmClusters.add(buildfarmCluster);
    }
    return buildfarmClusters;
  }

  @Override
  public void createCluster(CreateClusterRequest createClusterRequest) throws IOException {
    terminateCluster("Local");
    createPath(configPath);
    downloadFile(createClusterRequest.getServerConfig(), Paths.get(configPath + "/shard-server.config").toFile());
    downloadFile(createClusterRequest.getWorkerConfig(), Paths.get(configPath + "/shard-worker.config").toFile());
    createPath(casPath);

    pullImage(redisRepo, redisTag);
    pullImage(createClusterRequest.getServerRepo(), createClusterRequest.getServerTag());
    pullImage(createClusterRequest.getWorkerRepo(), createClusterRequest.getWorkerTag());

    Ports portBindings = new Ports();
    portBindings.bind(ExposedPort.tcp(redisPortVal), Ports.Binding.bindPort(redisPortVal));

    CreateContainerResponse response = dockerClient.createContainerCmd(redisRepo + ":" + redisTag)
      .withName(redisContainer)
      .withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings))
      .exec();
    dockerClient.startContainerCmd(response.getId()).exec();

    portBindings = new Ports();
    portBindings.bind(ExposedPort.tcp(workerPortVal), Ports.Binding.bindPort(workerPortVal));

    response = dockerClient.createContainerCmd(createClusterRequest.getWorkerRepo() + ":" + createClusterRequest.getWorkerTag())
      .withName(workerContainer)
      .withHostConfig(HostConfig.newHostConfig()
        .withPortBindings(portBindings)
        .withPrivileged(true)
        .withBinds(Bind.parse("/tmp/buildfarm:/var/lib/buildfarm-shard-worker"), Bind.parse("/tmp/worker:/tmp/worker"))
        .withNetworkMode("host"))
      .withCmd("/var/lib/buildfarm-shard-worker/shard-worker.config", "--public_name=localhost:" + workerPortVal)
      .exec();
    dockerClient.startContainerCmd(response.getId()).exec();

    portBindings = new Ports();
    portBindings.bind(ExposedPort.tcp(serverPortVal), Ports.Binding.bindPort(serverPortVal));

    response = dockerClient.createContainerCmd(createClusterRequest.getServerRepo() + ":" + createClusterRequest.getServerTag())
      .withName(serverContainer)
      .withHostConfig(HostConfig.newHostConfig()
        .withPortBindings(portBindings)
        .withBinds(Bind.parse("/tmp/buildfarm:/var/lib/buildfarm-server"))
        .withNetworkMode("host"))
      .withCmd("/var/lib/buildfarm-server/shard-server.config", "-p", Integer.toString(serverPortVal))
      .exec();
    dockerClient.startContainerCmd(response.getId()).exec();
  }

  @Override
  public void terminateCluster(String clusterName) {
    removeContainer(serverContainer);
    removeContainer(workerContainer);
    removeContainer(redisContainer);
  }

  @Override
  public CreateClusterRequest getDefaultCreateClusterRequest() {
    CreateClusterRequest createClusterRequest = new CreateClusterRequest();
    createClusterRequest.setDeploymentType("local");
    createClusterRequest.setClusterName("buildfarm-test");
    createClusterRequest.setServerConfig(remoteServerConfig);
    createClusterRequest.setServerRepo(serverRepo);
    createClusterRequest.setServerTag(buildfarmTag);
    createClusterRequest.setWorkerConfig(remoteWorkerConfig);
    createClusterRequest.setWorkerRepo(workerRepo);
    createClusterRequest.setWorkerTag(buildfarmTag);
    return createClusterRequest;
  }

  @Override
  public List<Subnet> getSubnets() {
    return new ArrayList<>();
  }

  @Override
  public List<SecurityGroup> getSecurityGroups() {
    return new ArrayList<>();
  }

  @Override
  public List<KeyPairInfo> getKeyNames() {
    return new ArrayList<>();
  }

  private void removeContainer(String container) {
    try {
      dockerClient.removeContainerCmd(container).withForce(true).exec();
    } catch (Exception e) { }
  }

  private Path createPath(String pathStr) throws IOException {
    Path path = Paths.get(pathStr);
    if (!Files.exists(path)) {
      Files.createDirectory(path);
    }
    return path;
  }

  private void downloadFile(String urlStr, File file) throws IOException {
    URL url = new URL(urlStr);
    ReadableByteChannel rbc = Channels.newChannel(url.openStream());
    FileOutputStream fos = new FileOutputStream(file);
    fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
    fos.close();
    rbc.close();
  }

  private void pullImage(String imageRepo, String tag) {
    try {
      if (imageRepo.contains("amazonaws.com")) {
        pullEcrImage(imageRepo, tag);
      } else {
        logger.info("Pulling image {}:{} from Dockerhub", imageRepo, tag);
        dockerClient
            .pullImageCmd(imageRepo)
            .withTag(tag)
            .start()
            .awaitCompletion(5, TimeUnit.MINUTES);
        }
    } catch (Exception e) {
      logger.error("Could not pull image {}:{} from repository", imageRepo, tag, e);
    }
  }

  private void pullEcrImage(String imageRepo, String tag) throws InterruptedException {
    logger.info("Pulling image {}:{} from ECR", imageRepo, tag);
    GetAuthorizationTokenRequest getAuthTokenRequest = new GetAuthorizationTokenRequest();
    List<String> registryIds = new ArrayList<>();
    registryIds.add(imageRepo.substring(0, 12));
    getAuthTokenRequest.setRegistryIds(registryIds);
    GetAuthorizationTokenResult getAuthTokenResult = ecrClient.getAuthorizationToken(getAuthTokenRequest);
    AuthorizationData authData = getAuthTokenResult.getAuthorizationData().get(0);
    String userPassword = StringUtils.newStringUtf8(Base64.decodeBase64(authData.getAuthorizationToken()));
    String user = userPassword.substring(0, userPassword.indexOf(":"));
    String password = userPassword.substring(userPassword.indexOf(":")+1);
    DockerClientConfig ecrConfig = DefaultDockerClientConfig.createDefaultConfigBuilder()
      .withDockerTlsVerify(false)
      .withRegistryUsername(user)
      .withRegistryPassword(password)
      .withRegistryUrl(authData.getProxyEndpoint())
      .build();
    DockerClient dockerClient = DockerClientBuilder.getInstance(ecrConfig).build();
    dockerClient
      .pullImageCmd(imageRepo)
      .withTag(tag)
      .withAuthConfig(dockerClient.authConfig())
      .start()
      .awaitCompletion(5, TimeUnit.MINUTES);
  }

  private String getRegion() {
    return Regions.US_EAST_1.getName();
  }
}
