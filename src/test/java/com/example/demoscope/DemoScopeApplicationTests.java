package com.example.demoscope;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class DemoScopeApplicationTests {

    @Autowired
    private LongTermMemoryRepository longTermMemoryRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void pgvectorDisabledUsesEmptyLongTermMemoryRepository() {
        assertTrue(longTermMemoryRepository instanceof EmptyLongTermMemoryRepository);
    }
}
