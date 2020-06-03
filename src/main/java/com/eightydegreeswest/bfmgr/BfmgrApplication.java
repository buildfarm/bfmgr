package com.eightydegreeswest.bfmgr;

import com.eightydegreeswest.bfmgr.service.BfMgrCtrl;
import com.eightydegreeswest.bfmgr.service.impl.BfMgrCtrlAws;
import com.eightydegreeswest.bfmgr.service.impl.BfMgrCtrlGcp;
import com.eightydegreeswest.bfmgr.service.impl.BfMgrCtrlLocal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.context.annotation.ApplicationScope;

@SpringBootApplication
public class BfmgrApplication {
	private static final Logger logger = LoggerFactory.getLogger(BfmgrApplication.class);
	private static Map<String, String> bfArgs = new HashMap<>();

	public static void main(String[] args) {
		parseArgs(args);
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

	@Bean
	@ApplicationScope
	public Map<String, String> args() {
		return bfArgs;
	}

	private static void parseArgs(String[] args) {
		try {
			for (int i = 0; i < args.length; i++) {
				bfArgs.put(args[i], args[i+1]);
				i++;
			}
		} catch (Exception e) {
			logger.error("Could not parse args: {}", args, e);
		}
		finally{
			logger.info("Args: {}", bfArgs);
		}
	}

	private boolean deployedInAws() {
		if ("aws".equalsIgnoreCase(bfArgs.get(BfArgs.DEPLOY))) {
			return true;
		}
		try (Socket socket = new Socket()) {
			socket.connect(new InetSocketAddress("169.254.169.254", 80), 1000);
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private boolean deployedInGcp() {
		if ("gcp".equalsIgnoreCase(bfArgs.get(BfArgs.DEPLOY))) {
			return true;
		}
		return false;
	}
}
