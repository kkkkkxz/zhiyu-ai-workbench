package com.zwskchat.zwskchat.vector;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zwskchat.zwskchat.common.api.ResultCode;
import com.zwskchat.zwskchat.common.exception.BusinessException;
import com.zwskchat.zwskchat.config.properties.AiProperties;
import com.zwskchat.zwskchat.config.properties.QdrantProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Qdrant REST 客户端。仅实现：
 *   - 确保 Collection 存在（按租户分库）
 *   - upsert 批量向量
 *   - search 单向量 topK，支持 payload 过滤（kbId/permission）
 *
 * Collection 命名：{prefix}_{tenantId}
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class QdrantClient {

    private final QdrantProperties qdrantProps;
    private final AiProperties aiProps;
    private final ObjectMapper om;
    private HttpClient http;
    private final ConcurrentMap<String, Boolean> ensuredCollections = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, qdrantProps.getConnectTimeoutSeconds())))
                .version(HttpClient.Version.HTTP_1_1)
                .build();
    }

    public String collectionName(Long tenantId) {
        return qdrantProps.getCollectionPrefix() + "_" + (tenantId == null ? "0" : tenantId);
    }

    public void ensureCollection(Long tenantId) {
        String name = collectionName(tenantId);
        if (ensuredCollections.containsKey(name)) return;
        try {
            HttpResponse<String> get = doRequest("GET", "/collections/" + name, null);
            if (get.statusCode() == 200) {
                ensuredCollections.put(name, Boolean.TRUE);
                return;
            }
            // 不存在则创建
            ObjectNode body = om.createObjectNode();
            ObjectNode vectors = body.putObject("vectors");
            vectors.put("size", aiProps.getEmbedding().getDimension());
            vectors.put("distance", qdrantProps.getDistance());
            HttpResponse<String> put = doRequest("PUT", "/collections/" + name, om.writeValueAsString(body));
            if (put.statusCode() / 100 != 2) {
                log.warn("[Qdrant] create collection failed status={} body={}", put.statusCode(), put.body());
                throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "Qdrant 创建 Collection 失败");
            }
            ensuredCollections.put(name, Boolean.TRUE);
            log.info("[Qdrant] collection created: {}", name);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Qdrant] ensureCollection error", e);
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "Qdrant 不可用");
        }
    }

    public void upsert(Long tenantId, List<UpsertPoint> points) {
        if (points == null || points.isEmpty()) return;
        ensureCollection(tenantId);
        String name = collectionName(tenantId);
        try {
            ObjectNode body = om.createObjectNode();
            ArrayNode arr = body.putArray("points");
            for (UpsertPoint p : points) {
                ObjectNode node = arr.addObject();
                node.put("id", p.id());
                ArrayNode vec = node.putArray("vector");
                for (float v : p.vector()) vec.add(v);
                ObjectNode payload = node.putObject("payload");
                if (p.payload() != null) {
                    p.payload().forEach((k, v) -> {
                        if (v == null) return;
                        // 19 位 Snowflake ID 如果写成 JSON number，Qdrant 内部会以 IEEE 754 double 存，
                        // 导致后 2 位有效数字丢失（例如 ...547714 -> ...547712）。所以 Long 一律转字符串存。
                        if (v instanceof Long l) payload.put(k, String.valueOf(l));
                        else if (v instanceof Number n) payload.put(k, n.doubleValue());
                        else if (v instanceof Boolean b) payload.put(k, b);
                        else payload.put(k, String.valueOf(v));
                    });
                }
            }
            HttpResponse<String> resp = doRequest("PUT",
                    "/collections/" + name + "/points?wait=true",
                    om.writeValueAsString(body));
            if (resp.statusCode() / 100 != 2) {
                log.warn("[Qdrant] upsert failed status={} body={}", resp.statusCode(), resp.body());
                throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "Qdrant upsert 失败");
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("[Qdrant] upsert error", e);
            throw new BusinessException(ResultCode.DEPENDENCY_UNAVAILABLE, "Qdrant 不可用");
        }
    }

    public List<SearchHit> search(Long tenantId, float[] vector, int topK, double scoreThreshold,
                                   Map<String, Object> mustEqualFilters) {
        String name = collectionName(tenantId);
        try {
            ensureCollection(tenantId);
        } catch (Exception e) {
            log.warn("[Qdrant] ensure collection unavailable before search, fallback empty result. collection={}, error={}: {}",
                    name, e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        }
        try {
            ObjectNode body = om.createObjectNode();
            ArrayNode vec = body.putArray("vector");
            for (float v : vector) vec.add(v);
            body.put("limit", topK);
            body.put("with_payload", true);
            body.put("with_vector", false);
            if (scoreThreshold > 0) body.put("score_threshold", scoreThreshold);
            if (mustEqualFilters != null && !mustEqualFilters.isEmpty()) {
                ObjectNode filter = body.putObject("filter");
                ArrayNode must = filter.putArray("must");
                mustEqualFilters.forEach((k, v) -> {
                    ObjectNode cond = must.addObject();
                    cond.put("key", k);
                    ObjectNode match = cond.putObject("match");
                    // 与 upsert 一致：Long 走字符串 match，以免雪花 ID 精度丢失。
                    if (v instanceof Long l) match.put("value", String.valueOf(l));
                    else if (v instanceof Number n) match.put("value", n.longValue());
                    else if (v instanceof Boolean b) match.put("value", b);
                    else match.put("value", String.valueOf(v));
                });
            }
            HttpResponse<String> resp = doRequest("POST",
                    "/collections/" + name + "/points/search",
                    om.writeValueAsString(body),
                    Math.max(1, qdrantProps.getSearchTimeoutSeconds()));
            if (resp.statusCode() / 100 != 2) {
                log.warn("[Qdrant] search failed status={} body={}", resp.statusCode(), resp.body());
                return List.of();
            }
            JsonNode root = om.readTree(resp.body());
            JsonNode arr = root.path("result");
            List<SearchHit> hits = new ArrayList<>();
            for (int i = 0; i < arr.size(); i++) {
                JsonNode n = arr.get(i);
                long id = n.path("id").asLong();
                double score = n.path("score").asDouble();
                Map<String, Object> payload = new LinkedHashMap<>();
                JsonNode p = n.path("payload");
                if (p.isObject()) {
                    p.properties().forEach(e -> payload.put(e.getKey(),
                            e.getValue().isNumber() ? e.getValue().asDouble() : e.getValue().asText()));
                }
                hits.add(new SearchHit(id, score, payload));
            }
            return hits;
        } catch (HttpTimeoutException e) {
            log.warn("[Qdrant] search timed out after {}s, fallback empty result. collection={}",
                    Math.max(1, qdrantProps.getSearchTimeoutSeconds()), name);
            return List.of();
        } catch (Exception e) {
            log.warn("[Qdrant] search unavailable, fallback empty result. collection={}, error={}: {}",
                    name, e.getClass().getSimpleName(), e.getMessage());
            return List.of();
        }
    }

    private HttpResponse<String> doRequest(String method, String path, String body) throws Exception {
        return doRequest(method, path, body, Math.max(1, qdrantProps.getRequestTimeoutSeconds()));
    }

    private HttpResponse<String> doRequest(String method, String path, String body, int timeoutSeconds) throws Exception {
        URI uri = URI.create("http://" + qdrantProps.getHost() + ":" + qdrantProps.getPort() + path);
        HttpRequest.Builder b = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(Math.max(1, timeoutSeconds)))
                .header("Content-Type", "application/json");
        if (qdrantProps.getApiKey() != null && !qdrantProps.getApiKey().isBlank()) {
            b.header("api-key", qdrantProps.getApiKey());
        }
        switch (method) {
            case "GET" -> b.GET();
            case "POST" -> b.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
            case "PUT" -> b.PUT(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
            case "DELETE" -> b.DELETE();
            default -> throw new IllegalArgumentException(method);
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    public record UpsertPoint(long id, float[] vector, Map<String, Object> payload) {}

    public record SearchHit(long id, double score, Map<String, Object> payload) {}
}
