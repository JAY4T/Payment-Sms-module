package com.module.paymentsms.dao;

import com.module.paymentsms.entity.CelcomSender;

import java.util.List;

public interface CelcomSenderDao {
    CelcomSender createCelcomSender(CelcomSender celcomSender);
    CelcomSender updateCelcomSender(CelcomSender celcomSender);
    CelcomSender getCelcomSenderById(Long id);
    CelcomSender getCelcomSenderByShortcode(String shortcode);
    List<CelcomSender> getAllCelcomSenders();
}
