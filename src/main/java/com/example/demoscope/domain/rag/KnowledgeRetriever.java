package com.example.demoscope.domain.rag;

import java.util.List;

@FunctionalInterface
public interface KnowledgeRetriever {

    List<KnowledgeChunk> retrieve(SemanticQuery query);
}
