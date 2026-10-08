package com.zwskchat.zwskchat.sso;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** SSO 票据交换请求。 */
@Data
public class SsoExchangeRequest {
    @NotBlank
    private String ticket;
}
