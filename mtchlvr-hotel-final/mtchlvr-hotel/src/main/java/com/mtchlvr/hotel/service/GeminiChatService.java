
package com.mtchlvr.hotel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class GeminiChatService {

    private static final Logger log = LoggerFactory.getLogger(GeminiChatService.class);

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_ATTEMPTS = 3;
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build();

    public String reply(String message) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Gemini chatbot request skipped: GEMINI_API_KEY is not configured.");
            return "The chat assistant is not configured yet. Please set GEMINI_API_KEY in the Codespaces environment and restart the app.";
        }

        try {
            String prompt = """
                You are MatchaLover Hotel's guest support assistant.
                Answer only questions about hotel rooms, room prices, amenities,
                check-in/check-out, booking procedures, reservation cancellation,
                and general hotel policies.
                Do not claim to create, modify, cancel, or confirm a reservation.
                Do not reveal private customer data, credentials, or manager functions.
                If a question is unrelated to the hotel, politely redirect the guest.
                Be friendly, concise, and clear.
                Guest question: %s
                """.formatted(message);

            String requestBody = mapper.writeValueAsString(Map.of(
                    "contents", new Object[]{
                            Map.of("parts", new Object[]{
                                    Map.of("text", prompt)
                            })
                    }
            ));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "?key=" + URLEncoder.encode(apiKey, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(60))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = null;
            for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
                try {
                    response = client.send(request, HttpResponse.BodyHandlers.ofString());
                } catch (java.net.http.HttpTimeoutException ex) {
                    if (attempt == MAX_ATTEMPTS) {
                        log.warn("Gemini API request timed out after {} attempts.", attempt);
                        return "The assistant is taking too long to respond. Please try again in a moment.";
                    }
                    log.warn("Gemini API request timed out (attempt {}/{}); retrying.", attempt, MAX_ATTEMPTS);
                    pauseBeforeRetry(attempt);
                    continue;
                }

                int status = response.statusCode();
                if ((status == 429 || status == 503 || status == 502 || status == 504)
                        && attempt < MAX_ATTEMPTS) {
                    log.warn("Gemini API returned retryable HTTP {} (attempt {}/{}); retrying.",
                            status, attempt, MAX_ATTEMPTS);
                    pauseBeforeRetry(attempt);
                    continue;
                }
                break;
            }

            if (response == null) {
                return "Sorry, the assistant is temporarily unavailable. Please try again.";
            }
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                // Do not retry permanent errors such as invalid requests or API-key failures.
                log.error("Gemini API returned HTTP {}: {}", response.statusCode(), response.body());
                if (response.statusCode() == 429 || response.statusCode() == 503
                        || response.statusCode() == 502 || response.statusCode() == 504) {
                    return "Gemini is busy right now. Please wait a moment and try again.";
                }
                return "Sorry, the assistant is temporarily unavailable. Please try again.";
            }

            JsonNode json = mapper.readTree(response.body());
            JsonNode answer = json.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");

            if (answer.isMissingNode() || answer.asText().isBlank()) {
                return "Sorry, I couldn't generate a reply. Please try again.";
            }

            return answer.asText();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            log.warn("Gemini chatbot request was interrupted.", ex);
            return "The request was interrupted. Please try again.";
        } catch (Exception ex) {
            log.error("Gemini chatbot request failed.", ex);
            return "Sorry, the assistant is temporarily unavailable. Please try again.";
        }
    }

    private void pauseBeforeRetry(int attempt) throws InterruptedException {
        // Short exponential backoff: 1 second, then 2 seconds.
        Thread.sleep(1000L * attempt);
    }
}
