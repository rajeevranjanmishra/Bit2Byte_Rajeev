package com.softwareag.um.model;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;


public class TenantDetails {

	@JsonInclude(Include.NON_NULL)
	List<String> tenantIds;

	@JsonInclude(Include.NON_NULL)
	TenantMigrationStatus migrationStatus;

	public List<String> getTenantIds() {
		return tenantIds;
	}

	public void setTenantIds(List<String> tenantIds) {
		this.tenantIds = tenantIds;
	}

	public TenantMigrationStatus getMigrationStatus() {
		return migrationStatus;
	}

	public void setMigrationStatus(TenantMigrationStatus migrationStatus) {
		this.migrationStatus = migrationStatus;
	}
	
}
