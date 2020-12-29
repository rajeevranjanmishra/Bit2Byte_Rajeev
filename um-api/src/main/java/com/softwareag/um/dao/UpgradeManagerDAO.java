package com.softwareag.um.dao;

import com.softwareag.um.model.TenantDetails;
import com.softwareag.um.property.ToolProperties;

public interface UpgradeManagerDAO {

	public ToolProperties getConfig() throws Exception;

	public TenantDetails upgradeDB(TenantDetails tenantDetails) throws Exception;

	public TenantDetails checkStatus(TenantDetails tenantDetails) throws Exception;
}
