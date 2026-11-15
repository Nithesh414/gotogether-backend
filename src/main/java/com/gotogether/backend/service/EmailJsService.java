
package com.gotogether.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EmailJsService {

    private final RestClient restClient;

    @Value("${app.emailjs.service-id}")
    private String serviceId;

    @Value("${app.emailjs.template-id}")
    private String templateId;

    @Value("${app.emailjs.public-key}")
    private String publicKey;

    @Value("${app.emailjs.private-key:}")
    private String privateKey;

    public EmailJsService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://api.emailjs.com")
                .build();
    }

    public void sendOtp(String email, String name, String otp) {

        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalStateException(
                    "EmailJS public key is not configured");
        }

        Map<String, Object> templateParams = new LinkedHashMap<>();
        templateParams.put("to_email", email);
        templateParams.put("to_name", name);
        templateParams.put("otp", otp);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("service_id", serviceId);
        payload.put("template_id", templateId);
        payload.put("user_id", publicKey);
        payload.put("template_params", templateParams);

        if (privateKey != null && !privateKey.isBlank()) {
            payload.put("accessToken", privateKey);
        }

        try {
            restClient.post()
                    .uri("/api/v1.0/email/send")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException exception) {
            throw new IllegalStateException(
                    "Unable to send OTP email through EmailJS",
                    exception);
        }
    }
}
