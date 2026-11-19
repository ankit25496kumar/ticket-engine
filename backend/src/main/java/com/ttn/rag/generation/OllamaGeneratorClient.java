package com.ttn.rag.generation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ttn.rag.config.RagGenerationProperties;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("!test")
public class OllamaGeneratorClient implements GeneratorClient {

    private static final Pattern TKT = Pattern.compile("TKT-[0-9]+");

    private final RestClient restClient;
    private final RagGenerationProperties properties;
    private final ObjectMapper objectMapper;

    public OllamaGeneratorClient(RagGenerationProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(properties.getBaseUrl()).build();
    }

    @Override
    public GeneratedAnswer generate(String query, String systemPrompt, List<String> contextChunks) {
        try {
            StringBuilder user = new StringBuilder("Context:\n");
            for (String chunk : contextChunks) {
                user.append(chunk).append("\n---\n");
            }
            user.append("\nQuestion: ").append(query);
            String body =
                    objectMapper.writeValueAsString(
                            Map.of(
                                    "model",
                                    properties.getModel(),
                                    "stream",
                                    false,
                                    "messages",
                                    List.of(
                                            Map.of("role", "system", "content", systemPrompt),
                                            Map.of("role", "user", "content", user.toString()))));
            ChatResponse response =
                    restClient
                            .post()
                            .uri("/api/chat")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(body)
                            .retrieve()
                            .body(ChatResponse.class);
            if (response == null || response.message == null || response.message.content == null) {
                throw new GeneratorException("Empty generator response");
            }
            String text = response.message.content;
            Set<String> ids = new LinkedHashSet<>();
            Matcher m = TKT.matcher(text);
            while (m.find()) {
                ids.add(m.group());
            }
            return new GeneratedAnswer(text, new ArrayList<>(ids));
        } catch (RestClientException | java.io.IOException ex) {
            throw new GeneratorException("Generation failed", ex);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatResponse {
        public ChatMessage message;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ChatMessage {
        public String content;
    }
}