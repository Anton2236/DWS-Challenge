package com.dws.challenge;

import com.dws.challenge.domain.Account;
import com.dws.challenge.service.AccountsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@ExtendWith(SpringExtension.class)
@SpringBootTest
@WebAppConfiguration
class TransferControllerTest {

    private MockMvc mockMvc;

    @Autowired
    private AccountsService accountsService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @BeforeEach
    void prepareMockMvc() {
        this.mockMvc = webAppContextSetup(this.webApplicationContext).build();

        // Reset the existing accounts before each test.
        accountsService.getAccountsRepository().clearAccounts();
    }

    @Test
    void makeTransfer() throws Exception {
        Account account1 = new Account("Id-1", new BigDecimal(1000));
        accountsService.createAccount(account1);
        Account account2 = new Account("Id-2", new BigDecimal(1000));
        accountsService.createAccount(account2);

        this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountFrom\":\"Id-1\",\"accountTo\":\"Id-2\",\"amount\":500}")).andExpect(status().isCreated());

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(500));
        assertThat(account2.getBalance()).isEqualTo(BigDecimal.valueOf(1500));
    }

    @Test
    void makeTransferNotEnoughMoney() throws Exception {
        Account account1 = new Account("Id-1", new BigDecimal(200));
        accountsService.createAccount(account1);

        Account account2 = new Account("Id-2", new BigDecimal(1000));
        accountsService.createAccount(account2);

        this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountFrom\":\"Id-1\",\"accountTo\":\"Id-2\",\"amount\":500}")).andExpect(status().isBadRequest());

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(200));
        assertThat(account2.getBalance()).isEqualTo(BigDecimal.valueOf(1000));
    }

    @Test
    void makeTransferInvalidAmount() throws Exception {
        Account account1 = new Account("Id-1", new BigDecimal(1000));
        accountsService.createAccount(account1);

        Account account2 = new Account("Id-2", new BigDecimal(1000));
        accountsService.createAccount(account2);

        this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountFrom\":\"Id-1\",\"accountTo\":\"Id-2\",\"amount\":-500}")).andExpect(status().isBadRequest());

        this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountFrom\":\"Id-1\",\"accountTo\":\"Id-2\",\"amount\":}")).andExpect(status().isBadRequest());

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(1000));
        assertThat(account2.getBalance()).isEqualTo(BigDecimal.valueOf(1000));
    }

    @Test
    void makeTransferEmptyId() throws Exception {
        Account account1 = new Account("Id-1", new BigDecimal(1000));
        accountsService.createAccount(account1);

        this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountFrom\":\"\",\"accountTo\":\"Id-1\",\"amount\":500}")).andExpect(status().isBadRequest());

        this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountFrom\":\"Id-1\",\"accountTo\":\"\",\"amount\":500}")).andExpect(status().isBadRequest());

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(1000));
    }

    @Test
    void makeTransferNonExistentAccount() throws Exception {
        Account account1 = new Account("Id-1", new BigDecimal(1000));
        accountsService.createAccount(account1);

        this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountFrom\":\"Id-1\",\"accountTo\":\"Id-2\",\"amount\":500}")).andExpect(status().isNotFound());

        this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountFrom\":\"Id-2\",\"accountTo\":\"Id-1\",\"amount\":500}")).andExpect(status().isNotFound());

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(1000));
    }

    @Test
    void makeTransferSameAccount() throws Exception {
        Account account1 = new Account("Id-1", new BigDecimal(1000));
        accountsService.createAccount(account1);

        this.mockMvc.perform(post("/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content("{\"accountFrom\":\"Id-1\",\"accountTo\":\"Id-1\",\"amount\":500}")).andExpect(status().isBadRequest());

        assertThat(account1.getBalance()).isEqualTo(BigDecimal.valueOf(1000));
    }
}
