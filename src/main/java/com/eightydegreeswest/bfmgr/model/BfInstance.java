package com.eightydegreeswest.bfmgr.model;

import com.amazonaws.services.ec2.model.Instance;
import lombok.Data;

@Data
public class BfInstance {
  private String id;
  private Instance ec2;
}
