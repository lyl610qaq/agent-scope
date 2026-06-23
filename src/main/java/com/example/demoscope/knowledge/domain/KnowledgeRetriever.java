package com.example.demoscope.knowledge.domain;

import java.util.List;

@FunctionalInterface
public interface KnowledgeRetriever {

    List<KnowledgeChunk> retrieve(SemanticQuery query);
}
