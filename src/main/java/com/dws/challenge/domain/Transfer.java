package com.dws.challenge.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class Transfer {

    @NotNull
    @NotEmpty
    private final String accountFrom;
    @NotNull
    @NotEmpty
    private final String accountTo;

    @NotNull
    @DecimalMin(value = "0", inclusive = false, message = "Transfer amount must be a positive number.")
    private final BigDecimal amount;

    @JsonCreator
    public Transfer(@JsonProperty("accountFrom") String accountFrom,
                    @JsonProperty("accountTo") String accountTo,
                    @JsonProperty("amount") BigDecimal amount) {
        this.accountFrom = accountFrom;
        this.accountTo = accountTo;
        this.amount = amount;
    }

}
