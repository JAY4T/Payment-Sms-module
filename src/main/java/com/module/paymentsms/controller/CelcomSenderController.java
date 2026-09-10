package com.module.paymentsms.controller;

import com.module.paymentsms.dto.CelcomSenderCreationDto;
import com.module.paymentsms.dto.CelcomSenderUpdateDto;
import org.springframework.http.ResponseEntity;

public interface CelcomSenderController {
    ResponseEntity<Object> createCelcomSender(CelcomSenderCreationDto celcomSenderCreationDto);
    ResponseEntity<Object> getAllCelcomSenders();
    ResponseEntity<Object> updateCelcomSender(Long id, CelcomSenderUpdateDto celcomSenderUpdateDto);
    ResponseEntity<Object> revokeCelcomSender(Long id);
    ResponseEntity<Object> activateCelcomSender(Long id);
}
