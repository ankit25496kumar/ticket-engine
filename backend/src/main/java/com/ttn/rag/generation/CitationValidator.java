package com.ttn.rag.generation;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class CitationValidator {

    private CitationValidator() {}

    public static boolean citationsSubsetOfKeepSet(List<String> citedTicketIds, Set<String> keepSet) {
        if (citedTicketIds == null || citedTicketIds.isEmpty()) {
            return false;
        }
        for (String id : citedTicketIds) {
            if (!keepSet.contains(id)) {
                return false;
            }
        }
        return true;
    }

    public static Set<String> ticketIdsFromChunks(List<String> chunkTexts) {
        Set<String> ids = new HashSet<>();
        for (String chunk : chunkTexts) {
            int start = 0;
            while (true) {
                int idx = chunk.indexOf("TKT-", start);
                if (idx < 0) {
                    break;
                }
                int end = idx + 4;
                while (end < chunk.length() && Character.isDigit(chunk.charAt(end))) {
                    end++;
                }
                if (end > idx + 4) {
                    ids.add(chunk.substring(idx, end));
                }
                start = idx + 4;
            }
        }
        return ids;
    }
}