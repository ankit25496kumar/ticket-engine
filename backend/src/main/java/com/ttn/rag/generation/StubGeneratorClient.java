package com.ttn.rag.generation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("test")
public class StubGeneratorClient implements GeneratorClient {

    private static final Pattern TKT = Pattern.compile("TKT-[0-9]+");

    @Override
    public GeneratedAnswer generate(String query, String systemPrompt, List<String> contextChunks) {
        Set<String> ids = new LinkedHashSet<>();
        StringBuilder context = new StringBuilder();
        for (String chunk : contextChunks) {
            context.append(chunk).append("\n---\n");
            Matcher m = TKT.matcher(chunk);
            while (m.find()) {
                ids.add(m.group());
            }
        }
        String answer = "Based on retrieved tickets: " + context.substring(0, Math.min(200, context.length()));
        return new GeneratedAnswer(answer, new ArrayList<>(ids));
    }
}