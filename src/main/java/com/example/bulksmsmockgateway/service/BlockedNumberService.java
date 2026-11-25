package com.example.bulksmsmockgateway.service;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.facade.AccountFacade;
import com.example.bulksmsmockgateway.facade.BlockedNumberFacade;
import com.example.bulksmsmockgateway.mapper.ApiMapper;
import com.example.bulksmsmockgateway.validation.ApiInput;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Service
public class BlockedNumberService {
    private final AccountFacade accounts;
    private final BlockedNumberFacade blocked;
    private final ApiMapper mapper;
    private final Clock clock;

    public BlockedNumberService(AccountFacade accounts, BlockedNumberFacade blocked, ApiMapper mapper, Clock clock) {
        this.accounts = accounts;
        this.blocked = blocked;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Responses.BlockedNumber> list(int minId, int limit) {
        if (minId < 0 || limit < 1 || limit > 10_000) {
            throw ApiException.invalid("min-id must be non-negative and limit must be between 1 and 10000");
        }
        return blocked.list(minId, limit).stream().map(mapper::blocked).toList();
    }

    @Transactional
    public void add(List<String> numbers) {
        if (numbers.size() > 1000) {
            throw ApiException.invalid("Submit at most 1000 blocked numbers");
        }
        Instant now = clock.instant();
        var entries = numbers.stream().map(ApiInput::phone)
                .map(phone -> new GatewayData.BlockedNumber(null, phone, now, null)).toList();
        accounts.lockSandbox();
        blocked.insert(entries);
    }
}
