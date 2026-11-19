package com.ttn.rag.generation;

import java.util.List;

public interface GeneratorClient {

    GeneratedAnswer generate(String query, String systemPrompt, List<String> contextChunks);
}