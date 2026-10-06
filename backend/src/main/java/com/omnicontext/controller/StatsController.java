package com.omnicontext.controller;

import com.omnicontext.dto.StatsSummaryResponse;
import com.omnicontext.service.ContextService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final ContextService contextService;

    public StatsController(ContextService contextService) {
        this.contextService = contextService;
    }

    @GetMapping
    public ResponseEntity<StatsSummaryResponse> getStats() {
        return ResponseEntity.ok(contextService.getStats());
    }
}
