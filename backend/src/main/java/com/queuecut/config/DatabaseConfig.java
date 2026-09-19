package com.queuecut.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import javax.sql.DataSource;
import java.net.URI;

/**
 * Production DataSource configuration.
 * Automatically parses Render / Supabase / Heroku postgresql:// or postgres:// connection strings
 * into valid JDBC URLs (jdbc:postgresql://...) and extracts credentials if needed.
 */
@Configuration
@Profile("prod")
public class DatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(DatabaseConfig.class);

    @Value("${spring.datasource.url:${DATABASE_URL:}}")
    private String rawUrl;

    @Value("${spring.datasource.username:${DATABASE_USERNAME:}}")
    private String rawUsername;

    @Value("${spring.datasource.password:${DATABASE_PASSWORD:}}")
    private String rawPassword;

    @Value("${spring.datasource.hikari.maximum-pool-size:3}")
    private int maxPoolSize;

    @Bean
    @Primary
    public DataSource dataSource() {
        HikariConfig config = new HikariConfig();

        String jdbcUrl = rawUrl;
        String username = rawUsername;
        String password = rawPassword;

        if (rawUrl != null && !rawUrl.isBlank()) {
            if (rawUrl.startsWith("postgres://") || rawUrl.startsWith("postgresql://")) {
                try {
                    // Standard URI format: postgresql://username:password@host:port/database
                    String cleanUriStr = rawUrl.startsWith("postgres://")
                            ? "http://" + rawUrl.substring("postgres://".length())
                            : "http://" + rawUrl.substring("postgresql://".length());
                    URI uri = URI.create(cleanUriStr);

                    String host = uri.getHost();
                    int port = uri.getPort() == -1 ? 5432 : uri.getPort();
                    String path = uri.getPath();
                    String dbName = (path != null && path.length() > 1) ? path.substring(1) : "";

                    if (uri.getUserInfo() != null) {
                        String[] userInfo = uri.getUserInfo().split(":", 2);
                        if (username == null || username.isBlank()) {
                            username = userInfo[0];
                        }
                        if (userInfo.length > 1 && (password == null || password.isBlank())) {
                            password = userInfo[1];
                        }
                    }

                    jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
                    log.info("Converted Render DATABASE_URL to JDBC URL format: jdbc:postgresql://{}:{}/{}", host, port, dbName);
                } catch (Exception e) {
                    log.warn("Failed to parse DATABASE_URL as URI, prepending 'jdbc:' prefix: {}", e.getMessage());
                    if (!rawUrl.startsWith("jdbc:")) {
                        jdbcUrl = "jdbc:" + rawUrl;
                    }
                }
            }
        }

        config.setJdbcUrl(jdbcUrl);
        if (username != null && !username.isBlank()) {
            config.setUsername(username);
        }
        if (password != null && !password.isBlank()) {
            config.setPassword(password);
        }
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(maxPoolSize);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(30000);
        config.setIdleTimeout(600000);
        config.setMaxLifetime(1800000);

        return new HikariDataSource(config);
    }
}
