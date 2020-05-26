package com.eightydegreeswest.bfmgr.service.impl;

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
import com.github.dockerjava.core.DockerClientBuilder;
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
import org.springframework.beans.factory.annotation.Value;

public class BfMgrCtrlLocal implements BfMgrCtrl {
  private static DockerClient dockerClient = DockerClientBuilder.getInstance().build();

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

  private static ExposedPort redisPort = ExposedPort.tcp(6379);
  private static ExposedPort serverPort = ExposedPort.tcp(8980);
  private static ExposedPort workerPort = ExposedPort.tcp(8981);

  @Override
  public List<BuildfarmCluster> getBuildfarmClusters() throws UnknownHostException {
    List<BuildfarmCluster> buildfarmClusters = new ArrayList<>();
    List<Container> containers = dockerClient.listContainersCmd()
      .withShowAll(true)
      .exec();
    BuildfarmCluster buildfarmCluster = new BuildfarmCluster();
    buildfarmCluster.setClusterName("Local");
    buildfarmCluster.setEndpoint(InetAddress.getLocalHost().getHostName() + ":8980");
    for (Container container : containers) {
      String containerName = container.getNames()[0];
      if ("running".equals(container.getState()) && containerName.contains("buildfarm")) {
        switch (containerName) {
          case "/buildfarm-server":
            BfInstance scheduler = new BfInstance();
            scheduler.setId("localhost:8980");
            List workers = Collections.singletonList(new ArrayList().add(scheduler));
            buildfarmCluster.setWorkers(workers);
          case "/buildfarm-worker":
            BfInstance worker = new BfInstance();
            worker.setId("localhost:8981");
            List schedulers = Collections.singletonList(new ArrayList().add(worker));
            buildfarmCluster.setSchedulers(schedulers);
          case "/buildfarm-redis":
            buildfarmCluster.setRedis("localhost:6379");
        }
      }
    }
    if (buildfarmCluster.getRedis() != null
        && buildfarmCluster.getWorkers() != null
        && buildfarmCluster.getWorkers().size() > 0
        && buildfarmCluster.getSchedulers() != null
        && buildfarmCluster.getSchedulers().size() > 0) {
      buildfarmClusters.add(buildfarmCluster);
    }
    return buildfarmClusters;
  }

  @Override
  public void createCluster(CreateClusterRequest createClusterRequest) throws IOException {
    terminateCluster("Local");
    createPath(configPath);
    downloadFile(remoteServerConfig, Paths.get(configPath + "/shard-server.config").toFile());
    downloadFile(remoteWorkerConfig, Paths.get(configPath + "/shard-worker.config").toFile());
    createPath(casPath);

    pullImage(redisRepo, redisTag);
    pullImage(serverRepo, buildfarmTag);
    pullImage(workerRepo, buildfarmTag);

    Ports portBindings = new Ports();
    portBindings.bind(redisPort, Ports.Binding.bindPort(6379));

    CreateContainerResponse response = dockerClient.createContainerCmd(redisRepo + ":" + redisTag)
      .withName(redisContainer)
      .withExposedPorts(redisPort)
      .withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings).withPublishAllPorts(true))
      .exec();
    dockerClient.startContainerCmd(response.getId()).exec();

    portBindings = new Ports();
    portBindings.bind(workerPort, Ports.Binding.bindPort(8981));

    response = dockerClient.createContainerCmd(workerRepo + ":" + buildfarmTag)
      .withName(workerContainer)
      .withExposedPorts(workerPort)
      .withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings).withPublishAllPorts(true))
      .withPrivileged(true)
      .withHostConfig(HostConfig.newHostConfig().withNetworkMode("host"))
      .withCmd("/var/lib/buildfarm-shard-worker/shard-worker.config", "--public_name=localhost:8981")
      .withBinds(Bind.parse("/tmp/buildfarm:/var/lib/buildfarm-shard-worker"), Bind.parse("/tmp/worker:/tmp/worker"))
      .exec();
    dockerClient.startContainerCmd(response.getId()).exec();

    portBindings = new Ports();
    portBindings.bind(serverPort, Ports.Binding.bindPort(8980));

    response = dockerClient.createContainerCmd(serverRepo + ":" + buildfarmTag)
      .withName(serverContainer)
      .withExposedPorts(serverPort)
      .withHostConfig(HostConfig.newHostConfig().withPortBindings(portBindings).withPublishAllPorts(true))
      .withHostConfig(HostConfig.newHostConfig().withNetworkMode("host"))
      .withCmd("/var/lib/buildfarm-server/shard-server.config", "-p", "8980")
      .withBinds(Bind.parse("/tmp/buildfarm:/var/lib/buildfarm-server"))
      .exec();
    dockerClient.startContainerCmd(response.getId()).exec();
  }

  @Override
  public void terminateCluster(String clusterName) {
    removeContainer(serverContainer);
    removeContainer(workerContainer);
    removeContainer(redisContainer);
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
      dockerClient.pullImageCmd(imageRepo).withTag(tag).start().awaitCompletion(5, TimeUnit.MINUTES);
    } catch (Exception e) { }
  }
}
