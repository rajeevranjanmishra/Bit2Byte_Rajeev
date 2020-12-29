package com.softwareag.um.property;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties
@PropertySource(value = { "classpath:tool.properties" })
public class ToolProperties {

	private MySQL mysql;
	private Schema sourceSchema;
	private Schema destinationSchema;

	public MySQL getMysql() {
		return mysql;
	}

	public void setMysql(MySQL mysql) {
		this.mysql = mysql;
	}

	public Schema getSourceSchema() {
		return sourceSchema;
	}

	public void setSourceSchema(Schema sourceSchema) {
		this.sourceSchema = sourceSchema;
	}

	public Schema getDestinationSchema() {
		return destinationSchema;
	}

	public void setDestinationSchema(Schema destinationSchema) {
		this.destinationSchema = destinationSchema;
	}

	public static class MySQL {
		private String clientPath;
		private String host;
		private String port;
		private String username;
		private String password;

		public String getClientPath() {
			return clientPath;
		}

		public void setClientPath(String clientPath) {
			this.clientPath = clientPath;
		}

		public String getHost() {
			return host;
		}

		public void setHost(String host) {
			this.host = host;
		}

		public String getPort() {
			return port;
		}

		public void setPort(String port) {
			this.port = port;
		}

		public String getUsername() {
			return username;
		}

		public void setUsername(String username) {
			this.username = username;
		}

		public String getPassword() {
			return password;
		}

		public void setPassword(String password) {
			this.password = password;
		}

		@Override
		public String toString() {
			return "MySQL [clientPath=" + clientPath + ", host=" + host + ", port=" + port + ", username=" + username
					+ ", password=" + password + "]";
		}
	}

	public static class Schema {
		private String name;
		private String version;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getVersion() {
			return version;
		}

		public void setVersion(String version) {
			this.version = version;
		}

		@Override
		public String toString() {
			return "Schema [name=" + name + ", version=" + version + "]";
		}
	}

	@Override
	public String toString() {
		return "ToolProperties [mysql=" + mysql + ", sourceSchema=" + sourceSchema + ", destinationSchema="
				+ destinationSchema + "]";
	}

}
