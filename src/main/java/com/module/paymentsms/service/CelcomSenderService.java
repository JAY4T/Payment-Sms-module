package com.module.paymentsms.service;

import com.module.paymentsms.dto.CelcomSenderCreationDto;
import com.module.paymentsms.dto.CelcomSenderDto;
import com.module.paymentsms.dto.CelcomSenderUpdateDto;

import java.util.List;

public interface CelcomSenderService {
    CelcomSenderDto createCelcomSender(CelcomSenderCreationDto celcomSenderCreationDto);
    CelcomSenderDto updateCelcomSender(CelcomSenderUpdateDto celcomSenderUpdateDto);
    CelcomSenderDto revokeCelcomSender(Long id);
    CelcomSenderDto activateCelcomSender(Long id);
    List<CelcomSenderDto> getAllCelcomSenders();

    // Used by CelcomSmsMessageServiceImpl to resolve which credentials to send a given
    // message's "sender" shortcode with. Throws if none are configured or the match is inactive.
    CelcomSenderDto getActiveCredentialsForShortcode(String shortcode);
}
