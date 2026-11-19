package com.ttn.rag.api.dto;

import jakarta.validation.constraints.NotBlank;

public record RagQueryRequest(@NotBlank String query) {}