package com.skygo.backend.flight.service;

import com.skygo.backend.flight.contract.AirportPageResponse;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class AirportListCache {
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final Duration ttl;

    public AirportListCache(
            StringRedisTemplate redis,
            ObjectMapper mapper,
            @Value("${skygo.cache.airports.ttl:10m}") Duration ttl) {
        this.redis = redis;
        this.mapper = mapper;
        this.ttl = ttl;
    }

    public record Query(
            String keyword, String country, String region, int page, int size) {
    }

    public AirportPageResponse getOrLoad(
            Query query, Supplier<AirportPageResponse> loader) {
        // Không lưu kết quả đọc trong một giao dịch ghi chưa commit.
        // Điều này cũng tránh cache dữ liệu fixture của AirportIntegrationTest.
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && !TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            return loader.get();
        }

        String key = key(query);

        try {
            String json = redis.opsForValue().get(key);
            if (json != null) {
                return mapper.readValue(json, AirportPageResponse.class);
            }
        } catch (DataAccessException | JacksonException ex) {
            // Redis không sẵn sàng hoặc dữ liệu cache hỏng: đọc MariaDB.
        }

        AirportPageResponse result = loader.get();

        try {
            redis.opsForValue().set(key, mapper.writeValueAsString(result), ttl);
        } catch (DataAccessException | JacksonException ex) {
            // Vẫn trả kết quả đã đọc được từ MariaDB.
        }

        return result;
    }

    private String key(Query query) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(mapper.writeValueAsBytes(query));
            return "skygo:airports:v1:" + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}