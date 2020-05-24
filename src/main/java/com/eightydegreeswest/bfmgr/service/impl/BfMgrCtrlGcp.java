package com.eightydegreeswest.bfmgr.service.impl;

import com.eightydegreeswest.bfmgr.model.BuildfarmCluster;
import com.eightydegreeswest.bfmgr.model.CreateClusterRequest;
import com.eightydegreeswest.bfmgr.service.BfMgrCtrl;
import java.util.List;
import org.springframework.scheduling.annotation.Async;

public class BfMgrCtrlGcp implements BfMgrCtrl {
  @Override
  public List<BuildfarmCluster> getBuildfarmClusters() {
    throw new UnsupportedOperationException();
  }

  @Override
  @Async
  public void createCluster(CreateClusterRequest createClusterRequest) {
    throw new UnsupportedOperationException();
  }

  @Override
  @Async
  public void terminateCluster(String clusterName) {
    throw new UnsupportedOperationException();
  }
}
