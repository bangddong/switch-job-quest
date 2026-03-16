package com.devquest.storage.db.core.config

import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import org.springframework.transaction.annotation.EnableTransactionManagement

@Configuration
@EnableTransactionManagement
@EnableJpaRepositories(basePackages = ["com.devquest.storage.db.core"])
class CoreJpaConfig
