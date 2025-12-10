package com.module.paymentsms.controller;

import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.WalletCreationDto;
import com.module.paymentsms.dto.WalletDto;
import com.module.paymentsms.dto.WalletUpdateDto;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

public interface IntasendWalletController {
    ResponseEntity<Object> createWallet(WalletCreationDto walletCreationDto);
    ResponseEntity<Object> updateWallet(WalletUpdateDto walletUpdateDto);
    ResponseEntity<Object> getWalletById(Long id);
    ResponseEntity<Object> getAllWallets(String name, Boolean isSystemWallet, LocalDate createdAtStartDate, LocalDate createdAtEndDate, LocalDate updatedAtStartDate, LocalDate updatedAtEndDate, Integer page, Integer size);
}
