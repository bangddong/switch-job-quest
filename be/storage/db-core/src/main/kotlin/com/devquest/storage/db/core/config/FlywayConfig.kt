package com.devquest.storage.db.core.config

import org.flywaydb.core.Flyway
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import javax.sql.DataSource

@Configuration
@Profile("prod")
class FlywayConfig {

    @Bean
    fun flyway(@Qualifier("coreDataSource") dataSource: DataSource): Flyway {
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations("classpath:db/migration")
            .load()
        flyway.repair()
        flyway.migrate()
        return flyway
    }

    @Bean
    fun flywayEntityManagerFactoryDependsOn(): EntityManagerFactoryDependsOnPostProcessor {
        return EntityManagerFactoryDependsOnPostProcessor("flyway")
    }
}
