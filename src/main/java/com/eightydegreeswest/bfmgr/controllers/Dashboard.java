package com.eightydegreeswest.bfmgr.controllers;

import com.eightydegreeswest.bfmgr.model.CreateClusterRequest;
import com.eightydegreeswest.bfmgr.service.BfMgrCtrl;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class Dashboard {

  @Autowired
  BfMgrCtrl bfMgrCtrl;

  @RequestMapping("/")
  public String getMainDashboard(Model model) {
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
}
