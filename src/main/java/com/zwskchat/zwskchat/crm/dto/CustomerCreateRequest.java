package com.zwskchat.zwskchat.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CustomerCreateRequest {

    @NotBlank(message = "customer name is required")
    @Size(max = 200, message = "customer name must be at most 200 characters")
    private String name;

    @Size(max = 100, message = "short name must be at most 100 characters")
    private String shortName;

    @Size(max = 20, message = "type must be at most 20 characters")
    private String type;

    @Size(max = 50, message = "industry must be at most 50 characters")
    private String industry;

    @Size(max = 20, message = "scale must be at most 20 characters")
    private String scale;

    @Size(max = 50, message = "source must be at most 50 characters")
    private String source;

    private Long ownerId;

    @Size(max = 20, message = "status must be at most 20 characters")
    private String status;

    @Size(max = 20, message = "grade must be at most 20 characters")
    private String grade;

    private Integer aiScore;

    @Size(max = 50, message = "lifecycle stage must be at most 50 characters")
    private String lifecycleStage;

    @Size(max = 500, message = "AI next action must be at most 500 characters")
    private String aiNextAction;

    private BigDecimal aiValuePredict;

    @Size(max = 30, message = "phone must be at most 30 characters")
    private String phone;

    @Email(message = "email format is invalid")
    @Size(max = 100, message = "email must be at most 100 characters")
    private String email;

    @Size(max = 300, message = "address must be at most 300 characters")
    private String address;

    private String remark;
}
