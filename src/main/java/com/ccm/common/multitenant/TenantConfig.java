package com.ccm.common.multitenant;

import com.ccm.common.repository.ClientDetailsRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class TenantConfig {

    @Bean
    public TenantDataSourceInitializer tenantDataSourceInitializer(
            ClientDetailsRepository repo,
            @Qualifier("multiTenantDataSource") DataSource dataSource) {
        return new TenantDataSourceInitializer(repo, dataSource);
    }
}
