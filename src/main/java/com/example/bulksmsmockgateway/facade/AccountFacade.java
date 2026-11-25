package com.example.bulksmsmockgateway.facade;

import com.example.bulksmsmockgateway.dtos.common.GatewayData;
import com.example.bulksmsmockgateway.mapper.PersistenceMapper;
import com.example.bulksmsmockgateway.repo.AccountRepository;
import com.example.bulksmsmockgateway.repo.TransferRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class AccountFacade {
    public static final long SANDBOX_ACCOUNT = 1;

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final PersistenceMapper mapper;

    public AccountFacade(AccountRepository accounts, TransferRepository transfers, PersistenceMapper mapper) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.mapper = mapper;
    }

    public boolean sandboxExists() {
        return accounts.existsById(SANDBOX_ACCOUNT);
    }

    public GatewayData.Account profile() {
        return mapper.account(accounts.findById(SANDBOX_ACCOUNT).orElseThrow());
    }

    public GatewayData.Account lockSandbox() {
        return mapper.account(accounts.lockById(SANDBOX_ACCOUNT).orElseThrow());
    }

    public Optional<GatewayData.Account> lockRecipient(long id) {
        return accounts.lockById(id).map(mapper::account);
    }

    public void changeBalance(long id, BigDecimal delta) {
        var account = accounts.findById(id).orElseThrow();
        account.balance = account.balance.add(delta);
    }

    public void transfer(GatewayData.Transfer transfer) {
        changeBalance(SANDBOX_ACCOUNT, transfer.credits().negate());
        changeBalance(transfer.toUserId(), transfer.credits());
        transfers.save(mapper.transfer(transfer));
    }
}
