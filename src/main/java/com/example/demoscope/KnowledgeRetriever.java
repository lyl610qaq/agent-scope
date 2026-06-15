package com.example.demoscope;

import java.util.List;

@FunctionalInterface
public interface KnowledgeRetriever {

    List<KnowledgeChunk> retrieve(SemanticQuery query);
}
