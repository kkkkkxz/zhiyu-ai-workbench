package com.zwskchat.zwskchat.contract.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
public class ContractUpdateRequest {

    @NotBlank(message = "contract number is required")
    @Size(max = 50, message = "contract number must be at most 50 characters")
    private String contractNo;

    @NotBlank(message = "contract title is required")
    @Size(max = 200, message = "contract title must be at most 200 characters")
    private String title;

    private Long customerId;

    @NotBlank(message = "contract type is required")
    @Size(max = 30, message = "contract type must be at most 30 characters")
    private String type;

    @DecimalMin(value = "0.00", message = "amount must be non-negative")
    private BigDecimal amount;

    @Size(max = 10, message = "currency must be at most 10 characters")
    private String currency;

    private LocalDate signDate;
    private LocalDate startDate;
    private LocalDate endDate;

    @Size(max = 20, message = "status must be at most 20 characters")
    private String status;

    private Long ownerId;

    @Size(max = 300, message = "file key must be at most 300 characters")
    private String fileKey;

    private String remark;
}
