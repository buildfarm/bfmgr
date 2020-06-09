package com.eightydegreeswest.bfmgr.controllers;

import com.eightydegreeswest.bfmgr.model.CreateClusterRequest;
import com.eightydegreeswest.bfmgr.service.BfMgrCtrl;
import java.io.IOException;
import java.net.UnknownHostException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class Dashboard {
  private static final Logger logger = LoggerFactory.getLogger(Dashboard.class);

  @Autowired
  BfMgrCtrl bfMgrCtrl;

  @RequestMapping("/")
  public String getMainDashboard(Model model) throws UnknownHostException {
    model.addAttribute("createClusterRequest", bfMgrCtrl.getDefaultCreateClusterRequest());
    model.addAttribute("subnetList", bfMgrCtrl.getSubnets());
    model.addAttribute("securityGroupList", bfMgrCtrl.getSecurityGroups());
    model.addAttribute("keyNamesList", bfMgrCtrl.getKeyNames());
    model.addAttribute("clusters", bfMgrCtrl.getBuildfarmClusters());
    return "dashboard";
  }

  @RequestMapping("/terminate/{clusterName}")
  public String terminateCluster(@PathVariable String clusterName) {
    bfMgrCtrl.terminateCluster(clusterName);
    return "redirect:/";
  }

  @RequestMapping("/create")
  public String createCluster() throws IOException {
    bfMgrCtrl.createCluster(new CreateClusterRequest());
    return "redirect:/";
  }

  @PostMapping("/create")
  public String createClusterSubmit(@ModelAttribute CreateClusterRequest createClusterRequest) throws IOException {
    bfMgrCtrl.createCluster(createClusterRequest);
    return "redirect:/";
  }
}
