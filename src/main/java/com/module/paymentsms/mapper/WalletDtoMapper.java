package com.module.paymentsms.mapper;

import com.module.paymentsms.dto.WalletDto;
import com.module.paymentsms.entity.Wallet;
import org.springframework.stereotype.Component;

@Component
public class WalletDtoMapper {

    public WalletDto toWalletDto(Wallet wallet) {
        return WalletDto.builder()
                .id(wallet.getId())
                .intasendWalletId(wallet.getIntasendWalletId())
                .name(wallet.getName())
                .description(wallet.getDescription())
                .balance(wallet.getBalance() != null ? String.valueOf(wallet.getBalance()) : null)
                .availableBalance(wallet.getAvailableBalance() != null ? String.valueOf(wallet.getAvailableBalance()) : null)
                .isSystemWallet(wallet.getIsSystemWallet())
                .createdAt(wallet.getCreatedAt())
                .updatedAt(wallet.getUpdatedAt())
                .build();
    }

}
