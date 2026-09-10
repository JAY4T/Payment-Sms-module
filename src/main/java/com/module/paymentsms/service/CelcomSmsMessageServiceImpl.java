package com.module.paymentsms.service;

import com.google.gson.Gson;
import com.module.paymentsms.dao.SmsMessageDao;
import com.module.paymentsms.dto.CelcomSenderDto;
import com.module.paymentsms.dto.PaginationDto;
import com.module.paymentsms.dto.SmsCreationDto;
import com.module.paymentsms.dto.SmsMessageDto;
import com.module.paymentsms.entity.SmsMessage;
import com.module.paymentsms.mapper.SmsMessageDtoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CelcomSmsMessageServiceImpl implements CelcomSmsMessageService {
    private final SmsMessageDao smsMessageDao;
    private final SmsMessageDtoMapper smsMessageDtoMapper;
    private final CelcomSenderService celcomSenderService;

    @Value("${celcom.sms.url}")
    private String celcomSmsUrl;

    @Autowired
    public CelcomSmsMessageServiceImpl(
            SmsMessageDao smsMessageDao,
            SmsMessageDtoMapper smsMessageDtoMapper,
            CelcomSenderService celcomSenderService
    ) {
        this.smsMessageDao = smsMessageDao;
        this.smsMessageDtoMapper = smsMessageDtoMapper;
        this.celcomSenderService = celcomSenderService;
    }

    @Override
    @Transactional
    public SmsMessageDto sendMessage(SmsCreationDto smsCreationDto) {
        LocalDateTime now = LocalDateTime.now();

        // Create SMS message record
        SmsMessage smsMessage = SmsMessage.builder()
                .service(smsCreationDto.getService())
                .provider("CELCOM")
                .sender(smsCreationDto.getSender())
                .recipient(smsCreationDto.getRecipient())
                .message(smsCreationDto.getMessage())
                .status("PENDING")
                .createdAt(now)
                .updatedAt(now)
                .build();

        smsMessageDao.createMessage(smsMessage);

        try {
            // Send SMS via Celcom API
            sendToCelcomApi(smsMessage);
            return smsMessageDtoMapper.toSmsMessageDto(smsMessage);
        } catch (Exception e) {
            log.error("Error sending SMS: {}", e.getMessage(), e);
            smsMessage.setStatus("NOT_DELIVERED");
            smsMessage.setFailureReason(e.getMessage());
            smsMessage.setUpdatedAt(LocalDateTime.now());
            smsMessageDao.createMessage(smsMessage);
            throw new RuntimeException("Failed to send SMS: " + e.getMessage());
        }
    }

    @Override
    public SmsMessageDto getMessageById(Long id) {
        SmsMessage smsMessage = smsMessageDao.getSmsMessageById(id);
        if (smsMessage == null) {
            throw new RuntimeException("SMS message not found with ID: " + id);
        }
        return smsMessageDtoMapper.toSmsMessageDto(smsMessage);
    }

    @Override
    public PaginationDto<SmsMessageDto> getAllSmsMessages(String service, String provider, String sender, String recipient, String status, String celcomMessageId, LocalDateTime createdAtStartDate, LocalDateTime createdAtEndDate, LocalDateTime updatedAtStartDate, LocalDateTime updatedAtEndDate, Integer page, Integer size) {
        try {
            // Create Pageable object
            Pageable pageable = PageRequest.of(page, size);

            // Get paginated SMS messages from DAO
            Page<SmsMessage> smsMessagePage = smsMessageDao.getAllSmsMessages(
                    service, provider, sender, recipient, status, celcomMessageId,
                    createdAtStartDate, createdAtEndDate,
                    updatedAtStartDate, updatedAtEndDate,
                    pageable
            );

            // Convert to DTOs
            List<SmsMessageDto> smsMessageDtos = smsMessagePage.getContent()
                    .stream()
                    .map(smsMessageDtoMapper::toSmsMessageDto)
                    .collect(Collectors.toList());

            // Create pagination response
            return new PaginationDto<>(
                    smsMessageDtos,
                    page,
                    size,
                    smsMessagePage.getTotalElements()
            );

        } catch (Exception e) {
            log.error("Failed to get SMS messages: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to retrieve SMS messages", e);
        }
    }

    private void sendToCelcomApi(SmsMessage smsMessage) throws Exception {
        // Each shortcode (sender) has its own Celcom partner ID + API key, registered via
        // POST /api/v1/admin/celcom-senders - throws if this sender isn't configured.
        CelcomSenderDto celcomSender = celcomSenderService.getActiveCredentialsForShortcode(smsMessage.getSender());

        // Prepare request body
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("partnerID", celcomSender.getPartnerId());
        requestBody.put("shortcode", smsMessage.getSender());
        requestBody.put("message", smsMessage.getMessage());
        requestBody.put("mobile", smsMessage.getRecipient());
        requestBody.put("apikey", celcomSender.getApiKey());
        requestBody.put("pass_type", "plain");

        Gson gson = new Gson();
        String jsonBody = gson.toJson(requestBody);

        log.debug("Sending SMS to Celcom API: {}", requestBody);

        // Create HTTP request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(new URI(celcomSmsUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody))
                .build();

        // Send request
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        log.debug("Celcom API response status: {}, body: {}", response.statusCode(), response.body());

        if (response.statusCode() != HttpStatus.OK.value()) {
            String detail = extractCelcomError(response.body());
            log.error("Celcom rejected SMS. Status: {}, Response: {}", response.statusCode(), response.body());
            smsMessage.setStatus("NOT_DELIVERED");
            smsMessage.setFailureReason(detail);
            smsMessage.setUpdatedAt(LocalDateTime.now());
            throw new Exception(detail);
        }

        // Parse response
        Map<String, Object> responseMap = gson.fromJson(response.body(), Map.class);

        // Check for error response format (unsuccessful request)
        if (responseMap.containsKey("response-code") && !responseMap.containsKey("responses")) {
            Object responseCodeObj = responseMap.get("response-code");
            int responseCode = responseCodeObj instanceof Double ? 
                    ((Double) responseCodeObj).intValue() : 
                    Integer.parseInt(responseCodeObj.toString());
            
            if (responseCode != 200) {
                String errorDescription = responseMap.get("response-description").toString();
                smsMessage.setStatus("NOT_DELIVERED");
                smsMessage.setFailureReason(errorDescription);
                smsMessage.setUpdatedAt(LocalDateTime.now());
                log.error("Celcom API error: {} - {}", responseCode, errorDescription);
                throw new Exception("SMS delivery failed: " + errorDescription);
            }
        }

        // Check for successful response format
        if (responseMap.containsKey("responses")) {
            List<Map<String, Object>> responses = (List<Map<String, Object>>) responseMap.get("responses");
            if (!responses.isEmpty()) {
                Map<String, Object> firstResponse = responses.get(0);
                Object responseCodeObj = firstResponse.get("response-code");
                int responseCode = responseCodeObj instanceof Double ? 
                        ((Double) responseCodeObj).intValue() : 
                        Integer.parseInt(responseCodeObj.toString());

                if (responseCode == 200) {
                    String messageId = firstResponse.get("messageid").toString();
                    smsMessage.setCelcomMessageId(messageId);
                    smsMessage.setStatus("DELIVERED");
                    smsMessage.setUpdatedAt(LocalDateTime.now());
                    log.info("SMS sent successfully. Message ID: {}", messageId);
                } else {
                    String errorDescription = firstResponse.get("response-description").toString();
                    smsMessage.setStatus("NOT_DELIVERED");
                    smsMessage.setFailureReason(errorDescription);
                    smsMessage.setUpdatedAt(LocalDateTime.now());
                    log.error("SMS delivery failed: {} - {}", responseCode, errorDescription);
                    throw new Exception("SMS delivery failed: " + errorDescription);
                }
            } else {
                throw new Exception("Empty response from Celcom API");
            }
        } else {
            throw new Exception("Invalid response format from Celcom API");
        }
    }

    // Celcom's non-2xx errors look like:
    //   {"response-code":1003,"response-description":"Validation Errors...",
    //    "errors":{"shortcode":{"Shortcode":"Sender ID is inactive or unassigned"}}}
    // Pull the human-readable bits out so the caller sees the actual reason, not just a status code.
    @SuppressWarnings("unchecked")
    private String extractCelcomError(String body) {
        try {
            Map<String, Object> parsed = new Gson().fromJson(body, Map.class);
            if (parsed != null) {
                StringBuilder sb = new StringBuilder();
                Object desc = parsed.get("response-description");
                if (desc != null) {
                    sb.append(desc);
                }
                Object errors = parsed.get("errors");
                if (errors instanceof Map) {
                    String flat = flattenErrorValues((Map<String, Object>) errors);
                    if (!flat.isEmpty()) {
                        sb.append(sb.length() > 0 ? " - " : "").append(flat);
                    }
                }
                if (sb.length() > 0) {
                    return sb.toString();
                }
            }
        } catch (Exception ignored) {
            // not JSON, or an unexpected shape - fall back to the raw body
        }
        return "Celcom API returned an error: " + body;
    }

    @SuppressWarnings("unchecked")
    private String flattenErrorValues(Map<String, Object> errors) {
        List<String> parts = new java.util.ArrayList<>();
        for (Object value : errors.values()) {
            if (value instanceof Map) {
                String nested = flattenErrorValues((Map<String, Object>) value);
                if (!nested.isEmpty()) {
                    parts.add(nested);
                }
            } else if (value != null) {
                parts.add(value.toString());
            }
        }
        return String.join("; ", parts);
    }
}
