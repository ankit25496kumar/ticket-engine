package com.ttn.ticket.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(String author, @NotBlank String content) {}