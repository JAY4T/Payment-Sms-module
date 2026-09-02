package com.module.paymentsms.service;

import com.module.paymentsms.dto.BankDto;

import java.util.List;

public interface BankService {
    List<BankDto> getBankCodes() throws Exception;
}
