package com.module.paymentsms.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@AllArgsConstructor
@NoArgsConstructor
@SuperBuilder
public class CelcomSenderCreationDto {
    private String shortcode;

    // Optional - Celcom accounts share one partner ID across shortcodes in practice, so this
    // defaults to the existing "954" when omitted.
    private String partnerId;

    private String apiKey;
}
