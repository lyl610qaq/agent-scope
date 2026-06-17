package com.example.demoscope;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InterviewMemoryContextProvider {

    private static final Logger log =
            LoggerFactory.getLogger(InterviewMemoryContextProvider.class);

    private final ShortTermMemoryStore shortTermMemoryStore;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final EmbeddingClient embeddingClient;

    public InterviewMemoryContextProvider(
            ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryRepository longTermMemoryRepository,
            EmbeddingClient embeddingClient) {
        this.shortTermMemoryStore = shortTermMemoryStore;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.embeddingClient = embeddingClient;
    }

    public List<MemoryTurn> shortTerm(InterviewSnapshot snapshot) {
        try {
            return shortTermMemoryStore.recent(
                    snapshot.session().userId(),
                    interviewConversationId(snapshot));
        } catch (RuntimeException exception) {
            log.warn("Interview short-term memory read failed");
            return List.of();
        }
    }

    public List<LongTermMemory> longTerm(InterviewSnapshot snapshot) {
        try {
            String query = "Java backend interview "
                    + snapshot.session().direction()
                    + " "
                    + snapshot.session().difficulty();
            return longTermMemoryRepository.findRelevant(
                    snapshot.session().userId(),
                    new SemanticQuery(query, embeddingClient.embed(query)));
        } catch (RuntimeException exception) {
            log.warn("Interview long-term memory read failed");
            return List.of();
        }
    }

    static String interviewConversationId(InterviewSnapshot snapshot) {
        return "interview:" + snapshot.session().id();
    }
}
