package com.kanvra.kafka.deadletter;

import com.kanvra.kafka.deadletter.dto.DeadLetterResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only inspection of parked messages (docs/TECH_DOC.md §20). Ops utility
 * for the MVP: any authenticated user may view the DLT; manual recovery is a
 * documented runbook step rather than an in-app flow.
 */
@RestController
@RequestMapping("/api/v1/dead-letters")
public class DeadLetterController {

    private final DeadLetterService deadLetterService;

    public DeadLetterController(DeadLetterService deadLetterService) {
        this.deadLetterService = deadLetterService;
    }

    @GetMapping
    public List<DeadLetterResponse> latest() {
        return deadLetterService.latest().stream().map(DeadLetterResponse::from).toList();
    }
}