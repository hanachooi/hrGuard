package dev.common.configuration;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateProperties;
import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.Objects;

import static dev.common.configuration.DataSourceConfig.DOMAIN_DATASOURCE;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties({JpaProperties.class, HibernateProperties.class})
public class TransactionManagerConfig {
    public static final String DOMAIN_ENTITY_MANAGER_FACTORY = "domainEntityManagerFactory";
    public static final String DOMAIN_TRANSACTION_MANAGER = "domainTransactionManager";

    private final JpaProperties jpaProperties;
    private final HibernateProperties hibernateProperties;
    private final ObjectProvider<Collection<DataSourcePoolMetadataProvider>> metadataProviders;
    private final EntityManagerFactoryBuilder entityManagerFactoryBuilder;

    @Bean(name = DOMAIN_ENTITY_MANAGER_FACTORY)
    public LocalContainerEntityManagerFactoryBean domainEntityManagerFactory(
            @Qualifier(DOMAIN_DATASOURCE) DataSource dataSource
    ) {
        return EntityManagerFactoryCreator.builder()
                .properties(jpaProperties)
                .hibernateProperties(hibernateProperties)
                .metadataProviders(metadataProviders)
                .entityManagerFactoryBuilder(entityManagerFactoryBuilder)
                .dataSource(dataSource)
                .packages("dev")
                .persistenceUnit("domainUnit")
                .build()
                .create();
    }

    @Primary
    @Bean(name = DOMAIN_TRANSACTION_MANAGER)
    public PlatformTransactionManager domainTransactionManager(
            @Qualifier(DOMAIN_ENTITY_MANAGER_FACTORY) LocalContainerEntityManagerFactoryBean entityManagerFactory
    ) {
        return new JpaTransactionManager(Objects.requireNonNull(entityManagerFactory.getObject()));
    }

    @Configuration
    @EnableJpaRepositories(
            basePackages = {
                    "dev.commute.repository",
                    "dev.member.repository",
                    "dev.payroll.repository",
                    "dev.payrollpolicy.repository",
                    "dev.holiday.repository",
                    "dev.workrecord.repository",
                    "dev.workschedule.repository",
                    "dev.businesstrip.repository",
                    "dev.fieldwork.repository",
                    "dev.leave.repository",
                    "dev.test"
            },
            entityManagerFactoryRef = DOMAIN_ENTITY_MANAGER_FACTORY,
            transactionManagerRef = DOMAIN_TRANSACTION_MANAGER
    )
    public static class DomainJpaRepositoriesConfig {
    }
}
