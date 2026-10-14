package com.ccm.common.multitenant;

import com.ccm.common.entity.ClientDetails;
import com.ccm.common.repository.ClientDetailsRepository;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.DependsOn;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@DependsOn("multiTenantDataSource")
public class TenantDataSourceInitializer implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(TenantDataSourceInitializer.class);

    private final ClientDetailsRepository clientDetailsRepository;
    private final AbstractRoutingDataSource routingDataSource;
    private final Map<String, DataSource> tenantDataSources = new ConcurrentHashMap<>();

    public TenantDataSourceInitializer(ClientDetailsRepository clientDetailsRepository,
                                       @Qualifier("multiTenantDataSource") DataSource dataSource) {
        this.clientDetailsRepository = clientDetailsRepository;
        this.routingDataSource = (AbstractRoutingDataSource) dataSource;
    }

    @Override
    public void run(ApplicationArguments args) {
        if(logger.isDebugEnabled()) {
            logger.debug(">>> ApplicationRunner run() method called for Tenant Initialization");
        }
        Map<Object, Object> newTargetDataSources = new ConcurrentHashMap<>();

        List<ClientDetails> clients = clientDetailsRepository.findAll();

        if(logger.isDebugEnabled()) {
            logger.debug(">>> Retrieved {} client(s) from ClientDetailsRepository", clients.size());
        }

        for (ClientDetails client : clients) {
            String clientId = client.getClientId();
            try {
                DataSource dataSource = tenantDataSources.computeIfAbsent(clientId, id -> {
                    try {
                        String url = client.getDbUrl();
                        String username = client.getDbUsername();
                        String password = client.getDbPassword();

                        if(logger.isDebugEnabled()) {
                            logger.debug(">>> Initializing DataSource for clientId: {}, clientId");
                        }
                        return createHikariDataSource(clientId, url, username, password);
                    } catch (Exception e) {
                        logger.error("Error creating datasource for clientId: {}", clientId, e);
                        return null;
                    }
                });

                if (dataSource != null) {
                    newTargetDataSources.put(clientId, dataSource);
                } else {
                    logger.warn("DataSource for clientId '{}' was null and skipped.", clientId);
                }

            } catch (Exception ex) {
                logger.error("Unexpected error processing clientId: {}", clientId, ex);
            }
        }

        if (!newTargetDataSources.isEmpty()) {
            routingDataSource.setTargetDataSources(newTargetDataSources);
            routingDataSource.afterPropertiesSet(); // reloads the resolved DataSources

            if(logger.isDebugEnabled()) {
                logger.debug("Initialized {} tenant DataSources", newTargetDataSources.size());
            }
        } else {
            logger.warn("No tenant DataSources initialized.");
        }
    }

    private HikariDataSource createHikariDataSource(String clientId, String url, String username, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("HikariPool-" + clientId);
        config.setJdbcUrl(url);
        config.setUsername(username);
        config.setPassword(password);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(5);
        config.setMinimumIdle(2);
        config.setIdleTimeout(30000);
        config.setMaxLifetime(600000);
        return new HikariDataSource(config);
    }

    @PreDestroy
    public void shutdownAll() {
        if(logger.isDebugEnabled()) {
            logger.debug(">>> Shutting down all tenant DataSources...");
        }
        tenantDataSources.forEach((key, ds) -> {
            if (ds instanceof HikariDataSource hikari) {
                try {
                    hikari.close();
                    if(logger.isDebugEnabled()) {
                        logger.debug("Closed HikariDataSource for tenant: {}", key);
                    }
                } catch (Exception ex) {
                    logger.error("Error while closing HikariDataSource for tenant: {}", key, ex);
                }
            }
        });
    }
}

