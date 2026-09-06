package com.module.paymentsms.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.module.paymentsms.dao.BankDao;
import com.module.paymentsms.dto.BankDto;
import com.module.paymentsms.entity.Bank;
import com.module.paymentsms.mapper.BankDtoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class BankServiceImpl implements BankService {

    private final BankDao bankDao;
    private final BankDtoMapper bankDtoMapper;

    @Value("${intasend.bank.codes.url}")
    private String bankCodesUrl;

    @Autowired
    public BankServiceImpl(BankDao bankDao, BankDtoMapper bankDtoMapper) {
        this.bankDao = bankDao;
        this.bankDtoMapper = bankDtoMapper;
    }

    @Override
    @Transactional
    public List<BankDto> getBankCodes() throws Exception {
        // No Authorization header - Intasend's bank-codes lookup is public reference data.
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(bankCodesUrl))
                .header("Accept", "application/json")
                .GET()
                .build();

        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != HttpStatus.OK.value()) {
            log.error("Failed to fetch bank codes from Intasend. Status: {}, Response: {}", response.statusCode(), response.body());
            throw new RuntimeException("Failed to fetch bank codes: Status " + response.statusCode());
        }

        Gson gson = new Gson();
        List<Map<String, Object>> intasendBanks = gson.fromJson(response.body(), new TypeToken<List<Map<String, Object>>>() {}.getType());

        // Upsert into the local banks table (matched by intasend_bank_code) so this data is
        // also available for local lookups, not just as a live pass-through.
        List<Bank> banks = new ArrayList<>();
        for (Map<String, Object> intasendBank : intasendBanks) {
            String bankCode = intasendBank.get("bank_code").toString();
            String bankName = intasendBank.get("bank_name").toString();

            Bank existing = bankDao.getBankByIntasendBankCode(bankCode);
            if (existing != null) {
                existing.setBankName(bankName);
                banks.add(bankDao.updateBank(existing));
            } else {
                Bank bank = Bank.builder()
                        .bankName(bankName)
                        .intasendBankCode(bankCode)
                        .build();
                banks.add(bankDao.createBank(bank));
            }
        }

        log.info("Synced {} bank code(s) from Intasend", banks.size());

        return banks.stream().map(bankDtoMapper::toBankDto).collect(Collectors.toList());
    }
}
