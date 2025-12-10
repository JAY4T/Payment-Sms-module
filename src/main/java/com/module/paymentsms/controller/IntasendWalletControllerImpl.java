package com.module.paymentsms.controller;

import com.module.paymentsms.config.BuildResponse;
import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.WalletCreationDto;
import com.module.paymentsms.dto.WalletDto;
import com.module.paymentsms.dto.WalletUpdateDto;
import com.module.paymentsms.service.IntasendWalletService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/intasend/wallet")
@Slf4j
public class IntasendWalletControllerImpl implements IntasendWalletController{

    private final IntasendWalletService intasendWalletService;
    private final BuildResponse buildResponse;

    @Autowired
    public IntasendWalletControllerImpl(
            IntasendWalletService intasendWalletService,
            BuildResponse buildResponse
    ) {
        this.intasendWalletService = intasendWalletService;
        this.buildResponse = buildResponse;
    }

    @Override
    @PostMapping
    public ResponseEntity<Object> createWallet(@RequestBody WalletCreationDto walletCreationDto) {
        try {
            log.info("Creating wallet: {}", walletCreationDto);
            WalletDto wallet = intasendWalletService.createWallet(walletCreationDto);
            return buildResponse.success(wallet, "Wallet created successfully", null, HttpStatus.CREATED);
        } catch (Exception e) {
            log.error("Error creating wallet: {}", e.getMessage(), e);
            return buildResponse.error("Failed to create wallet: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @PutMapping
    public ResponseEntity<Object> updateWallet(@RequestBody WalletUpdateDto walletUpdateDto) {
        try {
            log.info("Updating wallet ID {}: {}", walletUpdateDto.getId(), walletUpdateDto);
            WalletDto wallet = intasendWalletService.updateWallet(walletUpdateDto);
            return buildResponse.success(wallet, "Wallet updated successfully");
        } catch (Exception e) {
            log.error("Error updating wallet: {}", e.getMessage(), e);
            return buildResponse.error("Failed to update wallet: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<Object> getWalletById(@PathVariable Long id) {
        try {
            log.info("Retrieving wallet by ID: {}", id);
            WalletDto wallet = intasendWalletService.getWalletById(id);
            return buildResponse.success(wallet, "Wallet retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving wallet by ID {}: {}", id, e.getMessage(), e);
            return buildResponse.error("Failed to retrieve wallet: " + e.getMessage(), null, HttpStatus.NOT_FOUND);
        }
    }

    @Override
    @GetMapping
    public ResponseEntity<Object> getAllWallets(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) Boolean isSystemWallet,
            @RequestParam(required = false) LocalDate createdAtStartDate,
            @RequestParam(required = false) LocalDate createdAtEndDate,
            @RequestParam(required = false) LocalDate updatedAtStartDate,
            @RequestParam(required = false) LocalDate updatedAtEndDate,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size
    ) {
        try {
            log.info("Retrieving wallets with filters - name: {}, isSystemWallet: {}, page: {}, size: {}", 
                    name, isSystemWallet, page, size);
            
            // Convert LocalDate to LocalDateTime (start of day and end of day)
            LocalDateTime createdAtStart = createdAtStartDate != null ? createdAtStartDate.atStartOfDay() : null;
            LocalDateTime createdAtEnd = createdAtEndDate != null ? createdAtEndDate.atTime(23, 59, 59) : null;
            LocalDateTime updatedAtStart = updatedAtStartDate != null ? updatedAtStartDate.atStartOfDay() : null;
            LocalDateTime updatedAtEnd = updatedAtEndDate != null ? updatedAtEndDate.atTime(23, 59, 59) : null;
            
            PaginationDto<WalletDto> wallets = intasendWalletService.getAllWallets(
                    name, isSystemWallet, 
                    createdAtStart, createdAtEnd, 
                    updatedAtStart, updatedAtEnd, 
                    page, size
            );
            return buildResponse.success(wallets, "Wallets retrieved successfully");
        } catch (Exception e) {
            log.error("Error retrieving wallets: {}", e.getMessage(), e);
            return buildResponse.error("Failed to retrieve wallets: " + e.getMessage(), null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
