package com.softwareag.um.migrator;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.mysql.cj.util.StringUtils;
import com.softwareag.um.property.ToolProperties;
import com.softwareag.um.runtime.executor.RuntimeExec;
import com.softwareag.um.runtime.executor.RuntimeExec.StreamWrapper;
import com.softwareag.um.script.generator.ScriptGenerator;
import com.softwareag.um.script.runner.ScriptRunner;

@Component
public class DatabaseMigrator {
    
    public static long CURRENT_TIME_MILLIS = System.currentTimeMillis();
	
	private static String TOOL_PROP_FILE_NAME = "tool.properties";
	
	private static String SQL_DUMP_FILE_NAME = "sqldump.sql";
	
	private static String TRIGGERS_FILE_NAME = "triggers.sql";
	
	private static String MIGRATION_REF_CHANGES_FILE_NAME = "migration-reference-changes.sql";
	
	private static String MIGRATION_SCRIPTS_FILE_NAME = "migration-scripts.sql";

	@Autowired
	ToolProperties toolProperties;
	
	public void startMigration(String tenantId) {
//		String propFilePath = getBasePath() + "\\"+ TOOL_PROP_FILE_NAME;
//		ToolProperties.init(propFilePath);
		
		String host = toolProperties.getMysql().getHost();
		String port = toolProperties.getMysql().getPort();
		String userName = toolProperties.getMysql().getUsername();
		String password = toolProperties.getMysql().getPassword();
		String sourceSchemaName = toolProperties.getSourceSchema().getName();
		String destSchemaName = toolProperties.getSourceSchema().getName();
		
		System.out.println("Running tool with full capabilities!!");
		runFullMigration(host, port, sourceSchemaName, userName, password, destSchemaName);
	}
	private void runFullMigration(String host, String port, String sourceSchemaName, String userName,
			String password, String destSchemaName) {

		try(Connection conn = getConnection(host, port, sourceSchemaName, userName, password)){
			System.out.println("Connection established to mysql DB - " + sourceSchemaName + "@" + host);
			
			// Step-1
			setupMigrationEnv(conn, sourceSchemaName, destSchemaName);

			// Step-2
			changeMigrationStage(conn, "BACKUP");

			// Step-3
			exportDatabaseDump(sourceSchemaName, userName, password, destSchemaName);
	
			// Step-4
			importDatabaseDump(sourceSchemaName, userName, password);
			
			// Step-5
			runMigrationScripts(host, port, destSchemaName, userName, password);
			
			// Step-6
			runMigration(conn, destSchemaName);
		}catch(Exception e) {
			System.out.println("An error occurred while running migration tool - " + e);
		}
	}
	
	private void changeMigrationStage(Connection conn, String stageKey) throws Exception {

		System.out.println("***** Change migration stage to backup *****");
		String sql = "UPDATE migrationstage SET stage_key = ?";
		ScriptRunner.runScripts(conn, sql, stageKey);
	}
	private void runMigrationScripts(String host, String port, String destSchemaName, String userName,
			String password) throws Exception {

		try (Connection conn = getConnection(host, port, destSchemaName, userName, password)) {
			String customSQLFilePath = getBasePath() + "\\custom-scripts\\" + MIGRATION_SCRIPTS_FILE_NAME;
			executeCustomSQLs(conn, customSQLFilePath);
		} catch (Exception e) {
			System.out.println("An error occurred while running product migration scripts - " + e);
			throw e;
		}

	}
	
	private void setupMigrationEnvSeperate(String host, String port, String sourceSchemaName, String userName,
			String password, String destSchemaName) {

		try(Connection conn = getConnection(host, port, sourceSchemaName, userName, password)){
			System.out.println("Connection established to mysql DB - " + sourceSchemaName + "@" + host);
			
			// Step-1
			setupMigrationEnv(conn, sourceSchemaName, destSchemaName);
	
		}catch(Exception e) {
			System.out.println("An error occurred while running migration tool - " + e);
		}
	}
	
	private void runMigrationSeperate(String host, String port, String sourceSchemaName, String userName,
			String password, String destSchemaName) {
		
		try(Connection conn = getConnection(host, port, sourceSchemaName, userName, password)){
			System.out.println("Connection established to mysql DB - " + sourceSchemaName + "@" + host);
			
			// Step-1
			runMigration(conn, destSchemaName);
	
		}catch(Exception e) {
			System.out.println("An error occurred while running migration tool - " + e);
		}
	}

