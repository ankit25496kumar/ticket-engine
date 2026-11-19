package com.ttn.rag.api;

import com.ttn.rag.api.dto.RagQueryRequest;
import com.ttn.rag.api.dto.RagQueryResponse;
import com.ttn.rag.service.RagQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rag")
public class RagQueryController {

    private final RagQueryService ragQueryService;

    public RagQueryController(RagQueryService ragQueryService) {
        this.ragQueryService = ragQueryService;
    }

    @PostMapping("/query")
    public ResponseEntity<RagQueryResponse> query(@Valid @RequestBody RagQueryRequest request) {
        RagQueryResponse response = ragQueryService.query(request.query());
        HttpStatus status =
                switch (response.outcome()) {
                    case "ANSWER", "NO_MATCH" -> HttpStatus.OK;
                    case "ERROR" -> {
                        String code =
                                response.error() != null ? response.error().code() : "INTERNAL_ERROR";
                        yield switch (code) {
                            case "INVALID_QUERY" -> HttpStatus.BAD_REQUEST;
                            case "INTERNAL_ERROR" -> HttpStatus.INTERNAL_SERVER_ERROR;
                            default -> HttpStatus.SERVICE_UNAVAILABLE;
                        };
                    }
                    default -> HttpStatus.INTERNAL_SERVER_ERROR;
                };
        return ResponseEntity.status(status).body(response);
    }
}