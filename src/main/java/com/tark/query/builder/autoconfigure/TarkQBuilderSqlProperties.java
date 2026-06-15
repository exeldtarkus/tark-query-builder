package com.tark.query.builder.autoconfigure;

import com.tark.query.builder.sql.TarkQBuilderDialect;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SQL dialect configuration via application.properties:
 *
 *   tark.query.dialect=mysql       (default)
 *   tark.query.dialect=postgresql
 *   tark.query.dialect=sqlserver
 */
@ConfigurationProperties(prefix = "tark.query")
public class TarkQBuilderSqlProperties {

    private TarkQBuilderDialect dialect = TarkQBuilderDialect.MYSQL;

    public TarkQBuilderDialect getDialect() {
        return dialect;
    }

    public void setDialect(TarkQBuilderDialect dialect) {
        this.dialect = dialect;
    }
}
