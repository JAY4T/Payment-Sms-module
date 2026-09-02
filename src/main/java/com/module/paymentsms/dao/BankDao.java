package com.module.paymentsms.dao;

import com.module.paymentsms.entity.Bank;

import java.util.List;

public interface BankDao {
    Bank createBank(Bank bank);
    Bank updateBank(Bank bank);
    Bank getBankByIntasendBankCode(String intasendBankCode);
    List<Bank> getAllBanks();
}
