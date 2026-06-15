package com.tark.query.builder.autoconfigure;

import com.tark.query.builder.sql.TarkQBuilderDialect;
import com.tark.query.builder.sql.TarkQBuilderFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class TarkQBuilderAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(TarkQBuilderSqlAutoConfiguration.class));

    @Test
    void tarkQBuilderFactoryBeanTerdaftarOtomatis() {
        contextRunner.run(context ->
                assertThat(context).hasSingleBean(TarkQBuilderFactory.class)
        );
    }

    @Test
    void defaultDialectAdalaMySQL() {
        contextRunner.run(context -> {
            TarkQBuilderFactory factory = context.getBean(TarkQBuilderFactory.class);
            assertThat(factory.getDialect()).isEqualTo(TarkQBuilderDialect.MYSQL);
        });
    }

    @Test
    void dialectDapatDiubahKeSqlServer() {
        contextRunner
                .withPropertyValues("tark.query.dialect=SQLSERVER")
                .run(context -> {
                    TarkQBuilderFactory factory = context.getBean(TarkQBuilderFactory.class);
                    assertThat(factory.getDialect()).isEqualTo(TarkQBuilderDialect.SQLSERVER);
                });
    }

    @Test
    void dialectDapatDiubahKePostgresql() {
        contextRunner
                .withPropertyValues("tark.query.dialect=POSTGRESQL")
                .run(context -> {
                    TarkQBuilderFactory factory = context.getBean(TarkQBuilderFactory.class);
                    assertThat(factory.getDialect()).isEqualTo(TarkQBuilderDialect.POSTGRESQL);
                });
    }

    @Test
    void customTarkQBuilderFactoryBeanMenggantikanAutoconfiguration() {
        contextRunner
                .withUserConfiguration(CustomTarkQBuilderFactoryConfig.class)
                .run(context -> {
                    assertThat(context).hasSingleBean(TarkQBuilderFactory.class);
                    TarkQBuilderFactory factory = context.getBean(TarkQBuilderFactory.class);
                    assertThat(factory.getDialect()).isEqualTo(TarkQBuilderDialect.SQLSERVER);
                });
    }

    @Configuration
    static class CustomTarkQBuilderFactoryConfig {
        @Bean
        public TarkQBuilderFactory customTarkQBuilderFactory() {
            return new TarkQBuilderFactory(TarkQBuilderDialect.SQLSERVER);
        }
    }
}
