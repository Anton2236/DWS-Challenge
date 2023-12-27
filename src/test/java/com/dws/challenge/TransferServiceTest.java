package com.dws.challenge;

import com.dws.challenge.domain.Account;
import com.dws.challenge.domain.Transfer;
import com.dws.challenge.repository.AccountsRepositoryInMemory;
import com.dws.challenge.service.AccountsService;
import com.dws.challenge.service.NotificationService;
import com.dws.challenge.service.TransferService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ExtendWith(SpringExtension.class)
@SpringBootTest
class TransferServiceTest {

    private final AccountsService accountsService = new AccountsService(new AccountsRepositoryInMemory());
    private final ConcurrentHashMap<String, Integer> notificationCounts = new ConcurrentHashMap<>();
    private final NotificationService notificationService = mockNotificationService(notificationCounts);
    private final TransferService transferService = new TransferService(accountsService, notificationService);

    @BeforeEach
    void cleanUp() {
        // Reset the existing accounts before each test.
        accountsService.getAccountsRepository().clearAccounts();
        notificationCounts.clear();
    }

    @Test
    void makeTransfer() {
        Account account1 = new Account("Id-1", new BigDecimal(1000));
        accountsService.createAccount(account1);

        Account account2 = new Account("Id-2", new BigDecimal(1000));
        accountsService.createAccount(account2);

        Transfer transfer = new Transfer(account1.getAccountId(), account2.getAccountId(), BigDecimal.valueOf(500));

        transferService.makeTransfer(transfer);

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(500));
        assertThat(account2.getBalance()).isEqualTo(BigDecimal.valueOf(1500));

        assertThat(notificationCounts.get(account1.getAccountId())).isEqualTo(1);
        assertThat(notificationCounts.get(account2.getAccountId())).isEqualTo(1);
    }

    @Test
    void makeTransferNotEnoughMoney() {
        Account account1 = new Account("Id-1", new BigDecimal(200));
        accountsService.createAccount(account1);

        Account account2 = new Account("Id-2", new BigDecimal(1000));
        accountsService.createAccount(account2);

        Transfer transfer = new Transfer(account1.getAccountId(), account2.getAccountId(), BigDecimal.valueOf(500));

        assertThrows(IllegalArgumentException.class, () -> transferService.makeTransfer(transfer));

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(200));
        assertThat(account2.getBalance()).isEqualTo(BigDecimal.valueOf(1000));

        assertThat(notificationCounts.getOrDefault(account1.getAccountId(), 0)).isEqualTo(0);
        assertThat(notificationCounts.getOrDefault(account2.getAccountId(), 0)).isEqualTo(0);
    }

    @Test
    void makeTransferNegativeAmount() {
        Account account1 = new Account("Id-1", new BigDecimal(1000));
        accountsService.createAccount(account1);

        Account account2 = new Account("Id-2", new BigDecimal(1000));
        accountsService.createAccount(account2);

        Transfer transfer = new Transfer(account1.getAccountId(), account2.getAccountId(), BigDecimal.valueOf(-500));

        assertThrows(IllegalArgumentException.class, () -> transferService.makeTransfer(transfer));

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(1000));
        assertThat(account2.getBalance()).isEqualTo(BigDecimal.valueOf(1000));

        assertThat(notificationCounts.getOrDefault(account1.getAccountId(), 0)).isEqualTo(0);
        assertThat(notificationCounts.getOrDefault(account2.getAccountId(), 0)).isEqualTo(0);
    }

    @Test
    void makeTransferSameAccount() {
        Account account1 = new Account("Id-1", new BigDecimal(1000));
        accountsService.createAccount(account1);

        Transfer transfer = new Transfer(account1.getAccountId(), account1.getAccountId(), BigDecimal.valueOf(500));

        assertThrows(IllegalArgumentException.class, () -> transferService.makeTransfer(transfer));

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(1000));

        assertThat(notificationCounts.getOrDefault(account1.getAccountId(), 0)).isEqualTo(0);
    }

    @Test
    void makeTransferNotExistingAccount() {
        Account account1 = new Account("Id-1", new BigDecimal(1000));
        accountsService.createAccount(account1);

        String account2Id = "Id-2";

        Transfer transfer1 = new Transfer(account1.getAccountId(), account2Id, BigDecimal.valueOf(500));
        assertThrows(NoSuchElementException.class, () -> transferService.makeTransfer(transfer1));
        Transfer transfer2 = new Transfer(account2Id, account1.getAccountId(), BigDecimal.valueOf(500));
        assertThrows(NoSuchElementException.class, () -> transferService.makeTransfer(transfer2));

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(1000));

        assertThat(notificationCounts.getOrDefault(account1.getAccountId(), 0)).isEqualTo(0);
    }

    @Test
    void makeConcurrentTransfers() throws ExecutionException, InterruptedException {
        Account account1 = new Account("Id-1", new BigDecimal(1000));
        accountsService.createAccount(account1);

        Account account2 = new Account("Id-2", new BigDecimal(1000));
        accountsService.createAccount(account2);

        Account account3 = new Account("Id-3", new BigDecimal(1000));
        accountsService.createAccount(account3);


        List<Transfer> transfers = new ArrayList<>();
        transfers.add(new Transfer(account1.getAccountId(), account2.getAccountId(), BigDecimal.valueOf(500)));
        transfers.add(new Transfer(account2.getAccountId(), account1.getAccountId(), BigDecimal.valueOf(500)));

        transfers.add(new Transfer(account2.getAccountId(), account1.getAccountId(), BigDecimal.valueOf(250)));
        transfers.add(new Transfer(account3.getAccountId(), account1.getAccountId(), BigDecimal.valueOf(250)));
        transfers.add(new Transfer(account2.getAccountId(), account3.getAccountId(), BigDecimal.valueOf(500)));

        try (ExecutorService executor = Executors.newCachedThreadPool()) {
            List<? extends Future<?>> futures = transfers.stream().map(t -> executor.submit(() -> transferService.makeTransfer(t))).toList();
            for (var future : futures) {
                future.get();
            }
        }

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(1500));
        assertThat(account2.getBalance()).isEqualTo(BigDecimal.valueOf(250));
        assertThat(account3.getBalance()).isEqualTo(BigDecimal.valueOf(1250));

        assertThat(notificationCounts.get(account1.getAccountId())).isEqualTo(4);
        assertThat(notificationCounts.get(account2.getAccountId())).isEqualTo(4);
        assertThat(notificationCounts.get(account3.getAccountId())).isEqualTo(2);
    }


    private NotificationService mockNotificationService(Map<String, Integer> notificationCounts) {
        return (account, transferDescription) -> {
            notificationCounts.putIfAbsent(account.getAccountId(), 0);
            notificationCounts.computeIfPresent(account.getAccountId(), (k, v) -> v + 1);
        };
    }
}
