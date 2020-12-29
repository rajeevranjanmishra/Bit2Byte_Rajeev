package com.softwareag.um.script.generator;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ScriptGenerator {
	
	private String NEW_SCHEMA_NAME = null;

	private List<String> dropTriggerSQLs = new ArrayList<String>();

	private List<String> createTriggerSQLs = new ArrayList<String>();
	
	public ScriptGenerator(String newSchemaName) {
		if(null == newSchemaName || newSchemaName.length() == 0) {
			throw new RuntimeException("New Schema Name is mandatory!!");
		}
		
		NEW_SCHEMA_NAME = newSchemaName;
	}
	
	public List<String> getDropTriggerSQLs() {
		return dropTriggerSQLs;
	}
	
	public List<String> getCreateTriggerSQLs() {
		return createTriggerSQLs;
	}
	
	public void prepareTriggerSQLs(Connection conn) throws Exception {
		
		Class.forName("com.mysql.jdbc.Driver");
		try (Statement stmt = conn.createStatement()) {

			ResultSet rs = stmt.executeQuery("select REF_TABLE_NAME, UNIQUE_FIELDS, INSERT_SQL, UPDATE_SQL, DELETE_SQL from migrationreference");
			while (rs.next()) {
				String tableName = rs.getString(1);
				
				String insertSQL = rs.getString(3);
				String updateSQL = rs.getString(4);
				String deleteSQL = rs.getString(5);

				String columnWhereCondition = prepareColumnWhereCondition(rs.getString(2), false);
				String whereCondition = prepareWhereCondition(rs.getString(2), false);

				String columnWhereConditionForDelete = prepareColumnWhereCondition(rs.getString(2), true);
				String whereConditionForDelete = prepareWhereCondition(rs.getString(2), true);
				
				dropTriggerSQLs.add(prepareDropTriggerSQL(tableName, "INSERT"));
				createTriggerSQLs.add(prepareInsertTriggerSQL(tableName, columnWhereCondition, insertSQL, whereCondition));

				dropTriggerSQLs.add(prepareDropTriggerSQL(tableName, "UPDATE"));
				createTriggerSQLs.add(prepareUpdateTriggerSQL(tableName, columnWhereCondition, updateSQL, whereCondition));

				dropTriggerSQLs.add(prepareDropTriggerSQL(tableName, "DELETE"));
				createTriggerSQLs.add(prepareDeleteTriggerSQL(tableName, columnWhereConditionForDelete, deleteSQL, whereConditionForDelete));
			}
			System.out.println("Trigger SQLs prepared successfully!!");
			
		} catch (Exception e) {
			System.out.println("Exception occurred creating Trigger SQLs - " + e);
			throw e;
		}
	}
	
	private String prepareDropTriggerSQL(String tableName, String operation) {
			return "DROP TRIGGER IF EXISTS " + tableName + "_AFTER_" + operation + "$$";
	}
	
	private String prepareWhereCondition(String uniqueFields, boolean forDelete) {
		
		StringBuilder sb = new StringBuilder();
		
		String[] columnArr = uniqueFields.split(",");
		for (String column : columnArr) {
			if (sb.length() > 0) {
				sb.append(" AND ");
			}
			if (forDelete) {
				sb.append(column + " = OLD." + column);
			} else {
				sb.append(column + " = NEW." + column);
			}
		}
		return sb.toString();
	}

	private String prepareColumnWhereCondition(String uniqueFields, boolean forDelete) {
		
		StringBuilder cond = new StringBuilder("CONCAT(");
		
		String[] columnArr = uniqueFields.split(",");
		for (String column : columnArr) {
			if (cond.length() > 7) {
				cond.append(", ' AND ', ");
			}
			if (forDelete) {
				cond.append("'" + column + " = \\'', OLD." + column + ", '\\''");
			} else {
				cond.append("'" + column + " = \\'', NEW." + column + ", '\\''");
			}
		}
		cond.append(")");
		return cond.toString();
	}

	private String prepareInsertTriggerSQL(String tableName,String columnWhereCondition, String insertSQL, String whereCondition) {
		String triggerSQL = "CREATE TRIGGER `" + tableName + "_AFTER_INSERT` AFTER INSERT ON `" + tableName + "` \n" +
		"FOR EACH ROW \n" +
		"BEGIN\n" +
		"  SET @CHANGE_TABLE_NAME = '" + tableName + "';\n" +
		"  SET @CHANGE_OPERATION = 'INSERT';\n" +
		"  SET @WHERE_CONDITION = " + columnWhereCondition + ";\n" +
		
		"  SET @stage_key = '';\n" +
		"  SELECT stage_key into @stage_key from migrationstage;\n" +
		"  IF(@stage_key IS NOT NULL AND @stage_key = 'BACKUP') THEN\n" +
		"	UPDATE MigrationChangeLog SET PROCESSED_FLAG = 2 WHERE CHANGE_TABLE_NAME = @CHANGE_TABLE_NAME AND WHERE_CONDITION = @WHERE_CONDITION AND PROCESSED_FLAG = 0;\n" +
		"   INSERT INTO MigrationChangeLog(CHANGE_TABLE_NAME,CHANGE_OPERATION,WHERE_CONDITION) VALUES (@CHANGE_TABLE_NAME, @CHANGE_OPERATION, @WHERE_CONDITION);\n" +
		"  ELSEIF(@stage_key IS NOT NULL AND @stage_key = 'MIGRATE') THEN\n" +
		"  	IF (SELECT count(*) from `" + NEW_SCHEMA_NAME + "`." + tableName + " where " + whereCondition + ") = 0 THEN\n" +
		"		UPDATE MigrationChangeLog SET PROCESSED_FLAG = 3 WHERE CHANGE_TABLE_NAME = @CHANGE_TABLE_NAME AND WHERE_CONDITION = @WHERE_CONDITION AND PROCESSED_FLAG = 0;\n" +
		"  		" + insertSQL +	" WHERE " +	whereCondition + ";\n" +
		"  	ELSE\n" +
		"		UPDATE MigrationChangeLog SET PROCESSED_FLAG = 2 WHERE CHANGE_TABLE_NAME = @CHANGE_TABLE_NAME AND WHERE_CONDITION = @WHERE_CONDITION AND PROCESSED_FLAG = 0;\n" +
		"    	INSERT INTO MigrationChangeLog(CHANGE_TABLE_NAME,CHANGE_OPERATION,WHERE_CONDITION) VALUES (@CHANGE_TABLE_NAME, @CHANGE_OPERATION, @WHERE_CONDITION);\n" +
		"  	END IF;\n" +
		"  END IF;\n" +
		
		"END$$\n\n";
		return triggerSQL;
	}

	private String prepareUpdateTriggerSQL(String tableName,String columnWhereCondition, String updateSQL, String whereCondition) {
		String triggerSQL = "CREATE TRIGGER `" + tableName + "_AFTER_UPDATE` AFTER UPDATE ON `" + tableName + "` \n" +
		"FOR EACH ROW \n" +
		"BEGIN\n" +
		"  SET @CHANGE_TABLE_NAME = '" + tableName + "';\n" +
		"  SET @CHANGE_OPERATION = 'UPDATE';\n" +
		"  SET @WHERE_CONDITION = " + columnWhereCondition + ";\n" +
		
		"  SET @stage_key = '';\n" +
		"  SELECT stage_key into @stage_key from migrationstage;\n" +
		"  IF(@stage_key IS NOT NULL AND @stage_key = 'BACKUP') THEN\n" +
//        "  	IF (SELECT COUNT(*) FROM MigrationChangeLog WHERE CHANGE_TABLE_NAME = @CHANGE_TABLE_NAME AND WHERE_CONDITION = @WHERE_CONDITION AND PROCESSED_FLAG = 0) = 0 THEN\n" +
		"  		INSERT INTO MigrationChangeLog(CHANGE_TABLE_NAME,CHANGE_OPERATION,WHERE_CONDITION) VALUES (@CHANGE_TABLE_NAME, @CHANGE_OPERATION, @WHERE_CONDITION);\n" +
//		"  	END IF;\n" +
		"  ELSEIF(@stage_key IS NOT NULL AND @stage_key = 'MIGRATE') THEN\n" +
		"  	IF (SELECT count(*) from `" + NEW_SCHEMA_NAME + "`." + tableName + " where " + whereCondition + ") > 0 THEN\n" +
		"		UPDATE MigrationChangeLog SET PROCESSED_FLAG = 3 WHERE CHANGE_TABLE_NAME = @CHANGE_TABLE_NAME AND WHERE_CONDITION = @WHERE_CONDITION AND PROCESSED_FLAG = 0;\n" +
		"  		" + updateSQL +	" WHERE " +	("o." + whereCondition).replaceAll("AND ", "AND o.") + ";\n" +
		"  	ELSE\n" +
//        "  		IF (SELECT COUNT(*) FROM MigrationChangeLog WHERE CHANGE_TABLE_NAME = @CHANGE_TABLE_NAME AND WHERE_CONDITION = @WHERE_CONDITION AND PROCESSED_FLAG = 0) = 0 THEN\n" +
		"  			INSERT INTO MigrationChangeLog(CHANGE_TABLE_NAME,CHANGE_OPERATION,WHERE_CONDITION) VALUES (@CHANGE_TABLE_NAME, @CHANGE_OPERATION, @WHERE_CONDITION);\n" +
//		" 		END IF;\n" +
		"  	END IF;\n" +
		"  END IF;\n" +
		
		"END$$\n\n";
		return triggerSQL;
	}

	private String prepareDeleteTriggerSQL(String tableName, String columnWhereCondition, String deleteSQL, String whereCondition) {
		String triggerSQL = "CREATE TRIGGER `" + tableName + "_AFTER_DELETE` AFTER DELETE ON `" + tableName + "` \n" +
		"FOR EACH ROW \n" +
		"BEGIN\n" +
		"  SET @CHANGE_TABLE_NAME = '" + tableName + "';\n" +
		"  SET @CHANGE_OPERATION = 'DELETE';\n" +
		"  SET @WHERE_CONDITION = " + columnWhereCondition + ";\n" +
		
		"  SET @stage_key = '';\n" +
		"  SELECT stage_key into @stage_key from migrationstage;\n" +
		"  IF(@stage_key IS NOT NULL AND @stage_key = 'BACKUP') THEN\n" +
		"	UPDATE MigrationChangeLog SET PROCESSED_FLAG = 2 WHERE CHANGE_TABLE_NAME = @CHANGE_TABLE_NAME AND WHERE_CONDITION = @WHERE_CONDITION AND PROCESSED_FLAG = 0;\n" +
		"	INSERT INTO MigrationChangeLog(CHANGE_TABLE_NAME,CHANGE_OPERATION,WHERE_CONDITION) VALUES (@CHANGE_TABLE_NAME, @CHANGE_OPERATION, @WHERE_CONDITION);\n" +
		"  ELSEIF(@stage_key IS NOT NULL AND @stage_key = 'MIGRATE') THEN\n" +
		"  	IF (SELECT count(*) from `" + NEW_SCHEMA_NAME + "`." + tableName + " where " + whereCondition + ") > 0 THEN\n" +
		"		UPDATE MigrationChangeLog SET PROCESSED_FLAG = 3 WHERE CHANGE_TABLE_NAME = @CHANGE_TABLE_NAME AND WHERE_CONDITION = @WHERE_CONDITION AND PROCESSED_FLAG = 0;\n" +
		"  		" + deleteSQL +	" WHERE " + whereCondition + ";\n" +
		"  	ELSE\n" +
		"		UPDATE MigrationChangeLog SET PROCESSED_FLAG = 2 WHERE CHANGE_TABLE_NAME = @CHANGE_TABLE_NAME AND WHERE_CONDITION = @WHERE_CONDITION AND PROCESSED_FLAG = 0;\n" +
		"  		INSERT INTO MigrationChangeLog(CHANGE_TABLE_NAME,CHANGE_OPERATION,WHERE_CONDITION) VALUES (@CHANGE_TABLE_NAME, @CHANGE_OPERATION, @WHERE_CONDITION);\n" +
		"  	END IF;\n" +
		"  END IF;\n" +

		"END$$\n\n";
		return triggerSQL;
	}

}
