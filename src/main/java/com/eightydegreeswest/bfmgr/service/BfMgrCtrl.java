package com.eightydegreeswest.bfmgr.service;

import com.eightydegreeswest.bfmgr.model.BuildfarmCluster;
import com.eightydegreeswest.bfmgr.model.CreateClusterRequest;
import java.io.IOException;
import java.util.List;

public interface BfMgrCtrl {
  List<BuildfarmCluster> getBuildfarmClusters();
  void createCluster(CreateClusterRequest createClusterRequest) throws IOException;
  void terminateCluster(String clusterName);
}
