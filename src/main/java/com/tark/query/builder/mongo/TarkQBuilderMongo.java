package com.tark.query.builder.mongo;

import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;

import java.util.*;
import java.util.stream.Collectors;

public class TarkQBuilderMongo {

    private final MongoTemplate mongoTemplate;

    private String collection;
    private final List<String> selectFields = new ArrayList<>();
    private final List<Criteria> criterias = new ArrayList<>();
    private final List<LookupStage> lookups = new ArrayList<>();
    private Integer limit;
    private Long skip;
    private Sort sort;

    public TarkQBuilderMongo(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public static TarkQBuilderMongo query(MongoTemplate mongoTemplate) {
        return new TarkQBuilderMongo(mongoTemplate);
    }

    public TarkQBuilderMongo from(String collection) {
        this.collection = collection;
        return this;
    }

    public TarkQBuilderMongo select(String... fields) {
        selectFields.addAll(Arrays.asList(fields));
        return this;
    }

    public TarkQBuilderMongo where(String field, Object value) {
        criterias.add(Criteria.where(field).is(value));
        return this;
    }

    public TarkQBuilderMongo where(String field, String operator, Object value) {
        Criteria criteria = Criteria.where(field);
        switch (operator.toUpperCase()) {
            case "="        -> criteria.is(value);
            case "!="       -> criteria.ne(value);
            case ">"        -> criteria.gt(value);
            case ">="       -> criteria.gte(value);
            case "<"        -> criteria.lt(value);
            case "<="       -> criteria.lte(value);
            case "LIKE"     -> criteria.regex(toMongoRegex(value.toString()), "i");
            case "NOT LIKE" -> criteria.not().regex(toMongoRegex(value.toString()), "i");
            default -> throw new IllegalArgumentException("Operator not allowed: " + operator);
        }
        criterias.add(criteria);
        return this;
    }

    public TarkQBuilderMongo whereIn(String field, List<?> values) {
        criterias.add(Criteria.where(field).in(values));
        return this;
    }

    public TarkQBuilderMongo whereNotIn(String field, List<?> values) {
        criterias.add(Criteria.where(field).nin(values));
        return this;
    }

    public TarkQBuilderMongo whereNull(String field) {
        criterias.add(Criteria.where(field).is(null));
        return this;
    }

    public TarkQBuilderMongo whereNotNull(String field) {
        criterias.add(Criteria.where(field).exists(true).ne(null));
        return this;
    }

    public TarkQBuilderMongo lookup(String from, String localField, String foreignField, String as) {
        lookups.add(new LookupStage(from, localField, foreignField, as));
        return this;
    }

    public TarkQBuilderMongo orderBy(String field, String direction) {
        String dir = direction.toUpperCase();
        if (!dir.equals("ASC") && !dir.equals("DESC")) {
            throw new IllegalArgumentException("Direction must be ASC or DESC, got: " + direction);
        }
        this.sort = Sort.by(dir.equals("ASC") ? Sort.Direction.ASC : Sort.Direction.DESC, field);
        return this;
    }

    public TarkQBuilderMongo limit(int limit) {
        this.limit = limit;
        return this;
    }

    public TarkQBuilderMongo skip(long skip) {
        this.skip = skip;
        return this;
    }

    public TarkQBuilderMongo offset(long offset) {
        return skip(offset);
    }

    public Query buildQuery() {
        Query query = new Query();

        if (!criterias.isEmpty()) {
            query.addCriteria(new Criteria().andOperator(criterias.toArray(new Criteria[0])));
        }

        if (!selectFields.isEmpty()) {
            org.springframework.data.mongodb.core.query.Field projection = query.fields();
            selectFields.forEach(projection::include);
        }

        if (sort != null) query.with(sort);
        if (limit != null) query.limit(limit);
        if (skip != null) query.skip(skip);

        return query;
    }

    public TarkQBuilderMongo showQuery() {
        Query query = buildQuery();
        System.out.println("Filter : " + query.getQueryObject().toJson());
        System.out.println("Fields : " + query.getFieldsObject().toJson());
        System.out.println("Sort   : " + query.getSortObject().toJson());
        if (limit != null) System.out.println("Limit  : " + limit);
        if (skip != null)  System.out.println("Skip   : " + skip);
        return this;
    }

    public List<Document> fetchAll() {
        return mongoTemplate.find(buildQuery(), Document.class, collection);
    }

    public <T> List<T> fetchAll(Class<T> clazz) {
        return mongoTemplate.find(buildQuery(), clazz, collection);
    }

    public Optional<Document> first() {
        Integer savedLimit = this.limit;
        this.limit = 1;
        try {
            Document doc = mongoTemplate.findOne(buildQuery(), Document.class, collection);
            return Optional.ofNullable(doc);
        } finally {
            this.limit = savedLimit;
        }
    }

    public long count() {
        return mongoTemplate.count(buildQuery(), collection);
    }

    public List<LookupStage> getLookups() {
        return Collections.unmodifiableList(lookups);
    }

    /**
     * Execute a MongoDB filter using a raw JSON string and return results as
     * {@code List<Document>}.
     *
     * <p><b>NoSQL INJECTION WARNING:</b> {@code json} is parsed directly into the filter query.
     * Never build the {@code json} string by interpolating user input — an attacker could
     * inject MongoDB operators (e.g. {@code $where}, {@code $gt}) to manipulate query logic.
     * Use {@link #where(String, Object)} for dynamic values from users.
     *
     * <pre>
     * // SAFE — hardcoded JSON literal
     * .rawFilter("{\"status\": \"ACTIVE\", \"age\": {\"\$gt\": 18}}")
     *
     * // DANGEROUS — JSON built from user input
     * .rawFilter("{\"name\": \"" + request.getParam("name") + "\"}")
     * </pre>
     */
    public List<Document> rawFilter(String json) {
        return mongoTemplate.find(new BasicQuery(json), Document.class, collection);
    }

    /**
     * Execute a MongoDB filter using a raw JSON string and map results to {@code clazz}.
     *
     * <p><b>NoSQL INJECTION WARNING:</b> See {@link #rawFilter(String)}.
     *
     * <pre>
     * List&lt;UserModel&gt; users = tark.rawFilter("{\"status\": \"ACTIVE\"}", UserModel.class);
     * </pre>
     */
    public <T> List<T> rawFilter(String json, Class<T> clazz) {
        return mongoTemplate.find(new BasicQuery(json), clazz, collection);
    }

    /**
     * Execute a MongoDB filter using a raw BSON {@link Document} and return results as
     * {@code List<Document>}.
     *
     * <p>Safer than the {@link #rawFilter(String)} overload because there is no JSON string
     * parsing — values are added programmatically to the {@code Document} rather than interpolated
     * into a string.
     *
     * <pre>
     * Document filter = new Document("status", "ACTIVE").append("age", new Document("\$gt", 18));
     * tark.rawFilter(filter)
     * </pre>
     */
    public List<Document> rawFilter(Document filterDoc) {
        return mongoTemplate.find(new BasicQuery(filterDoc, new Document()), Document.class, collection);
    }

    /**
     * Execute a MongoDB filter using a raw BSON {@link Document} and map results to {@code clazz}.
     *
     * <pre>
     * Document filter = new Document("status", "ACTIVE");
     * List&lt;UserModel&gt; users = tark.rawFilter(filter, UserModel.class);
     * </pre>
     */
    public <T> List<T> rawFilter(Document filterDoc, Class<T> clazz) {
        return mongoTemplate.find(new BasicQuery(filterDoc, new Document()), clazz, collection);
    }

    /**
     * Run a raw MongoDB aggregation pipeline using a list of {@link Document} pipeline stages
     * and return the results as {@code List<Document>}.
     *
     * <p><b>NoSQL INJECTION WARNING:</b> Ensure that values inside the pipeline Documents do not
     * come directly from user input without validation.
     *
     * <pre>
     * tark.aggregate(List.of(
     *     new Document("\$match",  new Document("status", "ACTIVE")),
     *     new Document("\$group",  new Document("_id", "\$department").append("total", new Document("\$sum", 1))),
     *     new Document("\$sort",   new Document("total", -1))
     * ))
     * </pre>
     */
    public List<Document> aggregate(List<Document> pipeline) {
        List<AggregationOperation> ops = pipeline.stream()
                .map(doc -> (AggregationOperation) ctx -> doc)
                .collect(Collectors.toList());
        return mongoTemplate.aggregate(
                Aggregation.newAggregation(ops), collection, Document.class
        ).getMappedResults();
    }

    /**
     * Run a raw MongoDB aggregation pipeline and map results to {@code clazz}.
     *
     * <pre>
     * List&lt;SummaryModel&gt; result = tark.aggregate(pipeline, SummaryModel.class);
     * </pre>
     */
    public <T> List<T> aggregate(List<Document> pipeline, Class<T> clazz) {
        List<AggregationOperation> ops = pipeline.stream()
                .map(doc -> (AggregationOperation) ctx -> doc)
                .collect(Collectors.toList());
        return mongoTemplate.aggregate(
                Aggregation.newAggregation(ops), collection, clazz
        ).getMappedResults();
    }

    private static String toMongoRegex(String likePattern) {
        return likePattern.replace("%", ".*").replace("_", ".");
    }

    public record LookupStage(String from, String localField, String foreignField, String as) {}
}
