
package com.mtchlvr.hotel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

@Service
public class GeminiChatService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public String reply(String message) {
        if (apiKey == null || apiKey.isBlank()) {
            return "The chat assistant is not configured yet. Please contact the hotel.";
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
                    .uri(URI.create(apiUrl + "?key=" + apiKey))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();

            HttpResponse<String> response = client.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() < 200 || response.statusCode() >= 300) {
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
            return "The request was interrupted. Please try again.";
        } catch (Exception ex) {
            return "Sorry, the assistant is temporarily unavailable. Please try again.";
        }
    }
}
