package com.ars.health.chatbot.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Service
public class OpenAIService {

    private static final Logger logger = LoggerFactory.getLogger(OpenAIService.class);

    @Value("${openai.api.key}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private static final int MAX_RETRIES = 5;
    private static final long INITIAL_BACKOFF = 1000; // 1 second

    private final ConcurrentHashMap<String, String> cache = new ConcurrentHashMap<>();

    public String getChatbotResponse(String userMessage) {
        if (cache.containsKey(userMessage)) {
            return cache.get(userMessage);
        }

        String url = "https://api.openai.com/v1/chat/completions";

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + apiKey);
        headers.set("Content-Type", "application/json");

        String requestBody = "{"
                + "\"model\": \"gpt-3.5-turbo\","
                + "\"messages\": [{\"role\": \"user\", \"content\": \"" + userMessage + "\"}],"
                + "\"max_tokens\": 150"
                + "}";

        HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

        int attempt = 0;
        while (attempt < MAX_RETRIES) {
            try {
                ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
                String responseBody = response.getBody();
                cache.put(userMessage, responseBody);
                return responseBody;
            } catch (HttpClientErrorException.TooManyRequests e) {
                attempt++;
                long backoff = INITIAL_BACKOFF * (1 << (attempt - 1));
                logger.warn("Too many requests. Attempt {}. Retrying in {} ms.", attempt, backoff);
                try {
                    TimeUnit.MILLISECONDS.sleep(backoff);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Thread was interrupted", interruptedException);
                }
            } catch (Exception e) {
                logger.error("Error while calling OpenAI API", e);
                throw new RuntimeException("Error while calling OpenAI API", e);
            }
        }
        throw new RuntimeException("Exceeded maximum retry attempts for OpenAI API request.");
    }
}
