package dev.batch.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.batch.BatchDataSource;
import org.springframework.boot.autoconfigure.batch.BatchTransactionManager;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.LazyConnectionDataSourceProxy;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;

@Configuration
public class BatchDataSourceConfig {
    public static final String META_DATASOURCE = "metaDataSource";
    public static final String META_TRANSACTION_MANAGER = "metaTransactionManager";

    @Bean
    @ConfigurationProperties(prefix = "spring.datasource.meta.hikari")
    public HikariConfig metaHikariConfig() {
        return new HikariConfig();
    }

    @Bean(META_DATASOURCE)
    @BatchDataSource
    public DataSource metaDataSource() {
        return new LazyConnectionDataSourceProxy(new HikariDataSource(metaHikariConfig()));
    }

    @Bean(META_TRANSACTION_MANAGER)
    @BatchTransactionManager
    public PlatformTransactionManager metaTransactionManager(
            @Qualifier(META_DATASOURCE) DataSource dataSource
    ) {
        return new DataSourceTransactionManager(dataSource);
    }
}
