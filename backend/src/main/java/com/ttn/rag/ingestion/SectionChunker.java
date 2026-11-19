package com.ttn.rag.ingestion;

import com.ttn.rag.config.RagChunkProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class SectionChunker {

    private final RagChunkProperties chunkProperties;

    public SectionChunker(RagChunkProperties chunkProperties) {
        this.chunkProperties = chunkProperties;
    }

    public List<String> chunk(String ticketId, String title, String document) {
        String prefix = "[Ticket " + ticketId + "] " + nullToEmpty(title) + "\n";
        int maxChars = chunkProperties.getMaxTokens() * 4;
        int overlapChars = chunkProperties.getOverlapTokens() * 4;
        String full = prefix + document;
        List<String> bodies = new ArrayList<>();
        if (full.length() <= maxChars) {
            bodies.add(full);
            return bodies;
        }
        int start = 0;
        while (start < document.length()) {
            int end = Math.min(document.length(), start + maxChars);
            String window = document.substring(start, end);
            bodies.add(prefix + window);
            if (end >= document.length()) {
                break;
            }
            start = Math.max(0, end - overlapChars);
        }
        return bodies;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}