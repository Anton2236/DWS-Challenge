package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.Transfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
public class TransferService {

    private final AccountsService accountsService;
    private final NotificationService notificationService;

    @Autowired
    public TransferService(AccountsService accountsService, NotificationService notificationService) {
        this.accountsService = accountsService;
        this.notificationService = notificationService;
    }

    public synchronized void makeTransfer(Transfer transfer) {
        String accountFromId = transfer.getAccountFrom();
        String accountToId = transfer.getAccountTo();

        // Make sure accountFrom and accountTo are different accounts
        if (Objects.equals(accountToId, accountFromId)) {
            throw new IllegalArgumentException("accountFrom and accountTo should be different");
        }
        // Retrieve accounts by their ids
        Account accountFrom = accountsService.getAccount(accountFromId);
        if (accountFrom == null) {
            throw new NoSuchElementException(String.format("account %s not found", accountFromId));
        }
        Account accountTo = accountsService.getAccount(accountToId);
        if (accountTo == null) {
            throw new NoSuchElementException(String.format("account %s not found", accountFromId));
        }

        // Check Transfer amount is positive
        BigDecimal transferAmount = transfer.getAmount();
        if (transferAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("transfer amount should be positive");
        }

        BigDecimal accountFromBalance = accountFrom.getBalance();
        BigDecimal accountToBalance = accountTo.getBalance();

        //Check if there is enough money in accountFrom
        if (accountFromBalance.compareTo(transferAmount) < 0) {
            throw new IllegalArgumentException(String.format("accountFrom(%s) balance is less than transfer amount", accountFromId));
        }

        // Make the transfer
        accountFrom.setBalance(accountFromBalance.subtract(transferAmount));
        accountTo.setBalance(accountToBalance.add(transferAmount));

        //Notify accounts about the transfer
        notificationService.notifyAboutTransfer(accountFrom, String.format("Transferred %s to account %s. Balance: %s", transferAmount, accountToId, accountFrom.getBalance()));
        notificationService.notifyAboutTransfer(accountTo, String.format("Received %s from account %s. Balance: %s", transferAmount, accountFromId, accountTo.getBalance()));
    }
}
