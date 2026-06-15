package com.tark.query.builder.mongo;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Query;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TarkQBuilderMongoTest {

    private MongoTemplate mongoTemplate;

    @BeforeEach
    void setup() {
        mongoTemplate = mock(MongoTemplate.class);
    }

    @Test
    void testWhereEqual() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users").where("name", "Alice").buildQuery();
        Document filter = query.getQueryObject();
        List<Document> conditions = filter.getList("$and", Document.class);
        assertEquals("Alice", conditions.get(0).get("name"));
    }

    @Test
    void testWhereGt() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users").where("age", ">", 18).buildQuery();
        List<Document> conditions = query.getQueryObject().getList("$and", Document.class);
        assertEquals(18, conditions.get(0).get("age", Document.class).get("$gt"));
    }

    @Test
    void testMultipleWhereIsAnd() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users")
                .where("status", "ACTIVE").where("age", ">", 18).buildQuery();
        assertEquals(2, query.getQueryObject().getList("$and", Document.class).size());
    }

    @Test
    void testWhereIn() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users").whereIn("id", List.of(1, 2, 3)).buildQuery();
        List<Document> conditions = query.getQueryObject().getList("$and", Document.class);
        assertNotNull(conditions.get(0).get("id", Document.class).get("$in"));
    }

    @Test
    void testWhereNotIn() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users").whereNotIn("status", List.of("DELETED")).buildQuery();
        List<Document> conditions = query.getQueryObject().getList("$and", Document.class);
        assertNotNull(conditions.get(0).get("status", Document.class).get("$nin"));
    }

    @Test
    void testWhereNull() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users").whereNull("deletedAt").buildQuery();
        List<Document> conditions = query.getQueryObject().getList("$and", Document.class);
        assertNull(conditions.get(0).get("deletedAt"));
    }

    @Test
    void testSelect() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users").select("name", "email").buildQuery();
        Document fields = query.getFieldsObject();
        assertEquals(1, fields.get("name"));
        assertEquals(1, fields.get("email"));
    }

    @Test
    void testOrderByAsc() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users").orderBy("name", "ASC").buildQuery();
        assertEquals(1, query.getSortObject().get("name"));
    }

    @Test
    void testOrderByDesc() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users").orderBy("name", "DESC").buildQuery();
        assertEquals(-1, query.getSortObject().get("name"));
    }

    @Test
    void testLimitAndSkip() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users").limit(5).skip(10).buildQuery();
        assertEquals(5, query.getLimit());
        assertEquals(10, query.getSkip());
    }

    @Test
    void testOffsetAliasForSkip() {
        Query query = TarkQBuilderMongo.query(mongoTemplate).from("users").offset(15).buildQuery();
        assertEquals(15, query.getSkip());
    }

    @Test
    void testFetchAllCallsMongoTemplate() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of(new Document("name", "Alice")));

        List<Document> result = TarkQBuilderMongo.query(mongoTemplate).from("users").where("name", "Alice").fetchAll();

        assertEquals(1, result.size());
        verify(mongoTemplate).find(any(Query.class), eq(Document.class), eq("users"));
    }

    @Test
    void testFetchAllPassesCorrectQuery() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users"))).thenReturn(List.of());

        TarkQBuilderMongo.query(mongoTemplate).from("users").where("status", "ACTIVE").limit(5).fetchAll();

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).find(captor.capture(), eq(Document.class), eq("users"));
        assertEquals(5, captor.getValue().getLimit());
    }

    @Test
    void testFirstCallsFindOne() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(new Document("name", "Bob").append("email", "bob@example.com"));

        Optional<Document> result = TarkQBuilderMongo.query(mongoTemplate).from("users").where("name", "Bob").first();

        assertTrue(result.isPresent());
        assertEquals("bob@example.com", result.get().getString("email"));
    }

    @Test
    void testFirstSetsLimitToOne() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("users"))).thenReturn(null);

        TarkQBuilderMongo.query(mongoTemplate).from("users").first();

        ArgumentCaptor<Query> captor = ArgumentCaptor.forClass(Query.class);
        verify(mongoTemplate).findOne(captor.capture(), eq(Document.class), eq("users"));
        assertEquals(1, captor.getValue().getLimit());
    }

    @Test
    void testFirstDoesNotMutateLimit() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("users"))).thenReturn(null);

        TarkQBuilderMongo tark = TarkQBuilderMongo.query(mongoTemplate).from("users").limit(10);
        tark.first();

        assertEquals(10, tark.buildQuery().getLimit());
    }

    @Test
    void testFirstReturnsEmptyWhenNotFound() {
        when(mongoTemplate.findOne(any(Query.class), eq(Document.class), eq("users"))).thenReturn(null);

        Optional<Document> result = TarkQBuilderMongo.query(mongoTemplate).from("users").where("name", "Nobody").first();

        assertFalse(result.isPresent());
    }

    @Test
    void testCount() {
        when(mongoTemplate.count(any(Query.class), eq("users"))).thenReturn(42L);

        long count = TarkQBuilderMongo.query(mongoTemplate).from("users").count();

        assertEquals(42L, count);
    }

    @Test
    void testLookupStageIsStored() {
        TarkQBuilderMongo tark = TarkQBuilderMongo.query(mongoTemplate).from("users")
                .lookup("orders", "name", "userId", "userOrders");

        TarkQBuilderMongo.LookupStage stage = tark.getLookups().get(0);
        assertEquals("orders", stage.from());
        assertEquals("userOrders", stage.as());
    }

    @Test
    void testInvalidOperatorThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                TarkQBuilderMongo.query(mongoTemplate).from("users").where("name", "DROP", "users"));
    }

    @Test
    void testInvalidOrderByDirectionThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                TarkQBuilderMongo.query(mongoTemplate).from("users").orderBy("name", "INVALID"));
    }

    // rawFilter(String json)

    @Test
    void testRawFilterJsonString() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of(new Document("name", "Alice")));

        List<Document> result = TarkQBuilderMongo.query(mongoTemplate)
                .from("users")
                .rawFilter("{\"name\": \"Alice\"}");

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getString("name"));
        verify(mongoTemplate).find(any(BasicQuery.class), eq(Document.class), eq("users"));
    }

    @Test
    void testRawFilterJsonStringWithClass() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of(new Document("name", "Bob")));

        List<Document> result = TarkQBuilderMongo.query(mongoTemplate)
                .from("users")
                .rawFilter("{\"name\": \"Bob\"}", Document.class);

        assertEquals(1, result.size());
        verify(mongoTemplate).find(any(BasicQuery.class), eq(Document.class), eq("users"));
    }

    // rawFilter(Document filterDoc)

    @Test
    void testRawFilterDocument() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of(new Document("name", "Charlie")));

        Document filter = new Document("name", "Charlie");
        List<Document> result = TarkQBuilderMongo.query(mongoTemplate)
                .from("users")
                .rawFilter(filter);

        assertEquals(1, result.size());
        assertEquals("Charlie", result.get(0).getString("name"));
        verify(mongoTemplate).find(any(BasicQuery.class), eq(Document.class), eq("users"));
    }

    @Test
    void testRawFilterDocumentWithClass() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of(new Document("status", "ACTIVE")));

        Document filter = new Document("status", "ACTIVE");
        List<Document> result = TarkQBuilderMongo.query(mongoTemplate)
                .from("users")
                .rawFilter(filter, Document.class);

        assertEquals(1, result.size());
        verify(mongoTemplate).find(any(BasicQuery.class), eq(Document.class), eq("users"));
    }

    @Test
    void testRawFilterDocumentWithGtOperator() {
        when(mongoTemplate.find(any(Query.class), eq(Document.class), eq("users")))
                .thenReturn(List.of(
                        new Document("name", "Alice").append("age", 25),
                        new Document("name", "Bob").append("age", 30)
                ));

        Document filter = new Document("age", new Document("$gt", 18));
        List<Document> result = TarkQBuilderMongo.query(mongoTemplate)
                .from("users")
                .rawFilter(filter);

        assertEquals(2, result.size());
        verify(mongoTemplate).find(any(BasicQuery.class), eq(Document.class), eq("users"));
    }

    // aggregate(List<Document> pipeline)

    @Test
    @SuppressWarnings("unchecked")
    void testRawAggregate() {
        AggregationResults<Document> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults())
                .thenReturn(List.of(new Document("_id", "Alice").append("total", 3)));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("orders"), eq(Document.class)))
                .thenReturn(mockResults);

        List<Document> result = TarkQBuilderMongo.query(mongoTemplate)
                .from("orders")
                .aggregate(List.of(
                        new Document("$match", new Document("status", "ACTIVE")),
                        new Document("$group", new Document("_id", "$name")
                                .append("total", new Document("$sum", 1)))
                ));

        assertEquals(1, result.size());
        assertEquals("Alice", result.get(0).getString("_id"));
        verify(mongoTemplate).aggregate(any(Aggregation.class), eq("orders"), eq(Document.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRawAggregateWithClass() {
        AggregationResults<Document> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults())
                .thenReturn(List.of(new Document("_id", "dept-A").append("count", 5)));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("employees"), eq(Document.class)))
                .thenReturn(mockResults);

        List<Document> result = TarkQBuilderMongo.query(mongoTemplate)
                .from("employees")
                .aggregate(
                        List.of(new Document("$group",
                                new Document("_id", "$department")
                                        .append("count", new Document("$sum", 1)))),
                        Document.class
                );

        assertEquals(1, result.size());
        assertEquals(5, result.get(0).getInteger("count"));
        verify(mongoTemplate).aggregate(any(Aggregation.class), eq("employees"), eq(Document.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testRawAggregateEmptyResult() {
        AggregationResults<Document> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("users"), eq(Document.class)))
                .thenReturn(mockResults);

        List<Document> result = TarkQBuilderMongo.query(mongoTemplate)
                .from("users")
                .aggregate(List.of(new Document("$match", new Document("status", "NONEXISTENT"))));

        assertTrue(result.isEmpty());
    }
}
