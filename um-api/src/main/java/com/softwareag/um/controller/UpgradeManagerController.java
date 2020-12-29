package com.softwareag.um.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.softwareag.um.model.TenantDetails;
import com.softwareag.um.property.ToolProperties;
import com.softwareag.um.service.UpgradeManagerService;

@RestController
@RequestMapping(path = "/um/migration", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
public class UpgradeManagerController {

	@Autowired
	UpgradeManagerService service;
	
	@RequestMapping(value = "/start", method = RequestMethod.POST)
	public TenantDetails startMigration(@RequestBody TenantDetails tenantDetails) throws Exception {
		return service.upgradeDB(tenantDetails);
	}

	@GetMapping(value = "/config")
	public ToolProperties getConfig() throws Exception {
		return service.getConfig();
	}

	@RequestMapping(value = "/status", method = RequestMethod.POST)
	public TenantDetails checkStatus(@RequestBody TenantDetails tenantDetails) throws Exception {
		return service.checkStatus(tenantDetails);
	}
}
