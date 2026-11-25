package com.example.bulksmsmockgateway.controller;

import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/v1")
public class AccountController {
    private final AccountService accounts;

    public AccountController(AccountService accounts) {
        this.accounts = accounts;
    }

    @GetMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "profile.show")
    Responses.Profile profile() {
        return accounts.profile();
    }

    @PostMapping(value = "/credit/transfer", consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "credit.transfer")
    void transfer(@RequestBody Requests.Transfer request) {
        accounts.transfer(request);
    }
}
