package com.eightydegreeswest.bfmgr.model;

import java.util.Date;
import lombok.Data;

@Data
public class BfInstance {
  private String id;
  private Date startDate;
  private String ipAddress;
  private String state;
  private String type;
}
