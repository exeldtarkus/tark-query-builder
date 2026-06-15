package com.tark.query.builder.sql;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.BeanPropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.*;
import java.util.regex.Pattern;

public class TarkQBuilder {

    private static final Set<String> ALLOWED_OPERATORS = Set.of(
            "=", "!=", "<>", ">", "<", ">=", "<=", "LIKE", "NOT LIKE"
    );

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TarkQBuilderDialect dialect;

    private String table;
    private final List<String> selectColumns = new ArrayList<>();
    private final List<String> joins = new ArrayList<>();
    private final List<String> wheres = new ArrayList<>();
    private final MapSqlParameterSource params = new MapSqlParameterSource();
    private Integer limit;
    private Integer offset;
    private String orderBy;
    private final List<String> groupByColumns = new ArrayList<>();
    private String having;
    private String rawQuery;

    public TarkQBuilder(NamedParameterJdbcTemplate jdbcTemplate) {
        this(jdbcTemplate, TarkQBuilderDialect.MYSQL);
    }

    public TarkQBuilder(NamedParameterJdbcTemplate jdbcTemplate, TarkQBuilderDialect dialect) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
    }

    public static TarkQBuilder query(NamedParameterJdbcTemplate jdbcTemplate) {
        return new TarkQBuilder(jdbcTemplate);
    }

    public static TarkQBuilder query(NamedParameterJdbcTemplate jdbcTemplate, TarkQBuilderDialect dialect) {
        return new TarkQBuilder(jdbcTemplate, dialect);
    }

    public TarkQBuilder from(String table) {
        this.table = table;
        return this;
    }

    public TarkQBuilder table(String table) {
        this.table = table;
        return this;
    }

    public TarkQBuilder select(String... columns) {
        selectColumns.addAll(Arrays.asList(columns));
        return this;
    }

    public TarkQBuilder where(String column, Object value) {
        return where(column, "=", value);
    }

    public TarkQBuilder where(String column, String operator, Object value) {
        if (!ALLOWED_OPERATORS.contains(operator.toUpperCase())) {
            throw new IllegalArgumentException("Operator not allowed: " + operator);
        }
        String paramName = toParamName(column) + "_" + params.getValues().size();
        wheres.add(column + " " + operator + " :" + paramName);
        params.addValue(paramName, value);
        return this;
    }

    public TarkQBuilder whereIn(String column, List<?> values) {
        String paramName = toParamName(column) + "_in_" + params.getValues().size();
        wheres.add(column + " IN (:" + paramName + ")");
        params.addValue(paramName, values);
        return this;
    }

    public TarkQBuilder whereNull(String column) {
        wheres.add(column + " IS NULL");
        return this;
    }

    public TarkQBuilder whereNotNull(String column) {
        wheres.add(column + " IS NOT NULL");
        return this;
    }

    public TarkQBuilder innerJoin(String table, String first, String second) {
        joins.add("INNER JOIN " + table + " ON " + first + " = " + second);
        return this;
    }

    public TarkQBuilder leftJoin(String table, String first, String second) {
        joins.add("LEFT JOIN " + table + " ON " + first + " = " + second);
        return this;
    }

    public TarkQBuilder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public TarkQBuilder offset(int offset) {
        this.offset = offset;
        return this;
    }

    public TarkQBuilder orderBy(String column, String direction) {
        String dir = direction.toUpperCase();
        if (!dir.equals("ASC") && !dir.equals("DESC")) {
            throw new IllegalArgumentException("Direction must be ASC or DESC, got: " + direction);
        }
        this.orderBy = "ORDER BY " + column + " " + dir;
        return this;
    }

    public TarkQBuilder groupBy(String... columns) {
        groupByColumns.addAll(Arrays.asList(columns));
        return this;
    }

    public TarkQBuilder having(String condition) {
        this.having = condition;
        return this;
    }

    /**
     * Append a raw SQL expression to the SELECT clause.
     *
     * <p><b>SQL INJECTION WARNING:</b> {@code expression} is inserted directly into the query
     * without escaping. Never pass user-supplied input. Use hardcoded string literals only.
     *
     * <pre>
     * // SAFE
     * .selectRaw("SUM(total) as grand_total")
     *
     * // DANGEROUS
     * .selectRaw(request.getParam("col"))
     * </pre>
     */
    public TarkQBuilder selectRaw(String expression) {
        selectColumns.add(expression);
        return this;
    }

    /**
     * Append a raw SQL condition to the WHERE clause (ANDed with other conditions).
     *
     * <p><b>SQL INJECTION WARNING:</b> {@code condition} is inserted directly into the query
     * without escaping. Never pass user-supplied input directly.
     * For dynamic values, use the {@link #whereRaw(String, Map)} overload with bindings.
     *
     * <pre>
     * // SAFE — hardcoded literal
     * .whereRaw("deleted_at IS NULL")
     *
     * // DANGEROUS — user value interpolated into the string
     * .whereRaw("id = " + request.getParam("id"))
     * </pre>
     */
    public TarkQBuilder whereRaw(String condition) {
        wheres.add(condition);
        return this;
    }

    /**
     * Append a raw SQL condition to the WHERE clause with named parameter bindings.
     *
     * <p><b>SQL INJECTION WARNING:</b> The SQL template in {@code condition} is inserted directly
     * into the query without escaping — only {@code bindings} values are injection-safe.
     * Always place dynamic values in {@code bindings}, never interpolate them into {@code condition}.
     *
     * <pre>
     * // SAFE — dynamic values passed via bindings
     * .whereRaw("id = :userId AND status = :status",
     *           Map.of("userId", userId, "status", status))
     *
     * // DANGEROUS — direct interpolation into condition
     * .whereRaw("id = " + userId, Map.of())
     * </pre>
     */
    public TarkQBuilder whereRaw(String condition, Map<String, Object> bindings) {
        wheres.add(condition);
        params.addValues(bindings);
        return this;
    }

    /**
     * Append a raw SQL JOIN expression.
     *
     * <p><b>SQL INJECTION WARNING:</b> {@code joinExpression} is inserted directly into the query
     * without escaping. Never pass user-supplied input. Use hardcoded string literals only.
     *
     * <pre>
     * // SAFE
     * .joinRaw("LEFT JOIN orders ON users.id = orders.user_id AND orders.total > 100")
     *
     * // DANGEROUS
     * .joinRaw("LEFT JOIN " + request.getParam("table") + " ON ...")
     * </pre>
     */
    public TarkQBuilder joinRaw(String joinExpression) {
        joins.add(joinExpression);
        return this;
    }

    /**
     * Execute a raw SQL string against the database and return the results.
     *
     * <p><b>SQL INJECTION WARNING:</b> {@code sql} is executed directly without escaping.
     * Never build the {@code sql} string by interpolating user input.
     * For dynamic values, use the {@link #raw(String, Map)} overload with bindings.
     *
     * <pre>
     * // SAFE
     * .raw("SELECT * FROM users WHERE status = 'ACTIVE'")
     *
     * // DANGEROUS
     * .raw("SELECT * FROM users WHERE id = " + request.getParam("id"))
     * </pre>
     */
    public List<Map<String, Object>> raw(String sql) {
        return jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
    }

    /**
     * Execute a raw SQL string with named parameter bindings and return the results.
     *
     * <p><b>SQL INJECTION WARNING:</b> The SQL template is inserted into the query directly —
     * only {@code bindings} values are injection-safe. Always place dynamic values
     * in {@code bindings} using {@code :paramName} placeholders, never interpolate them
     * into the {@code sql} string.
     *
     * <pre>
     * // SAFE — dynamic values passed via bindings
     * .raw("SELECT * FROM users WHERE id = :id", Map.of("id", userId))
     *
     * // DANGEROUS — direct interpolation into sql
     * .raw("SELECT * FROM users WHERE id = " + userId, Map.of())
     * </pre>
     */
    public List<Map<String, Object>> raw(String sql, Map<String, Object> bindings) {
        return jdbcTemplate.queryForList(sql, new MapSqlParameterSource(bindings));
    }

    /**
     * Execute a raw SQL string and map results to {@code clazz} using
     * {@link BeanPropertyRowMapper}. snake_case column names are automatically mapped to
     * camelCase fields (e.g. {@code user_id} → {@code userId}).
     * The model must have a no-arg constructor and a setter for each field.
     *
     * <p><b>SQL INJECTION WARNING:</b> {@code sql} is inserted directly into the query
     * without escaping. Never build the {@code sql} string by interpolating user input.
     * Use the {@link #raw(String, Map, Class)} overload for dynamic values.
     *
     * <pre>
     * List&lt;UserModel&gt; users = tark.raw("SELECT * FROM users", UserModel.class);
     * </pre>
     */
    public <T> List<T> raw(String sql, Class<T> clazz) {
        return jdbcTemplate.query(sql, new MapSqlParameterSource(), new BeanPropertyRowMapper<>(clazz));
    }

    /**
     * Execute a raw SQL string with named parameter bindings and map results to
     * {@code clazz} using {@link BeanPropertyRowMapper}.
     *
     * <p><b>SQL INJECTION WARNING:</b> The SQL template is inserted into the query directly —
     * only {@code bindings} values are injection-safe. Always place dynamic values
     * in {@code bindings} using {@code :paramName} placeholders.
     *
     * <pre>
     * // SAFE — dynamic values passed via bindings
     * List&lt;UserModel&gt; users = tark.raw(
     *     "SELECT * FROM users WHERE id = :id",
     *     Map.of("id", userId),
     *     UserModel.class
     * );
     * </pre>
     */
    public <T> List<T> raw(String sql, Map<String, Object> bindings, Class<T> clazz) {
        return jdbcTemplate.query(sql, new MapSqlParameterSource(bindings), new BeanPropertyRowMapper<>(clazz));
    }

    /**
     * Execute a raw SQL string using fields from {@code paramBean} as named parameter
     * bindings, returned as {@code List<Map>}.
     *
     * <p>Bean fields are mapped to SQL placeholders via {@link BeanPropertySqlParameterSource}:
     * getter {@code getMinId()} → {@code :minId}, {@code getMaxId()} → {@code :maxId}, etc.
     *
     * <p><b>SQL INJECTION WARNING:</b> The SQL template is inserted into the query directly —
     * only bean getter values are injection-safe. Never interpolate user input into the {@code sql} string.
     *
     * <pre>
     * // SAFE
     * UserParamsDto params = new UserParamsDto(1, 2);
     * tark.raw("SELECT * FROM users WHERE id >= :minId AND id <= :maxId", params)
     * </pre>
     */
    public List<Map<String, Object>> raw(String sql, Object paramBean) {
        return jdbcTemplate.queryForList(sql, new BeanPropertySqlParameterSource(paramBean));
    }

    /**
     * Execute a raw SQL string using fields from {@code paramBean} as named parameter
     * bindings, with results mapped to {@code clazz}.
     *
     * <p><b>SQL INJECTION WARNING:</b> The SQL template is inserted into the query directly —
     * only bean getter values are injection-safe. Never interpolate user input into the {@code sql} string.
     *
     * <pre>
     * // SAFE
     * UserParamsDto params = new UserParamsDto(1, 2);
     * List&lt;UserModel&gt; users = tark.raw(
     *     "SELECT * FROM users WHERE id >= :minId AND id <= :maxId",
     *     params,
     *     UserModel.class
     * );
     * </pre>
     */
    public <T> List<T> raw(String sql, Object paramBean, Class<T> clazz) {
        return jdbcTemplate.query(sql, new BeanPropertySqlParameterSource(paramBean), new BeanPropertyRowMapper<>(clazz));
    }

    /**
     * Set the FROM source using a raw SQL expression (subquery, alias, etc.).
     *
     * <p><b>SQL INJECTION WARNING:</b> {@code rawFrom} is inserted directly into the FROM clause
     * without escaping. Never pass user-supplied input.
     *
     * <pre>
     * // SAFE — hardcoded subquery literal
     * .fromRaw("(SELECT id, name FROM users WHERE active = 1) AS sub")
     *
     * // DANGEROUS
     * .fromRaw(request.getParam("table"))
     * </pre>
     */
    public TarkQBuilder fromRaw(String rawFrom) {
        this.table = rawFrom;
        return this;
    }

    public TarkQBuilder showQuery() {
        System.out.println("SQL : " + buildSql());
        return this;
    }

    public TarkQBuilder toSQL() {
        String sql = buildSql();
        Map<String, Object> values = params.getValues();

        List<Map.Entry<String, Object>> entries = new ArrayList<>(values.entrySet());
        entries.sort((a, b) -> b.getKey().length() - a.getKey().length());

        for (Map.Entry<String, Object> entry : entries) {
            Object val = entry.getValue();
            String valStr;
            if (val == null) {
                valStr = "NULL";
            } else if (val instanceof String) {
                valStr = "'" + val + "'";
            } else {
                valStr = String.valueOf(val);
            }
            sql = sql.replaceAll(":" + Pattern.quote(entry.getKey()) + "\\b", valStr);
        }

        System.out.println("SQL : " + sql);
        return this;
    }

    public String buildSql() {
        if (rawQuery != null) return rawQuery;
        return dialect == TarkQBuilderDialect.SQLSERVER ? buildSqlServerSql() : buildStandardSql();
    }

    private String buildStandardSql() {
        StringBuilder sb = new StringBuilder("SELECT ");

        if (selectColumns.isEmpty()) {
            sb.append("*");
        } else {
            sb.append(String.join(", ", selectColumns));
        }

        sb.append(" FROM ").append(table);
        appendJoins(sb);
        appendWhere(sb);
        appendGroupBy(sb);
        appendHaving(sb);
        appendOrderBy(sb);

        if (limit != null) sb.append(" LIMIT ").append(limit);
        if (offset != null) sb.append(" OFFSET ").append(offset);

        return sb.toString();
    }

    private String buildSqlServerSql() {
        StringBuilder sb = new StringBuilder("SELECT ");

        if (limit != null && offset == null) {
            sb.append("TOP(").append(limit).append(") ");
        }

        if (selectColumns.isEmpty()) {
            sb.append("*");
        } else {
            sb.append(String.join(", ", selectColumns));
        }

        sb.append(" FROM ").append(table);
        appendJoins(sb);
        appendWhere(sb);
        appendGroupBy(sb);
        appendHaving(sb);
        appendOrderBy(sb);

        if (offset != null) {
            if (orderBy == null) {
                sb.append(" ORDER BY (SELECT 0)");
            }
            sb.append(" OFFSET ").append(offset).append(" ROWS");
            if (limit != null) {
                sb.append(" FETCH NEXT ").append(limit).append(" ROWS ONLY");
            }
        }

        return sb.toString();
    }

    private void appendJoins(StringBuilder sb) {
        for (String join : joins) sb.append(" ").append(join);
    }

    private void appendWhere(StringBuilder sb) {
        if (!wheres.isEmpty()) sb.append(" WHERE ").append(String.join(" AND ", wheres));
    }

    private void appendGroupBy(StringBuilder sb) {
        if (!groupByColumns.isEmpty()) sb.append(" GROUP BY ").append(String.join(", ", groupByColumns));
    }

    private void appendHaving(StringBuilder sb) {
        if (having != null) sb.append(" HAVING ").append(having);
    }

    private void appendOrderBy(StringBuilder sb) {
        if (orderBy != null) sb.append(" ").append(orderBy);
    }

    public List<Map<String, Object>> fetchAll() {
        return jdbcTemplate.queryForList(buildSql(), params);
    }

    public <T> List<T> fetchAll(Class<T> clazz) {
        return jdbcTemplate.queryForList(buildSql(), params, clazz);
    }

    public Optional<Map<String, Object>> first() {
        Integer savedLimit = this.limit;
        this.limit = 1;
        try {
            List<Map<String, Object>> results = fetchAll();
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } finally {
            this.limit = savedLimit;
        }
    }

    public MapSqlParameterSource getParams() {
        return params;
    }

    private static String toParamName(String column) {
        return column.replaceAll("[^a-zA-Z0-9]", "_");
    }
}
