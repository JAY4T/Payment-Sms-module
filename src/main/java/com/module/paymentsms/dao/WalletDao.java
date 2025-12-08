package com.module.paymentsms.dao;

import com.module.paymentsms.entity.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface WalletDao {
    Wallet createWallet(Wallet wallet);
    Wallet updateWallet(Wallet wallet);
    Wallet getWalletById(Long id);
    Wallet getWalletByIntasendWalletId(String intasendWalletId);
    Page<Wallet> getAllWallets(String name, Boolean isSystemWallet, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Pageable pageable);
}
