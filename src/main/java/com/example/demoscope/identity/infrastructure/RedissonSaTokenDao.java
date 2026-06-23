package com.example.demoscope.identity.infrastructure;

import static cn.dev33.satoken.dao.SaTokenDao.NEVER_EXPIRE;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import cn.dev33.satoken.dao.auto.SaTokenDaoByObjectFollowString;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

@SuppressWarnings("deprecation")
public final class RedissonSaTokenDao implements SaTokenDaoByObjectFollowString {

    private final RedissonClient redissonClient;

    public RedissonSaTokenDao(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient");
    }

    @Override
    public String get(String key) {
        Object value = bucket(key).get();
        return value == null ? null : value.toString();
    }

    @Override
    public void set(String key, String value, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            bucket(key).set(value);
        } else if (timeout > 0) {
            bucket(key).set(value, timeout, TimeUnit.SECONDS);
        }
    }

    @Override
    public void update(String key, String value) {
        bucket(key).setAndKeepTTL(value);
    }

    @Override
    public void delete(String key) {
        bucket(key).delete();
    }

    @Override
    public long getTimeout(String key) {
        long timeoutMillis = bucket(key).remainTimeToLive();
        if (timeoutMillis < 0) {
            return timeoutMillis;
        }
        return timeoutMillis / 1000 + 1;
    }

    @Override
    public void updateTimeout(String key, long timeout) {
        if (timeout == NEVER_EXPIRE) {
            bucket(key).clearExpire();
        } else if (timeout > 0) {
            bucket(key).expire(timeout, TimeUnit.SECONDS);
        }
    }

    @Override
    public List<String> searchData(
            String prefix,
            String keyword,
            int start,
            int size,
            boolean sortType) {
        String pattern = Objects.toString(prefix, "") + "*" + Objects.toString(keyword, "") + "*";
        List<String> matches = new ArrayList<>();
        redissonClient.getKeys().getKeysByPattern(pattern).forEach(matches::add);
        matches.sort(sortType ? Comparator.naturalOrder() : Comparator.reverseOrder());

        int fromIndex = Math.min(Math.max(start, 0), matches.size());
        int toIndex = size < 0
                ? matches.size()
                : (int) Math.min(matches.size(), (long) fromIndex + size);
        return List.copyOf(matches.subList(fromIndex, toIndex));
    }

    private RBucket<Object> bucket(String key) {
        return redissonClient.getBucket(key);
    }
}
