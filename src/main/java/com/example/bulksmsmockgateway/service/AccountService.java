package com.example.bulksmsmockgateway.service;

import com.example.bulksmsmockgateway.dtos.request.Requests;
import com.example.bulksmsmockgateway.dtos.response.Responses;
import com.example.bulksmsmockgateway.exception.ApiException;
import com.example.bulksmsmockgateway.facade.AccountFacade;
import com.example.bulksmsmockgateway.facade.MessageFacade;
import com.example.bulksmsmockgateway.mapper.ApiMapper;
import com.example.bulksmsmockgateway.validation.ApiInput;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class AccountService {
    private final AccountFacade accounts;
    private final MessageFacade messages;
    private final ApiMapper mapper;
    private final Clock clock;

    public AccountService(AccountFacade accounts, MessageFacade messages, ApiMapper mapper, Clock clock) {
        this.accounts = accounts;
        this.messages = messages;
        this.mapper = mapper;
        this.clock = clock;
    }

    public boolean isReady() {
        try {
            return accounts.sandboxExists();
        } catch (DataAccessException | TransactionException ex) {
            return false;
        }
    }

    @Transactional(readOnly = true)
    public Responses.Profile profile() {
        var account = accounts.profile();
        long sentToday = messages.sentSince(clock.instant().truncatedTo(ChronoUnit.DAYS));
        return new Responses.Profile(account.id().toString(), account.username(), account.created(),
                new Responses.Credits(account.balance(), account.creditLimit(), account.transferAllowed()),
                new Responses.Quota(account.dailyQuota(), (int) Math.max(0, account.dailyQuota() - sentToday)),
                new Responses.OriginAddresses(true, List.of()));
    }

    @Transactional
    public void transfer(Requests.Transfer request) {
        ApiInput.required(request.toUsername(), "toUsername", 100);
        ApiInput.bounded(request.commentOnFrom(), "commentOnFrom", 100);
        ApiInput.bounded(request.commentOnTo(), "commentOnTo", 100);
        if (request.credits() == null || request.credits().signum() <= 0 || request.credits().stripTrailingZeros().scale() > 4) {
            throw ApiException.invalid("credits must be positive with at most four decimal places");
        }
        long recipientId = recipientId(request);
        var source = accounts.lockSandbox();
        if (!source.transferAllowed() || source.balance().compareTo(request.credits()) < 0) {
            throw ApiException.credits();
        }
        if (recipientId <= 0 || recipientId == source.id()) {
            throw ApiException.invalid("toUserId must identify another sandbox account");
        }
        accounts.lockRecipient(recipientId)
                .filter(account -> account.username().equals(request.toUsername()))
                .orElseThrow(() -> ApiException.invalid("toUserId and toUsername must identify the same sandbox account"));
        accounts.transfer(mapper.transfer(request, recipientId, clock.instant()));
    }

    private static long recipientId(Requests.Transfer request) {
        if (request.toUserId() == null) {
            throw ApiException.invalid("toUserId is required");
        }
        try {
            return request.toUserId().longValueExact();
        } catch (ArithmeticException ex) {
            throw ApiException.invalid("toUserId must be a whole number in the supported range");
        }
    }
}
