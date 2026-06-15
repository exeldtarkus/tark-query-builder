# tark-query-builder

A Java library for building SQL and MongoDB queries using fluent method chaining, built on top of Spring Boot. Supports MySQL, PostgreSQL, and SQL Server dialects, as well as MongoDB via `MongoTemplate`.

---

## Table of Contents

- [Requirements](#requirements)
- [Installation](#installation)
- [Configuration](#configuration)
- [SQL Query Builder](#sql-query-builder)
  - [Creating an Instance](#creating-an-instance)
  - [SELECT](#select)
  - [WHERE](#where)
  - [JOIN](#join)
  - [GROUP BY & HAVING](#group-by--having)
  - [ORDER BY, LIMIT, OFFSET](#order-by-limit-offset)
  - [Raw Query](#raw-query)
  - [Executing Queries](#executing-queries)
  - [Debug Query](#debug-query)
- [MongoDB Query Builder](#mongodb-query-builder)
  - [Creating an Instance](#creating-an-instance-1)
  - [SELECT (Projection)](#select-projection)
  - [WHERE](#where-1)
  - [LOOKUP (Join)](#lookup-join)
  - [ORDER BY, LIMIT, SKIP](#order-by-limit-skip)
  - [Raw Filter & Aggregation](#raw-filter--aggregation)
  - [Executing Queries](#executing-queries-1)
  - [Debug Query](#debug-query-1)
- [SQL Server Dialect](#sql-server-dialect)
- [SQL Injection Warning](#sql-injection-warning)
- [Running Tests](#running-tests)

---

## Requirements

- Java 21+
- Maven 3.6+
- Spring Boot 3.3.x
- `NamedParameterJdbcTemplate` (for SQL)
- `MongoTemplate` (for MongoDB, optional)

---

## Installation

### Build from Source

```bash
git clone <repo-url>
cd tark-query-builder
mvn clean install
```

### Add to Your Maven Project

After installing to your local repository, add the following dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>com.tark</groupId>
    <artifactId>tark-query-builder</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Make sure your project already has the Spring Boot JDBC dependency (for SQL):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

For MongoDB (optional):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

---

## Configuration

This library supports **Spring Boot Autoconfiguration**. Once the dependency is added, the `TarkQBuilderFactory` bean is automatically registered as long as `NamedParameterJdbcTemplate` is available in the application context.

Set the SQL dialect in `application.properties`:

```properties
# Options: mysql (default), postgresql, sqlserver
tark.query.dialect=mysql
```

Then inject the factory bean into your Spring component:

```java
@Autowired
TarkQBuilderFactory tark;

// Use with jdbcTemplate
TarkQBuilder query = tark.query(jdbcTemplate);
```

### Manual Configuration (Without Autoconfiguration)

To create an instance manually without Spring:

```java
// MySQL (default)
TarkQBuilder tark = TarkQBuilder.query(jdbcTemplate);

// With a specific dialect
TarkQBuilder tark = TarkQBuilder.query(jdbcTemplate, TarkQBuilderDialect.POSTGRESQL);
TarkQBuilder tark = TarkQBuilder.query(jdbcTemplate, TarkQBuilderDialect.SQLSERVER);
```

---

## SQL Query Builder

### Creating an Instance

```java
// Via static factory
TarkQBuilder tark = TarkQBuilder.query(jdbcTemplate);

// Via autoconfigured factory bean
@Autowired TarkQBuilderFactory tarkFactory;
TarkQBuilder tark = tarkFactory.query(jdbcTemplate);
```

---

### SELECT

```java
// SELECT * FROM users
TarkQBuilder.query(jdbcTemplate).from("users").fetchAll();

// SELECT id, name FROM users
TarkQBuilder.query(jdbcTemplate)
    .from("users")
    .select("id", "name")
    .fetchAll();

// SELECT id, UPPER(name) as upper_name FROM users
TarkQBuilder.query(jdbcTemplate)
    .from("users")
    .select("id")
    .selectRaw("UPPER(name) as upper_name")
    .fetchAll();
```

---

### WHERE

Supported operators: `=`, `!=`, `<>`, `>`, `<`, `>=`, `<=`, `LIKE`, `NOT LIKE`

```java
// WHERE id = 1
.where("id", 1)

// WHERE status != 'DELETED'
.where("status", "!=", "DELETED")

// WHERE name LIKE '%alice%'
.where("name", "LIKE", "%alice%")

// WHERE id IN (1, 2, 3)
.whereIn("id", List.of(1, 2, 3))

// WHERE email IS NULL
.whereNull("email")

// WHERE email IS NOT NULL
.whereNotNull("email")

// Multiple WHERE clauses (ANDed together)
TarkQBuilder.query(jdbcTemplate)
    .from("users")
    .where("status", "ACTIVE")
    .where("age", ">=", 18)
    .whereNotNull("email")
    .fetchAll();
```

---

### JOIN

```java
// INNER JOIN
TarkQBuilder.query(jdbcTemplate)
    .from("users")
    .select("users.name", "orders.total")
    .innerJoin("orders", "users.id", "orders.user_id")
    .fetchAll();

// LEFT JOIN
TarkQBuilder.query(jdbcTemplate)
    .from("users")
    .select("users.name", "orders.total")
    .leftJoin("orders", "users.id", "orders.user_id")
    .fetchAll();

// JOIN with extra conditions (raw)
.joinRaw("LEFT JOIN orders ON users.id = orders.user_id AND orders.total > 100")
```

---

### GROUP BY & HAVING

```java
TarkQBuilder.query(jdbcTemplate)
    .from("orders")
    .select("user_id", "SUM(total) as total_sum")
    .groupBy("user_id")
    .having("SUM(total) > 100")
    .fetchAll();
```

---

### ORDER BY, LIMIT, OFFSET

```java
// ORDER BY name ASC LIMIT 10 OFFSET 20
TarkQBuilder.query(jdbcTemplate)
    .from("users")
    .orderBy("name", "ASC")
    .limit(10)
    .offset(20)
    .fetchAll();
```

---

### Raw Query

For cases that cannot be expressed with the builder methods:

```java
// Raw SELECT — returns List<Map>
tark.raw("SELECT * FROM users WHERE status = 'ACTIVE'");

// Raw SELECT with named parameter bindings
tark.raw("SELECT * FROM users WHERE id = :id", Map.of("id", userId));

// Raw SELECT mapped to a model class
List<UserModel> users = tark.raw("SELECT * FROM users", UserModel.class);

// Raw SELECT + bindings + model
List<UserModel> users = tark.raw(
    "SELECT * FROM users WHERE id = :id",
    Map.of("id", userId),
    UserModel.class
);

// Raw SELECT using a DTO as parameter source
UserParamsDto params = new UserParamsDto(1, 10); // getters: getMinId(), getMaxId()
List<UserModel> users = tark.raw(
    "SELECT * FROM users WHERE id >= :minId AND id <= :maxId",
    params,
    UserModel.class
);

// Raw WHERE clause (literal condition)
.whereRaw("deleted_at IS NULL")

// Raw WHERE with bindings
.whereRaw("id = :userId AND status = :status", Map.of("userId", 1, "status", "ACTIVE"))

// Raw SELECT expression (aggregate)
.selectRaw("SUM(total) as grand_total")

// FROM subquery
.fromRaw("(SELECT id, name FROM users WHERE active = 1) AS sub")
```

> **Note:** When using `raw()` with a model class, snake_case column names are automatically mapped to camelCase fields (e.g. `user_id` → `userId`). The model must have a no-arg constructor and setters for each field.

---

### Executing Queries

| Method | Return Type | Description |
|---|---|---|
| `fetchAll()` | `List<Map<String, Object>>` | Fetch all rows as Maps |
| `fetchAll(Class<T>)` | `List<T>` | Fetch all rows mapped to a model |
| `first()` | `Optional<Map<String, Object>>` | Fetch the first row |
| `raw(sql)` | `List<Map<String, Object>>` | Execute a raw SQL string |
| `raw(sql, bindings)` | `List<Map<String, Object>>` | Execute raw SQL with parameters |
| `raw(sql, Class<T>)` | `List<T>` | Raw SQL mapped to a model |
| `raw(sql, bindings, Class<T>)` | `List<T>` | Raw SQL + parameters + model |
| `raw(sql, paramBean)` | `List<Map<String, Object>>` | Raw SQL with a DTO as parameter source |
| `raw(sql, paramBean, Class<T>)` | `List<T>` | Raw SQL + DTO + model |
| `buildSql()` | `String` | Returns the SQL string without executing |

---

### Debug Query

```java
// Print SQL with named parameter placeholders
TarkQBuilder.query(jdbcTemplate)
    .from("users")
    .where("id", 1)
    .showQuery()   // output: SQL : SELECT * FROM users WHERE id = :id_0
    .fetchAll();

// Print SQL with parameter values substituted
TarkQBuilder.query(jdbcTemplate)
    .from("users")
    .where("id", 1)
    .toSQL()       // output: SQL : SELECT * FROM users WHERE id = 1
    .fetchAll();
```

---

## MongoDB Query Builder

### Creating an Instance

```java
TarkQBuilderMongo tark = TarkQBuilderMongo.query(mongoTemplate);
```

---

### SELECT (Projection)

```java
// Include only specific fields
TarkQBuilderMongo.query(mongoTemplate)
    .from("users")
    .select("name", "email")
    .fetchAll();
```

---

### WHERE

Supported operators: `=`, `!=`, `>`, `>=`, `<`, `<=`, `LIKE`, `NOT LIKE`

```java
// where field = value
.where("status", "ACTIVE")

// where field operator value
.where("age", ">", 18)
.where("name", "LIKE", "%alice%")

// whereIn
.whereIn("status", List.of("ACTIVE", "PENDING"))

// whereNotIn
.whereNotIn("status", List.of("DELETED"))

// whereNull / whereNotNull
.whereNull("deletedAt")
.whereNotNull("email")

// Multiple WHERE clauses (ANDed together)
TarkQBuilderMongo.query(mongoTemplate)
    .from("users")
    .where("status", "ACTIVE")
    .where("age", ">=", 18)
    .whereNotNull("email")
    .fetchAll();
```

---

### LOOKUP (Join)

Equivalent to MongoDB's `$lookup` aggregation stage:

```java
TarkQBuilderMongo.query(mongoTemplate)
    .from("users")
    .lookup("orders", "userId", "_id", "userOrders")
    .fetchAll();
```

Parameters: `lookup(fromCollection, localField, foreignField, alias)`

---

### ORDER BY, LIMIT, SKIP

```java
TarkQBuilderMongo.query(mongoTemplate)
    .from("users")
    .orderBy("name", "ASC")
    .limit(10)
    .skip(20)     // or .offset(20)
    .fetchAll();
```

---

### Raw Filter & Aggregation

```java
// Filter using a JSON string
tark.rawFilter("{\"status\": \"ACTIVE\", \"age\": {\"$gt\": 18}}");

// Filter using a JSON string mapped to a model
tark.rawFilter("{\"status\": \"ACTIVE\"}", UserModel.class);

// Filter using a BSON Document (safer)
Document filter = new Document("status", "ACTIVE")
    .append("age", new Document("$gt", 18));
tark.rawFilter(filter);

// Raw aggregation pipeline
tark.from("orders").aggregate(List.of(
    new Document("$match",  new Document("status", "ACTIVE")),
    new Document("$group",  new Document("_id", "$department")
                                .append("total", new Document("$sum", 1))),
    new Document("$sort",   new Document("total", -1))
));

// Aggregation mapped to a model
tark.from("orders").aggregate(pipeline, SummaryModel.class);
```

---

### Executing Queries

| Method | Return Type | Description |
|---|---|---|
| `fetchAll()` | `List<Document>` | Fetch all documents |
| `fetchAll(Class<T>)` | `List<T>` | Fetch all documents mapped to a model |
| `first()` | `Optional<Document>` | Fetch the first document |
| `count()` | `long` | Count matching documents |
| `rawFilter(String json)` | `List<Document>` | Filter using a JSON string |
| `rawFilter(Document)` | `List<Document>` | Filter using a BSON Document |
| `aggregate(List<Document>)` | `List<Document>` | Run an aggregation pipeline |
| `buildQuery()` | `Query` | Returns the Spring `Query` object |

---

### Debug Query

```java
TarkQBuilderMongo.query(mongoTemplate)
    .from("users")
    .where("status", "ACTIVE")
    .limit(5)
    .showQuery()
    .fetchAll();
// output:
// Filter : {"$and": [{"status": "ACTIVE"}]}
// Fields : {}
// Sort   : {}
// Limit  : 5
```

---

## SQL Server Dialect

SQL Server uses a different pagination syntax (`TOP` / `OFFSET FETCH`). Set the dialect in `application.properties`:

```properties
tark.query.dialect=sqlserver
```

Or set it directly:

```java
TarkQBuilder.query(jdbcTemplate, TarkQBuilderDialect.SQLSERVER)
```

SQL Server behavior:

```java
// LIMIT only → TOP(N)
.from("users").limit(5)
// → SELECT TOP(5) * FROM users

// LIMIT + OFFSET → OFFSET FETCH
.from("users").limit(5).offset(10)
// → SELECT * FROM users ORDER BY (SELECT 0) OFFSET 10 ROWS FETCH NEXT 5 ROWS ONLY

// ORDER BY + LIMIT + OFFSET
.from("users").orderBy("name", "ASC").limit(10).offset(20)
// → SELECT * FROM users ORDER BY name ASC OFFSET 20 ROWS FETCH NEXT 10 ROWS ONLY
```

---

## SQL Injection Warning

The methods `raw`, `selectRaw`, `whereRaw`, `joinRaw`, `fromRaw`, and `rawFilter` insert expressions directly into the query **without escaping**. Usage rules:

- Always use **hardcoded string literals**, never values from user input.
- For dynamic values, always use **named parameter bindings** instead of string interpolation.

```java
// SAFE — dynamic value passed via bindings
.whereRaw("id = :userId", Map.of("userId", userId))
tark.raw("SELECT * FROM users WHERE id = :id", Map.of("id", userId))

// DANGEROUS — user input interpolated directly into the string
.whereRaw("id = " + request.getParam("id"))
tark.raw("SELECT * FROM users WHERE id = " + userId)
```

---

## Running Tests

```bash
mvn test
```

SQL tests use an H2 in-memory database. MongoDB tests use a Mockito mock of `MongoTemplate`.

```
src/test/java/
├── sql/
│   ├── TarkQBuilderTest.java        # SQL builder tests (H2)
│   ├── UserModel.java               # Model used in tests
│   └── UserParamsDto.java           # DTO parameter used in tests
├── mongo/
│   └── TarkQBuilderMongoTest.java   # MongoDB builder tests (Mockito)
└── autoconfigure/
    └── TarkQBuilderAutoConfigurationTest.java
```
