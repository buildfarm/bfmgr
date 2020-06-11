package com.eightydegreeswest.bfmgr.model;

import com.amazonaws.util.StringUtils;
import java.util.List;
import lombok.Data;

@Data
public class BuildfarmCluster {
  private String clusterName;
  private String endpoint;
  private List<BfInstance> workers;
  private List<BfInstance> servers;
  private String redis;

  public String getEndpoint() {
    return StringUtils.isNullOrEmpty(endpoint) ? "" :  "grpc://" + endpoint;
  }
}
