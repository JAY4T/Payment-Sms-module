package com.module.paymentsms.service;

import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.WalletCreationDto;
import com.module.paymentsms.dto.WalletDto;
import com.module.paymentsms.dto.WalletUpdateDto;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;

public interface IntasendWalletService {
    WalletDto createWallet(WalletCreationDto walletCreationDto);
    WalletDto updateWallet(WalletUpdateDto walletUpdateDto);
    WalletDto getWalletById(Long id);
    WalletDto getWalletByIntasendWalletId(String intasendWalletId);
    PaginationDto<WalletDto> getAllWallets(String name, Boolean isSystemWallet, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Integer page, Integer size);
    WalletDto syncWallet(Long id);

}
