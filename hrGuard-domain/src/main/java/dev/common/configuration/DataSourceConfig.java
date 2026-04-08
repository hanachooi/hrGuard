package dev.common.configuration;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;

import javax.sql.DataSource;

@Configuration
public class DataSourceConfig {
    public static final String DOMAIN_DATASOURCE = "domainDataSource";

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.domain.hikari")
    public HikariConfig domainHikariConfig() {
        return new HikariConfig();
    }

    @Primary
    @Bean(DOMAIN_DATASOURCE)
    public DataSource domainDataSource() {
        return new LazyConnectionDataSourceProxy(new HikariDataSource(domainHikariConfig()));
    }
}
