package com.softwareag.um.dao.impl;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.softwareag.um.dao.UpgradeManagerDAO;
import com.softwareag.um.migrator.DatabaseMigrator;
import com.softwareag.um.model.TenantDetails;
import com.softwareag.um.model.TenantMigrationStatus;
import com.softwareag.um.property.ToolProperties;

@Service
public class UpgradeManagerDAOImpl implements UpgradeManagerDAO {
	
    private static final Logger logger = LogManager.getLogger(UpgradeManagerDAOImpl.class);
    
	@Autowired
	ToolProperties toolProps;

	@Autowired
	DatabaseMigrator migrator;
	
	@Override
	public ToolProperties getConfig() throws Exception {
		logger.info("Request to fetch tool configurations !!");
		return toolProps;
	}

	@Override
	public TenantDetails upgradeDB(TenantDetails tenantDetails) throws Exception {
		TenantDetails output = new TenantDetails();
		List<String> tenantIds = tenantDetails.getTenantIds();
		for(String tenantId : tenantIds) {
			TenantMigrationStatus migrationStatus = new TenantMigrationStatus();
			try {
				migrationStatus.setTenantId(tenantId);
				migrator.startMigration(tenantId);
				migrationStatus.setStatus("SUBMITTED");
			}catch(Exception e) {
				logger.error("Error occurred while starting the migration for tenantId: " + tenantId);
				migrationStatus.setStatus("FAILED");
				migrationStatus.setErrorMessage("Migration request failed to start with exception - " + e.getMessage());
			}

			output.setMigrationStatus(migrationStatus);
		}
		return output;
	}

	@Override
	public TenantDetails checkStatus(TenantDetails tenantDetails) throws Exception {
		return null;
	}

}
