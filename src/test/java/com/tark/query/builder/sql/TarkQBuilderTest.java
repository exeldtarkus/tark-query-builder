package com.tark.query.builder.sql;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class TarkQBuilderTest {

    private NamedParameterJdbcTemplate jdbcTemplate;

    @BeforeEach
    void setup() {
        DataSource dataSource = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .addScript("classpath:schema.sql")
                .build();
        jdbcTemplate = new NamedParameterJdbcTemplate(dataSource);

        jdbcTemplate.getJdbcTemplate().execute("DELETE FROM orders");
        jdbcTemplate.getJdbcTemplate().execute("DELETE FROM users");
        jdbcTemplate.getJdbcTemplate().execute("INSERT INTO users (id, name, email) VALUES (1, 'Alice', 'alice@example.com')");
        jdbcTemplate.getJdbcTemplate().execute("INSERT INTO users (id, name, email) VALUES (2, 'Bob', 'bob@example.com')");
        jdbcTemplate.getJdbcTemplate().execute("INSERT INTO users (id, name, email) VALUES (3, 'Charlie', null)");
        jdbcTemplate.getJdbcTemplate().execute("INSERT INTO orders (id, user_id, total) VALUES (1, 1, 150.00)");
        jdbcTemplate.getJdbcTemplate().execute("INSERT INTO orders (id, user_id, total) VALUES (2, 2, 50.00)");
    }

    @Test
    void testBasicSelect() {
        List<Map<String, Object>> users = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .select("id", "name")
                .where("id", 1)
                .fetchAll();

        assertEquals(1, users.size());
        assertEquals("Alice", users.get(0).get("name"));
    }

    @Test
    void testFirst() {
        Optional<Map<String, Object>> user = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .where("name", "Bob")
                .first();

        assertTrue(user.isPresent());
        assertEquals("bob@example.com", user.get().get("email"));
    }

    @Test
    void testFirstDoesNotMutateLimit() {
        TarkQBuilder tark = TarkQBuilder.query(jdbcTemplate).from("users").limit(10);
        tark.first();
        assertTrue(tark.buildSql().contains("LIMIT 10"));
    }

    @Test
    void testWhereIn() {
        List<Map<String, Object>> users = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .whereIn("id", List.of(1, 2))
                .orderBy("id", "ASC")
                .fetchAll();

        assertEquals(2, users.size());
    }

    @Test
    void testWhereNull() {
        List<Map<String, Object>> users = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .whereNull("email")
                .fetchAll();

        assertEquals(1, users.size());
        assertEquals("Charlie", users.get(0).get("name"));
    }

    @Test
    void testWhereNotNull() {
        List<Map<String, Object>> users = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .whereNotNull("email")
                .fetchAll();

        assertEquals(2, users.size());
    }

    @Test
    void testLeftJoin() {
        List<Map<String, Object>> results = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .select("users.name", "orders.total")
                .leftJoin("orders", "users.id", "orders.user_id")
                .orderBy("users.id", "ASC")
                .fetchAll();

        assertEquals(3, results.size());
        assertNull(results.get(2).get("total"));
    }

    @Test
    void testGroupByWithHaving() {
        String sql = TarkQBuilder.query(jdbcTemplate)
                .from("orders")
                .select("user_id", "SUM(total) as total_sum")
                .groupBy("user_id")
                .having("SUM(total) > 100")
                .buildSql();

        assertEquals("SELECT user_id, SUM(total) as total_sum FROM orders GROUP BY user_id HAVING SUM(total) > 100", sql);
    }

    @Test
    void testInvalidOperatorThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                TarkQBuilder.query(jdbcTemplate).from("users").where("id", "DROP TABLE", 1));
    }

    // SQL Server dialect tests

    @Test
    void testSqlServerLimitOnly() {
        String sql = TarkQBuilder.query(jdbcTemplate, TarkQBuilderDialect.SQLSERVER)
                .from("users").limit(5).buildSql();
        assertEquals("SELECT TOP(5) * FROM users", sql);
    }

    @Test
    void testSqlServerLimitAndOffset() {
        String sql = TarkQBuilder.query(jdbcTemplate, TarkQBuilderDialect.SQLSERVER)
                .from("users").limit(5).offset(10).buildSql();
        assertEquals("SELECT * FROM users ORDER BY (SELECT 0) OFFSET 10 ROWS FETCH NEXT 5 ROWS ONLY", sql);
    }

    @Test
    void testSqlServerWithOrderByAndPagination() {
        String sql = TarkQBuilder.query(jdbcTemplate, TarkQBuilderDialect.SQLSERVER)
                .from("users").orderBy("name", "ASC").limit(10).offset(20).buildSql();
        assertEquals("SELECT * FROM users ORDER BY name ASC OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY", sql);
    }

    // Raw query tests

    @Test
    void testRawExecuteNoBindings() {
        List<Map<String, Object>> result = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT id, name FROM users ORDER BY id ASC");
        assertEquals(3, result.size());
        assertEquals("Alice", result.get(0).get("name"));
    }

    @Test
    void testRawExecuteWithBindings() {
        List<Map<String, Object>> result = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT * FROM users WHERE id = :userId", Map.of("userId", 2));
        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).get("name"));
    }

    @Test
    void testSelectRaw() {
        String sql = TarkQBuilder.query(jdbcTemplate)
                .from("orders")
                .selectRaw("user_id")
                .selectRaw("SUM(total) as grand_total")
                .groupBy("user_id")
                .buildSql();
        assertEquals("SELECT user_id, SUM(total) as grand_total FROM orders GROUP BY user_id", sql);
    }

    @Test
    void testSelectRawExecutesCorrectly() {
        List<Map<String, Object>> result = TarkQBuilder.query(jdbcTemplate)
                .from("orders")
                .selectRaw("user_id")
                .selectRaw("SUM(total) as grand_total")
                .groupBy("user_id")
                .orderBy("user_id", "ASC")
                .fetchAll();
        assertEquals(2, result.size());
    }

    @Test
    void testSelectRawMixedWithSelect() {
        String sql = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .select("id")
                .selectRaw("UPPER(name) as upper_name")
                .buildSql();
        assertEquals("SELECT id, UPPER(name) as upper_name FROM users", sql);
    }

    @Test
    void testWhereRaw() {
        String sql = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .whereRaw("id > 1")
                .buildSql();
        assertEquals("SELECT * FROM users WHERE id > 1", sql);
    }

    @Test
    void testWhereRawWithBindings() {
        String sql = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .whereRaw("id = :rawId", Map.of("rawId", 1))
                .buildSql();
        assertEquals("SELECT * FROM users WHERE id = :rawId", sql);
    }

    @Test
    void testWhereRawExecutesCorrectly() {
        List<Map<String, Object>> result = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .whereRaw("id IN (1, 3)")
                .orderBy("id", "ASC")
                .fetchAll();
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).get("name"));
        assertEquals("Charlie", result.get(1).get("name"));
    }

    @Test
    void testWhereRawWithBindingsExecutesCorrectly() {
        List<Map<String, Object>> result = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .whereRaw("id = :rawId", Map.of("rawId", 2))
                .fetchAll();
        assertEquals(1, result.size());
        assertEquals("Bob", result.get(0).get("name"));
    }

    @Test
    void testWhereRawCombinedWithWhere() {
        String sql = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .where("id", ">", 0)
                .whereRaw("email IS NOT NULL")
                .buildSql();
        assertEquals("SELECT * FROM users WHERE id > :id_0 AND email IS NOT NULL", sql);
    }

    @Test
    void testJoinRaw() {
        String sql = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .select("users.name", "orders.total")
                .joinRaw("LEFT JOIN orders ON users.id = orders.user_id AND orders.total > 100")
                .buildSql();
        assertEquals(
            "SELECT users.name, orders.total FROM users LEFT JOIN orders ON users.id = orders.user_id AND orders.total > 100",
            sql
        );
    }

    @Test
    void testJoinRawExecutesCorrectly() {
        List<Map<String, Object>> result = TarkQBuilder.query(jdbcTemplate)
                .from("users")
                .select("users.name", "orders.total")
                .joinRaw("LEFT JOIN orders ON users.id = orders.user_id")
                .orderBy("users.id", "ASC")
                .fetchAll();
        assertEquals(3, result.size());
        assertNull(result.get(2).get("total"));
    }

    @Test
    void testJoinRawCombinedWithWhereRaw() {
        String sql = TarkQBuilder.query(jdbcTemplate)
                .from("users u")
                .select("u.name")
                .joinRaw("INNER JOIN orders o ON u.id = o.user_id")
                .whereRaw("o.total > 100")
                .buildSql();
        assertEquals("SELECT u.name FROM users u INNER JOIN orders o ON u.id = o.user_id WHERE o.total > 100", sql);
    }

    @Test
    void testFromRaw() {
        String sql = TarkQBuilder.query(jdbcTemplate)
                .fromRaw("(SELECT id, name FROM users WHERE id > 1) AS sub")
                .select("name")
                .buildSql();
        assertEquals("SELECT name FROM (SELECT id, name FROM users WHERE id > 1) AS sub", sql);
    }

    @Test
    void testFromRawExecutesCorrectly() {
        List<Map<String, Object>> result = TarkQBuilder.query(jdbcTemplate)
                .fromRaw("(SELECT id, name FROM users WHERE id > 1) AS sub")
                .select("name")
                .orderBy("name", "ASC")
                .fetchAll();
        assertEquals(2, result.size());
        assertEquals("Bob", result.get(0).get("name"));
        assertEquals("Charlie", result.get(1).get("name"));
    }

    // raw() dengan model (Class<T>)

    @Test
    void testRawWithModelNoBindings() {
        List<UserModel> users = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT id, name, email FROM users ORDER BY id ASC", UserModel.class);
        assertEquals(3, users.size());
        assertEquals("Alice", users.get(0).getName());
        assertEquals("alice@example.com", users.get(0).getEmail());
        assertEquals(1, users.get(0).getId());
    }

    @Test
    void testRawWithModelAndBindings() {
        List<UserModel> users = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT * FROM users WHERE id = :userId", Map.of("userId", 2), UserModel.class);
        assertEquals(1, users.size());
        assertEquals("Bob", users.get(0).getName());
        assertEquals("bob@example.com", users.get(0).getEmail());
        assertEquals(2, users.get(0).getId());
    }

    @Test
    void testRawWithModelNullField() {
        List<UserModel> users = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT * FROM users WHERE id = :userId", Map.of("userId", 3), UserModel.class);
        assertEquals(1, users.size());
        assertEquals("Charlie", users.get(0).getName());
        assertNull(users.get(0).getEmail());
    }

    @Test
    void testRawWithModelMultipleBindings() {
        List<UserModel> users = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT * FROM users WHERE id >= :minId AND id <= :maxId ORDER BY id ASC",
                     Map.of("minId", 1, "maxId", 2),
                     UserModel.class);
        assertEquals(2, users.size());
        assertEquals("Alice", users.get(0).getName());
        assertEquals("Bob", users.get(1).getName());
    }

    @Test
    void testRawWithModelEmptyResult() {
        List<UserModel> users = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT * FROM users WHERE id = :userId", Map.of("userId", 999), UserModel.class);
        assertTrue(users.isEmpty());
    }

    // raw() dengan DTO sebagai input parameter

    @Test
    void testRawWithBeanParamsReturnsMap() {
        UserParamsDto params = new UserParamsDto(1, 2);
        List<Map<String, Object>> result = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT * FROM users WHERE id >= :minId AND id <= :maxId ORDER BY id ASC", params);
        assertEquals(2, result.size());
        assertEquals("Alice", result.get(0).get("name"));
        assertEquals("Bob", result.get(1).get("name"));
    }

    @Test
    void testRawWithBeanParamsAndModelOutput() {
        UserParamsDto params = new UserParamsDto(1, 2);
        List<UserModel> users = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT * FROM users WHERE id >= :minId AND id <= :maxId ORDER BY id ASC",
                     params, UserModel.class);
        assertEquals(2, users.size());
        assertEquals("Alice", users.get(0).getName());
        assertEquals(2, users.get(1).getId());
    }

    @Test
    void testRawWithBeanParamsSingleField() {
        UserParamsDto params = new UserParamsDto("Bob");
        List<UserModel> users = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT * FROM users WHERE name = :name", params, UserModel.class);
        assertEquals(1, users.size());
        assertEquals("bob@example.com", users.get(0).getEmail());
    }

    @Test
    void testRawWithBeanParamsEmptyResult() {
        UserParamsDto params = new UserParamsDto(99, 100);
        List<UserModel> users = TarkQBuilder.query(jdbcTemplate)
                .raw("SELECT * FROM users WHERE id >= :minId AND id <= :maxId", params, UserModel.class);
        assertTrue(users.isEmpty());
    }
}
