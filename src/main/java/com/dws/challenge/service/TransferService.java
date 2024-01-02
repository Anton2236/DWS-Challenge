package com.dws.challenge.service;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.Transfer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class TransferService {

    private final AccountsService accountsService;
    private final NotificationService notificationService;

    private final Map<String, Lock> accountLocks = new ConcurrentHashMap<>();

    @Autowired
    public TransferService(AccountsService accountsService, NotificationService notificationService) {
        this.accountsService = accountsService;
        this.notificationService = notificationService;
    }

    public void makeTransfer(Transfer transfer) {
        String accountFromId = transfer.getAccountFrom();
        String accountToId = transfer.getAccountTo();

        // Make sure accountFrom and accountTo are different accounts
        if (Objects.equals(accountToId, accountFromId)) {
            throw new IllegalArgumentException("accountFrom and accountTo should be different");
        }

        // Check Transfer amount is positive
        BigDecimal transferAmount = transfer.getAmount();
        if (transferAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("transfer amount should be positive");
        }

        //Lock both account ids
        List<Lock> locks = acquireLocks(accountFromId, accountToId);

        try {
            // Retrieve accounts by their ids
            Account accountFrom = accountsService.getAccount(accountFromId);
            if (accountFrom == null) {
                throw new NoSuchElementException(String.format("account %s not found", accountFromId));
            }
            Account accountTo = accountsService.getAccount(accountToId);
            if (accountTo == null) {
                throw new NoSuchElementException(String.format("account %s not found", accountFromId));
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
        } finally {
            locks.forEach(Lock::unlock);
        }
    }

    private List<Lock> acquireLocks(String... accountIds) {
        List<Lock> locks = new ArrayList<>();
        Lock problemLock = null;
        // need to acquire locks for all the account ids
        while (locks.size() != accountIds.length) {
            for (String accountId : accountIds) {
                Lock lock = accountLocks.computeIfAbsent(accountId, k -> new ReentrantLock());
                if (lock == problemLock) {
                    locks.add(lock);
                    problemLock = null;
                } else if (lock.tryLock()) {
                    locks.add(lock);
                } else {
                    // other thread has acquired this lock, so we can face a deadlock -
                    // unlock all acquired locks and wait until the problem lock is unlocked, then try again
                    locks.forEach(Lock::unlock);
                    locks.clear();
                    if (problemLock != null) {
                        problemLock.unlock();
                    }
                    problemLock = lock;
                    break;
                }
            }
            if (problemLock != null) {
                problemLock.lock();
            }
        }
        return locks;
    }
}
