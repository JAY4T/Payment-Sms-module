package com.module.paymentsms.controller;

import com.module.paymentsms.config.BuildResponse;
import com.module.paymentsms.dto.BankDto;
import com.module.paymentsms.service.BankService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/intasend/banks")
@Slf4j
public class BankControllerImpl implements BankController {

    private final BuildResponse buildResponse;
    private final BankService bankService;

    @Autowired
    public BankControllerImpl(
            BuildResponse buildResponse,
            BankService bankService
    ) {
        this.buildResponse = buildResponse;
        this.bankService = bankService;
    }

    @Override
    @GetMapping
    public ResponseEntity<Object> getBankCodes() {
        try {
            List<BankDto> banks = bankService.getBankCodes();
            return buildResponse.success(banks, "Bank codes retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving bank codes: {}", e.getMessage(), e);
            return buildResponse.error("Failed to retrieve bank codes: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
