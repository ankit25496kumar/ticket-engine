package com.ttn.rag.retrieval;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class RagRetrievalServicePolicyLiteralsTest {

    @Test
    void doesNotHardcodeTopKOrThreshold() throws Exception {
        Path source =
                Path.of("src/main/java/com/ttn/rag/retrieval/RagRetrievalService.java")
                        .toAbsolutePath();
        String content = Files.readString(source);
        assertThat(content).doesNotContain("0.72");
        assertThat(content).doesNotContain("= 5;");
        assertThat(content).doesNotContain("=5;");
    }
}