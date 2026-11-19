package com.ttn.rag.generation;

import java.util.List;

public record GeneratedAnswer(String text, List<String> citedTicketIds) {}