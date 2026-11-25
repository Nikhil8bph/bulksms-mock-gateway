package com.example.bulksmsmockgateway.controller;

import com.example.bulksmsmockgateway.service.AccountService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {
    private final AccountService accounts;

    public HealthController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping("/healthz")
    ResponseEntity<Void> health() {
        HttpStatus status = accounts.isReady() ? HttpStatus.NO_CONTENT : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).build();
    }
}
