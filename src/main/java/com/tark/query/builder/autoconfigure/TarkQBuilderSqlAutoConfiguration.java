package com.tark.query.builder.autoconfigure;

import com.tark.query.builder.sql.TarkQBuilderFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@AutoConfiguration
@ConditionalOnClass(NamedParameterJdbcTemplate.class)
@EnableConfigurationProperties(TarkQBuilderSqlProperties.class)
public class TarkQBuilderSqlAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public TarkQBuilderFactory tarkQBuilderFactory(TarkQBuilderSqlProperties properties) {
        return new TarkQBuilderFactory(properties.getDialect());
    }
}
