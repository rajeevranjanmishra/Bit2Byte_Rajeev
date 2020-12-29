package com.softwareag.um.service.impl;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.softwareag.um.dao.UpgradeManagerDAO;
import com.softwareag.um.model.TenantDetails;
import com.softwareag.um.property.ToolProperties;
import com.softwareag.um.service.UpgradeManagerService;

@Service
public class UpgradeManagerServiceImpl implements UpgradeManagerService{
	
	@Autowired
	UpgradeManagerDAO dao;
	
	@Override
	public ToolProperties getConfig() throws Exception {
		return dao.getConfig();
	}

	@Override
	public TenantDetails upgradeDB(TenantDetails tenantDetails) throws Exception {
		return dao.upgradeDB(tenantDetails);
	}

	@Override
	public TenantDetails checkStatus(TenantDetails tenantDetails) throws Exception {
		return dao.checkStatus(tenantDetails);
	}

}
