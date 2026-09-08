package com.module.paymentsms.service;

import com.module.paymentsms.dao.CelcomSenderDao;
import com.module.paymentsms.dto.CelcomSenderCreationDto;
import com.module.paymentsms.dto.CelcomSenderDto;
import com.module.paymentsms.dto.CelcomSenderUpdateDto;
import com.module.paymentsms.entity.CelcomSender;
import com.module.paymentsms.mapper.CelcomSenderDtoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CelcomSenderServiceImpl implements CelcomSenderService {

    // Every Celcom shortcode registered so far shares this partner ID - kept as the default so
    // callers only need to know it if Celcom ever assigns a different one to a new shortcode.
    private static final String DEFAULT_PARTNER_ID = "954";

    private final CelcomSenderDao celcomSenderDao;
    private final CelcomSenderDtoMapper celcomSenderDtoMapper;

    @Autowired
    public CelcomSenderServiceImpl(
            CelcomSenderDao celcomSenderDao,
            CelcomSenderDtoMapper celcomSenderDtoMapper
    ) {
        this.celcomSenderDao = celcomSenderDao;
        this.celcomSenderDtoMapper = celcomSenderDtoMapper;
    }

    @Override
    @Transactional
    public CelcomSenderDto createCelcomSender(CelcomSenderCreationDto celcomSenderCreationDto) {
        if (celcomSenderDao.getCelcomSenderByShortcode(celcomSenderCreationDto.getShortcode()) != null) {
            throw new RuntimeException("A Celcom sender is already registered for shortcode: " + celcomSenderCreationDto.getShortcode());
        }

        LocalDateTime now = LocalDateTime.now();

        CelcomSender celcomSender = CelcomSender.builder()
                .shortcode(celcomSenderCreationDto.getShortcode())
                .partnerId(celcomSenderCreationDto.getPartnerId() != null
                        ? celcomSenderCreationDto.getPartnerId()
                        : DEFAULT_PARTNER_ID)
                .apiKey(celcomSenderCreationDto.getApiKey())
                .active(true)
                .createdAt(now)
                .updatedAt(now)
                .build();

        celcomSenderDao.createCelcomSender(celcomSender);

        log.info("Registered Celcom sender '{}' (id={})", celcomSender.getShortcode(), celcomSender.getId());

        return celcomSenderDtoMapper.toCelcomSenderDto(celcomSender);
    }

    @Override
    @Transactional
    public CelcomSenderDto updateCelcomSender(CelcomSenderUpdateDto celcomSenderUpdateDto) {
        CelcomSender celcomSender = celcomSenderDao.getCelcomSenderById(celcomSenderUpdateDto.getId());
        if (celcomSender == null) {
            throw new RuntimeException("Celcom sender not found with ID: " + celcomSenderUpdateDto.getId());
        }

        if (celcomSenderUpdateDto.getApiKey() != null) {
            celcomSender.setApiKey(celcomSenderUpdateDto.getApiKey());
        }
        if (celcomSenderUpdateDto.getPartnerId() != null) {
            celcomSender.setPartnerId(celcomSenderUpdateDto.getPartnerId());
        }
        celcomSender.setUpdatedAt(LocalDateTime.now());

        log.info("Updated Celcom sender '{}' (id={})", celcomSender.getShortcode(), celcomSender.getId());

        return celcomSenderDtoMapper.toCelcomSenderDto(celcomSenderDao.updateCelcomSender(celcomSender));
    }

    @Override
    @Transactional
    public CelcomSenderDto revokeCelcomSender(Long id) {
        CelcomSender celcomSender = celcomSenderDao.getCelcomSenderById(id);
        if (celcomSender == null) {
            throw new RuntimeException("Celcom sender not found with ID: " + id);
        }

        celcomSender.setActive(false);
        celcomSender.setUpdatedAt(LocalDateTime.now());
        celcomSenderDao.updateCelcomSender(celcomSender);

        log.info("Revoked Celcom sender '{}' (id={})", celcomSender.getShortcode(), id);

        return celcomSenderDtoMapper.toCelcomSenderDto(celcomSender);
    }

    @Override
    public List<CelcomSenderDto> getAllCelcomSenders() {
        return celcomSenderDao.getAllCelcomSenders().stream()
                .map(celcomSenderDtoMapper::toCelcomSenderDto)
                .collect(Collectors.toList());
    }

    @Override
    public CelcomSenderDto getActiveCredentialsForShortcode(String shortcode) {
        CelcomSender celcomSender = celcomSenderDao.getCelcomSenderByShortcode(shortcode);
        if (celcomSender == null || !Boolean.TRUE.equals(celcomSender.getActive())) {
            throw new RuntimeException(
                    "No active Celcom credentials configured for sender '" + shortcode
                            + "'. Register one via POST /api/v1/admin/celcom-senders.");
        }
        return celcomSenderDtoMapper.toCelcomSenderDto(celcomSender);
    }
}
