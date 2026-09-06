package com.module.paymentsms.mapper;

import com.module.paymentsms.dto.BankDto;
import com.module.paymentsms.entity.Bank;
import org.springframework.stereotype.Component;

@Component
public class BankDtoMapper {

    public BankDto toBankDto(Bank bank) {
        return BankDto.builder()
                .id(bank.getId())
                .bankName(bank.getBankName())
                .bankCode(bank.getIntasendBankCode())
                .build();
    }
}
