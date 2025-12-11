package com.module.paymentsms.config;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class BuildResponse {

    public ResponseEntity<Object> success(
            Object data,
            String message,
            Map<String, Object> meta,
            HttpStatus status
    ) {
        Map<String, Object> body = new LinkedHashMap<>(); // Maintains insertion order
        body.put("success", true);
        body.put("message", message);
        body.put("data", data);
        if (meta != null && !meta.isEmpty()) {
            body.put("meta", meta);
        }
        return ResponseEntity.status(status).body(body);
    }

    public ResponseEntity<Object> success(Object data, String message) {
        return success(data, message, null, HttpStatus.OK);
    }

    public ResponseEntity<Object> error(
            String message,
            Map<String, Object> errors,
            HttpStatus status
    ) {
        Map<String, Object> body = new LinkedHashMap<>(); // Maintains insertion order
        body.put("success", false);
        body.put("message", message);
        body.put("errors", errors);
        return ResponseEntity.status(status).body(body);
    }

    public ResponseEntity<Object> error(String message, Map<String, Object> errors) {
        return error(message, errors, HttpStatus.BAD_REQUEST);
    }

}
