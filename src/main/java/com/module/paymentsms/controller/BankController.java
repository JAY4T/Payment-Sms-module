package com.module.paymentsms.controller;

import org.springframework.http.ResponseEntity;

public interface BankController {
    ResponseEntity<Object> getBankCodes();
}
