package com.tark.query.builder.sql;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/**
 * Spring bean that holds the configured SQL dialect from application.properties.
 * Registered via autoconfiguration — no need to create manually.
 *
 * Usage:
 *   @Autowired TarkQBuilderFactory tark;
 *   tark.query(jdbcTemplate).from("users").fetchAll();
 */
public class TarkQBuilderFactory {

    private final TarkQBuilderDialect dialect;

    public TarkQBuilderFactory(TarkQBuilderDialect dialect) {
        this.dialect = dialect;
    }

    public TarkQBuilder query(NamedParameterJdbcTemplate jdbcTemplate) {
        return TarkQBuilder.query(jdbcTemplate, dialect);
    }

    public TarkQBuilderDialect getDialect() {
        return dialect;
    }
}
