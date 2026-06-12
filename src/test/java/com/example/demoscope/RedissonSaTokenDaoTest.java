package com.example.demoscope;

import static cn.dev33.satoken.dao.SaTokenDao.NEVER_EXPIRE;
import static cn.dev33.satoken.dao.SaTokenDao.NOT_VALUE_EXPIRE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;

@SuppressWarnings({"deprecation", "unchecked"})
class RedissonSaTokenDaoTest {

    private RedissonClient redissonClient;
    private RBucket<Object> bucket;
    private RKeys keys;
    private RedissonSaTokenDao dao;

    @BeforeEach
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        bucket = mock(RBucket.class);
        keys = mock(RKeys.class);
        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
        when(redissonClient.getKeys()).thenReturn(keys);
        dao = new RedissonSaTokenDao(redissonClient);
    }

    @Test
    void getConvertsRedisValueToStringAndPreservesNull() {
        when(bucket.get()).thenReturn(42L).thenReturn(null);

        assertEquals("42", dao.get("login-id"));
        assertNull(dao.get("missing"));
    }

    @Test
    void setWithoutExpirationUsesPlainSet() {
        dao.set("key", "value", NEVER_EXPIRE);

        verify(bucket).set("value");
        verify(bucket, never()).set("value", NEVER_EXPIRE, TimeUnit.SECONDS);
    }

    @Test
    void setWithPositiveTimeoutUsesSeconds() {
        dao.set("key", "value", 30);

        verify(bucket).set("value", 30, TimeUnit.SECONDS);
        verify(bucket, never()).set("value");
    }

    @Test
    void setIgnoresZeroAndInvalidTimeouts() {
        dao.set("key", "value", 0);
        dao.set("key", "value", NOT_VALUE_EXPIRE);
        dao.set("key", "value", -3);

        verifyNoInteractions(bucket);
    }

    @Test
    void updateKeepsExistingTtl() {
        dao.update("key", "updated");

        verify(bucket).setAndKeepTTL("updated");
    }

    @Test
    void deleteRemovesBucket() {
        dao.delete("key");

        verify(bucket).delete();
    }

    @Test
    void getTimeoutConvertsMillisecondsAndPreservesSentinels() {
        when(bucket.remainTimeToLive()).thenReturn(2_500L, 500L, NEVER_EXPIRE, NOT_VALUE_EXPIRE);

        assertEquals(2, dao.getTimeout("seconds"));
        assertEquals(1, dao.getTimeout("sub-second"));
        assertEquals(NEVER_EXPIRE, dao.getTimeout("persistent"));
        assertEquals(NOT_VALUE_EXPIRE, dao.getTimeout("missing"));
    }

    @Test
    void updateTimeoutClearsExpirationForNeverExpire() {
        dao.updateTimeout("key", NEVER_EXPIRE);

        verify(bucket).clearExpire();
        verify(bucket, never()).expire(NEVER_EXPIRE, TimeUnit.SECONDS);
    }

    @Test
    void updateTimeoutExpiresPositiveValuesInSeconds() {
        dao.updateTimeout("key", 45);

        verify(bucket).expire(45, TimeUnit.SECONDS);
        verify(bucket, never()).clearExpire();
    }

    @Test
    void updateTimeoutIgnoresZeroAndInvalidValues() {
        dao.updateTimeout("key", 0);
        dao.updateTimeout("key", NOT_VALUE_EXPIRE);
        dao.updateTimeout("key", -3);

        verifyNoInteractions(bucket);
    }

    @Test
    void searchDataBuildsPatternSortsAscendingAndPages() {
        when(keys.getKeysByPattern("satoken:*login*"))
                .thenReturn(List.of("satoken:c-login", "satoken:a-login", "satoken:b-login"));

        List<String> result = dao.searchData("satoken:", "login", 1, 2, true);

        assertEquals(List.of("satoken:b-login", "satoken:c-login"), result);
        assertThrows(UnsupportedOperationException.class, () -> result.add("satoken:d-login"));
    }

    @Test
    void searchDataSortsDescendingAndReadsToEndForNegativeSize() {
        when(keys.getKeysByPattern("satoken:*login*"))
                .thenReturn(List.of("satoken:a-login", "satoken:c-login", "satoken:b-login"));

        assertEquals(
                List.of("satoken:b-login", "satoken:a-login"),
                dao.searchData("satoken:", "login", 1, -1, false));
    }

    @Test
    void searchDataClampsNegativeStartAndHandlesNullSearchParts() {
        when(keys.getKeysByPattern(anyString())).thenReturn(List.of("b", "a"));

        assertEquals(List.of("a"), dao.searchData(null, null, -5, 1, true));

        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(keys).getKeysByPattern(pattern.capture());
        assertFalse(pattern.getValue().contains("null"));
    }
}
