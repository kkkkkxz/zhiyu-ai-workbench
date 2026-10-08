package com.zwskchat.zwskchat.crm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FollowUpCreateRequest {

    @NotBlank(message = "follow-up type is required")
    @Size(max = 20, message = "follow-up type must be at most 20 characters")
    private String type;

    @NotBlank(message = "follow-up content is required")
    private String content;

    @Size(max = 500, message = "next plan must be at most 500 characters")
    private String nextPlan;

    private LocalDateTime nextTime;
}
