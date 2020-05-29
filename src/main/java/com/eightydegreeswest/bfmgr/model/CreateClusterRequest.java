package com.eightydegreeswest.bfmgr.model;

import lombok.Data;

@Data
public class CreateClusterRequest {
  private String clusterName;
  private String redisInstanceType;
  private String serverInstanceType;
  private String workerInstanceType;
  private String vpcName;
  private String securityGroupId;
  private String subnet;
  private String ami;
  private String serverRepo;
  private String serverTag;
  private String serverConfig;
  private String workerRepo;
  private String workerTag;
  private String workerConfig;
}
