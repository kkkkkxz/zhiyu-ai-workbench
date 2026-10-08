package com.zwskchat.zwskchat.crm.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContactCreateRequest {

    @NotBlank(message = "contact name is required")
    @Size(max = 50, message = "contact name must be at most 50 characters")
    private String name;

    @Size(max = 50, message = "position must be at most 50 characters")
    private String position;

    @Size(max = 30, message = "phone must be at most 30 characters")
    private String phone;

    @Email(message = "email format is invalid")
    @Size(max = 100, message = "email must be at most 100 characters")
    private String email;

    private Integer isPrimary;
}
