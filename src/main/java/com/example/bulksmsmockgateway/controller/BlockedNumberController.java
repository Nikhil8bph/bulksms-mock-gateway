package com.example.bulksmsmockgateway.controller;

import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.service.BlockedNumberService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/blocked-numbers")
public class BlockedNumberController {
    private final BlockedNumberService blocked;

    public BlockedNumberController(BlockedNumberService blocked) {
        this.blocked = blocked;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "blocked.list")
    List<Responses.BlockedNumber> list(@RequestParam(name = "min-id", defaultValue = "0") int minId,
                                       @RequestParam(defaultValue = "10000") int limit) {
        return blocked.list(minId, limit);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @Operation(operationId = "blocked.create")
    void create(@RequestBody List<String> numbers) {
        blocked.add(numbers);
    }
}