	private void setupMigrationEnv(Connection conn, String schemaName, String newSchemaName) throws Exception {
			
			String triggerSQLFilePath = getBasePath() + "\\tool-generated-scripts\\"+ TRIGGERS_FILE_NAME;
			String customSQLFilePath = getBasePath() + "\\custom-scripts\\"+ MIGRATION_REF_CHANGES_FILE_NAME;

			// Step-1
			createMigrationReferences(conn, schemaName, newSchemaName);
			
			// Step-2
			executeCustomSQLs(conn, customSQLFilePath);
			
			// Step-3
			prepareAndCreateTriggers(conn, newSchemaName, triggerSQLFilePath);
	}
	private void prepareAndCreateTriggers(Connection conn, String newSchemaName, String filePath) throws Exception {

		System.out.println("***** Preparing Trigger SQLs *****");
		ScriptGenerator scriptGenerator = new ScriptGenerator(newSchemaName);
		scriptGenerator.prepareTriggerSQLs(conn);

		System.out.println("***** Writing Trigger SQLs to file *****");
		writeToFile(filePath, scriptGenerator.getDropTriggerSQLs(), scriptGenerator.getCreateTriggerSQLs());

		System.out.println("***** Executing triggers scripts in database *****");
		ScriptRunner.runScriptsFromFilePath(conn, filePath);
	}
	
	private void executeCustomSQLs(Connection conn, String filePath) throws Exception {
		
		System.out.println("***** Executing Custom scripts in database *****");
		ScriptRunner.runScriptsFromFilePath(conn, filePath);
	}

	private void createMigrationReferences(Connection conn, String schemaName, String newSchemaName) throws Exception{
		
		InputStream scriptInputStream = ScriptGenerator.class.getClassLoader().getResourceAsStream("migration_scripts.sql");
		
		System.out.println("***** Executing migration scripts in database *****");
		ScriptRunner.runScriptsFromFileStream(conn, scriptInputStream);

		System.out.println("***** Calling migration procedure in database *****");
		String sql = "call PrepareMigrateScriptsForAllTables(?, ?)";
		ScriptRunner.callStoredProcedure(conn, sql, schemaName, newSchemaName);
	}
	
	private Connection getConnection(String host, String port, String schemaName, String userId, String pass) throws Exception {
		
		Class.forName("com.mysql.jdbc.Driver");
		
		// "jdbc:mysql://" + host + ":" + port + "/" + schemaName
		String dbURL = "jdbc:mysql://" + host + ":" + port + "/" + schemaName;
		return DriverManager.getConnection(dbURL, userId, pass);
	}

	private void runMigration(Connection conn, String newSchemaName) throws Exception {
			System.out.println("***** Calling migration procedure in database *****");
			String sql = "call migratechangelogs(?)";
			ScriptRunner.callStoredProcedure(conn, sql, newSchemaName);
	}
	
	private String getBasePath() throws URISyntaxException {
		
		return new File(ClassLoader.getSystemClassLoader().getResource(".").getPath()).getPath();
	}


	public void writeToFile(String filePath, List<String> dropTriggerSQLs, List<String> createTriggerSQLs) throws IOException {
		Path file = Paths.get(filePath);
		Files.deleteIfExists(file);
		Files.createFile(file);

		List<String> triggerSQLs = new ArrayList<String>();
		triggerSQLs.add("DELIMITER $$");
		triggerSQLs.add("\n");
		triggerSQLs.addAll(dropTriggerSQLs);
		triggerSQLs.add("\n");
		triggerSQLs.addAll(createTriggerSQLs);
		triggerSQLs.add("DELIMITER ;");
		Files.write(file, triggerSQLs, StandardCharsets.UTF_8, StandardOpenOption.APPEND);

        System.out.println("SQLs written to file - " + filePath);
	}

