package com.zwskchat.zwskchat.kb.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateKbRequest {
    @NotBlank
    @Size(max = 128)
    private String name;
    @Size(max = 1000)
    private String description;
    /** PUBLIC / DEPT / ROLE / USER / PRIVATE */
    private String visibility = "PRIVATE";
}
