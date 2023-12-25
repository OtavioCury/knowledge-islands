
package br.com.gitanalyzer.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Constants {
	
	public static final String pathInputMlFile = new String("/home/otavio/analiseR/doutorado/master_data/ml_models/input.csv");
	public static final String pathScriptMlFile = new String("/home/otavio/analiseR/doutorado/master_data/ml_models/predictionScript.R");
	public static final String pathOutputMlFile = new String("/home/otavio/analiseR/doutorado/master_data/ml_models/output.csv");
	
	public static final String pathCommitFilesLog = new String("/home/otavio/estudo_ihealth/ihealth/commitFilesLog.csv");
	public static final String pathCommitFilesFrequencyLog = new String("/home/otavio/Desktop/ihealth/ihealth/commitFilesFrequencyLog.csv");

	public static List<String> invalidPaths = Arrays.asList(new String[]{"/dev/null"});

	/**
	 * DOE coefficients
	 */
	public static final double interceptDoe = 5.28223;
	public static final double addsCoefDoe = 0.23173;
	public static final double faCoefDoe = 0.36151;
	public static final double numDaysCoefDoe = -0.19421;
	public static final double sizeCoefDoe = -0.28761;

	/**
	 * DOA coefficients
	 */
	public static final double interceptDoa = 3.293;
	public static final double faCoefDoa = 1.098;
	public static final double dlCoefDoa = 0.164;
	public static final double acCoefDoa = -0.321;

	public static final String ADD = new String("ADD");
	public static final String MODIFY = new String("MODIFY");
	public static final String DELETE = new String("DELETE");
	public static final String RENAME = new String ("RENAME");

	public static final String linguistFileName = new String("linguistfiles.log");
	public static final String clocFileName = new String("cloc_info.log");
	public static final String allFilesFileName = new String("filelist.log");
	public static final String commitFileName = new String("commitinfo.log");
	public static final String commitFileFileName = new String("commitfileinfo.log");
	public static final String diffFileName = new String("diff.log");
	public static final String logFile = new String("log.log");

	public static final String truckFactorResultFile = new String("truck_factor.log");
	public static final String truckFactorHistoryFile = new String("truckFactorHistoryFile.log");
	public static final String developersProjectFileName = new String("truck_factor_devs.log");

	public static final double normalizedThresholdMantainerDOA = 0.75;
	public static final double normalizedThresholdMantainerDOE = 0.7;
	public static final double thresholdMantainerDOA = 3.293;

	public static final int quantKnowledgedDevsByFile = 3;
	
	public static final double thresholdPercentExpert = 0.01;

	public static Map<String, String[]> projectPatterns  = new HashMap<String, String[]>();
	
	public static int intervalYearsProjectConsideredInactivate = 1;
	public static int intervalYearsProjectAgeFilter = 2;
	
	public static int numberOfThreadsToGenerateLogsFiles = 9;
	public static int numberOfThreadsToCalculateTf = 3;
	
	public static List<String> projectsToRemoveInFiltering(){
		List<String> notProjectSoftwareNames = new ArrayList<>();
		notProjectSoftwareNames.add("spring-projects/spring-data-examples");
		notProjectSoftwareNames.add("spring-projects/spring-integration-samples");
		notProjectSoftwareNames.add("spring-projects/spring-security-samples");
		notProjectSoftwareNames.add("spring-projects/spring-amqp-samples");
		notProjectSoftwareNames.add("spring-projects/spring-session-data-mongodb-examples");
		notProjectSoftwareNames.add("spring-projects/spring-ws-samples");
		notProjectSoftwareNames.add("spring-projects/spring-hateoas-examples");
		notProjectSoftwareNames.add("spring-projects/spring-data-book");
		notProjectSoftwareNames.add("iluwatar/java-design-patterns");
		notProjectSoftwareNames.add("eugenp/tutorials");
		
		return notProjectSoftwareNames;
	}

}