	private void exportDatabaseDump(String schemaName, String userId, String pass, String newSchemaName) throws Exception {
		try {
			System.out.println("***** Exporting MySQL DB dump *****");
			
			String mysqlPath = toolProperties.getMysql().getClientPath();
			
			String sqlDumpFilePath = getBasePath() + "\\tool-generated-scripts\\" + SQL_DUMP_FILE_NAME;
			String dumpCmd = "cmd.exe /C echo CREATE DATABASE  IF NOT EXISTS `" + newSchemaName + "`; USE `" + newSchemaName
					+ "`; > " + sqlDumpFilePath + " "
					+ "&& cmd.exe /C \"" + mysqlPath + "\\mysqldump\" -u " + userId + " -p" + pass
					+ " " + schemaName + " " + "--routines --skip-triggers --ignore-table=" + schemaName
					+ ".migrationreference --ignore-table=" + schemaName + ".migrationchangelog " + "--ignore-table="
					+ schemaName + ".migrationstage >> " + sqlDumpFilePath + " "
					+ "&& cmd.exe /C echo DROP PROCEDURE IF EXISTS PrepareMigrateScripts;DROP PROCEDURE IF EXISTS PrepareMigrateScriptsForAllTables;"
					+ "DROP PROCEDURE IF EXISTS migratechangelogs;DROP PROCEDURE IF EXISTS migraterow; >> "
					+ sqlDumpFilePath;

			Runtime runtime = Runtime.getRuntime();
			RuntimeExec rte = new RuntimeExec();
			
			Process proc = runtime.exec(dumpCmd);
			StreamWrapper error = rte.getStreamWrapper(proc.getErrorStream(), "ERROR");
			StreamWrapper output = rte.getStreamWrapper(proc.getInputStream(), "OUTPUT");

			error.start();
			output.start();
			error.join(3000);
			output.join(3000);
			int processExitValue = proc.waitFor();

			System.out.println("Output: " + (StringUtils.isEmptyOrWhitespaceOnly(output.getMessage()) ? "DONE" : output.getMessage()));
            if (processExitValue == 0) {
            	if(!StringUtils.isEmptyOrWhitespaceOnly(error.getMessage()))
            		System.out.println("Warning: " + error.getMessage());
                System.out.println("DB Backup created successfully under - " + sqlDumpFilePath);

            } else {
            	if(!StringUtils.isEmptyOrWhitespaceOnly(error.getMessage()))
            		System.out.println("Error: " + error.getMessage());
            	System.out.println("Could not create the DB backup!");
            	throw new RuntimeException("Error creating backup DB from dump!!");
            }
		} catch (Exception e) {
			System.out.println("Exception occurred creating DB backup - " + e);
        	throw e;
        }
	}

	private void importDatabaseDump(String schemaName, String userId, String pass) throws Exception {
		try {
			System.out.println("***** Importing MySQL DB dump *****");
			
			String mysqlPath = toolProperties.getMysql().getClientPath();
			
			String sqlDumpFilePath = getBasePath() + "\\tool-generated-scripts\\" + SQL_DUMP_FILE_NAME;
			String dumpCmd = "cmd.exe /C \"" + mysqlPath + "\\mysql\" -u " + userId + " -p" + pass
					+ " " + schemaName + " < " + sqlDumpFilePath;

			Runtime runtime = Runtime.getRuntime();
			RuntimeExec rte = new RuntimeExec();
			
			Process proc = runtime.exec(dumpCmd);
			StreamWrapper error = rte.getStreamWrapper(proc.getErrorStream(), "ERROR");
			StreamWrapper output = rte.getStreamWrapper(proc.getInputStream(), "OUTPUT");

			error.start();
			output.start();
			error.join(3000);
			output.join(3000);
			int processExitValue = proc.waitFor();
			
			System.out.println("Output: " + (StringUtils.isEmptyOrWhitespaceOnly(output.getMessage()) ? "DONE" : output.getMessage()));
            if (processExitValue == 0) {
            	if(!StringUtils.isEmptyOrWhitespaceOnly(error.getMessage()))
            		System.out.println("Warning: " + error.getMessage());
                System.out.println("DB Restored successfully from - " + sqlDumpFilePath);
            } else {
            	if(!StringUtils.isEmptyOrWhitespaceOnly(error.getMessage()))
            		System.out.println("Error: " + error.getMessage());
            	System.out.println("Could not restore the DB dump!");
            	throw new RuntimeException("Error restoring DB from dump!!");
            }
		} catch (Exception e) {
			System.out.println("Exception occurred restoring DB dump - " + e);
        	throw e;
        }
	}
}
