package com.example.demoscope.biz.memory;

import com.example.demoscope.biz.interview.MemoryWriteDecision;
import com.example.demoscope.domain.interview.InterviewMemoryWriter;
import com.example.demoscope.domain.interview.InterviewSnapshot;
import com.example.demoscope.biz.memory.LongTermMemoryPolicy;
import com.example.demoscope.domain.memory.LongTermMemoryCandidate;
import com.example.demoscope.domain.memory.LongTermMemoryCategory;
import com.example.demoscope.domain.memory.LongTermMemoryRepository;
import com.example.demoscope.domain.memory.MemoryTurn;
import com.example.demoscope.domain.memory.ShortTermMemoryStore;
import java.time.Clock;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultInterviewMemoryWriter implements InterviewMemoryWriter {

    private static final Logger log =
            LoggerFactory.getLogger(DefaultInterviewMemoryWriter.class);

    private final ShortTermMemoryStore shortTermMemoryStore;
    private final LongTermMemoryRepository longTermMemoryRepository;
    private final LongTermMemoryPolicy longTermMemoryPolicy;
    private final Clock clock;

    public DefaultInterviewMemoryWriter(
            ShortTermMemoryStore shortTermMemoryStore,
            LongTermMemoryRepository longTermMemoryRepository,
            LongTermMemoryPolicy longTermMemoryPolicy,
            Clock clock) {
        this.shortTermMemoryStore = shortTermMemoryStore;
        this.longTermMemoryRepository = longTermMemoryRepository;
        this.longTermMemoryPolicy = longTermMemoryPolicy;
        this.clock = clock;
    }

    @Override
    public void write(InterviewSnapshot snapshot, MemoryWriteDecision decision) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(decision, "decision");
        String userId = snapshot.session().userId();
        String conversationId =
                InterviewMemoryContextProvider.interviewConversationId(snapshot);
        for (String write : decision.shortTermWrites()) {
            try {
                shortTermMemoryStore.append(
                        userId,
                        conversationId,
                        new MemoryTurn(
                                "interview-memory",
                                write,
                                clock.instant()));
            } catch (RuntimeException exception) {
                log.warn("Interview short-term memory write failed");
            }
        }
        for (String write : decision.longTermWrites()) {
            LongTermMemoryCandidate candidate = new LongTermMemoryCandidate(
                    LongTermMemoryCategory.STABLE_FACT,
                    write,
                    0.8);
            if (!longTermMemoryPolicy.isAllowed(candidate)) {
                continue;
            }
            try {
                longTermMemoryRepository.save(
                        userId,
                        conversationId,
                        candidate);
            } catch (RuntimeException exception) {
                log.warn("Interview long-term memory write failed");
            }
        }
    }
}
