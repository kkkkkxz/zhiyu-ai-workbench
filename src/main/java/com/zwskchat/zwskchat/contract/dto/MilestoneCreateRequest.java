package com.zwskchat.zwskchat.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class MilestoneCreateRequest {

    @NotBlank(message = "milestone name is required")
    @Size(max = 100, message = "milestone name must be at most 100 characters")
    private String name;

    @DecimalMin(value = "0.00", message = "amount must be non-negative")
    private BigDecimal amount;

    private LocalDate dueDate;

    @Size(max = 20, message = "status must be at most 20 characters")
    private String status;
}
