package de.tum.bgu.msm;

import de.tum.bgu.msm.data.SummarizeData;
import de.tum.bgu.msm.properties.Properties;
import org.apache.log4j.Logger;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ResourceBundle;

/**
 * @author dziemke
 */

public final class SiloMatsim {
	static Logger logger = Logger.getLogger(SiloMatsim.class);

	private final Properties properties;
	private final Config matsimConfig;// = ConfigUtils.createConfig(); // SILO-MATSim integration-specific

	/**
	 * Option to set the matsim config directly, at this point meant for tests.
	 */
	public SiloMatsim(String args, Config config, Implementation implementation) {
		properties = SiloUtil.siloInitialization(args, implementation);
		matsimConfig = config ;
	}	    

	public final void run() {
		long startTime = System.currentTimeMillis();
		try {
			logger.info("Starting SILO program for MATSim");
			logger.info("Scenario: " + properties.main.scenarioName + ", Simulation start year: " + properties.main.startYear);
			SiloModel model = new SiloModel(matsimConfig, Properties.get());
			model.runModel();
			logger.info("Finished SILO.");
		} catch (Exception e) {
			logger.error("Error running SILO.");
			throw new RuntimeException(e);
		} finally {
			SiloUtil.trackingFile("close");
			SummarizeData.resultFile("close");
			SummarizeData.resultFileSpatial("close");
			float endTime = SiloUtil.rounder(((System.currentTimeMillis() - startTime) / 60000), 1);
			int hours = (int) (endTime / 60);
			int min = (int) (endTime - 60 * hours);
			logger.info("Runtime: " + hours + " hours and " + min + " minutes.");
			if (properties.main.trackTime) {
				String fileName = properties.main.trackTimeFile;
				try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)))) {
					out.println("Runtime: " + hours + " hours and " + min + " minutes.");
					out.close();
				} catch (IOException e) {
					logger.warn("Could not add run-time statement to time-tracking file.");
				}
			}
		}
	}
}