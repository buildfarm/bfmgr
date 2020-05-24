package com.eightydegreeswest.bfmgr;

import com.eightydegreeswest.bfmgr.service.BfMgrCtrl;
import com.eightydegreeswest.bfmgr.service.impl.BfMgrCtrlAws;
import com.eightydegreeswest.bfmgr.service.impl.BfMgrCtrlGcp;
import com.eightydegreeswest.bfmgr.service.impl.BfMgrCtrlLocal;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.annotation.ApplicationScope;

@SpringBootApplication
public class BfmgrApplication {

	public static void main(String[] args) {
		SpringApplication.run(BfmgrApplication.class, args);
	}

	@Bean
	@ApplicationScope
	public BfMgrCtrl bfMgrCtrl() {
		if (deployedInAws()) {
			return new BfMgrCtrlAws();
		} else if (deployedInGcp()) {
			return new BfMgrCtrlGcp();
		} else {
			return new BfMgrCtrlLocal();
		}
	}

	private boolean deployedInAws() {
		return false;
	}

	private boolean deployedInGcp() {
		return false;
	}
}
