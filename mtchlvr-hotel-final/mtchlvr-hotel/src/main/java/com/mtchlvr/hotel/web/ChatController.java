
package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.service.GeminiChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final GeminiChatService chatService;

    public ChatController(GeminiChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ChatResponse chat(
            @Valid @RequestBody ChatRequest request,
            Authentication authentication) {

        return new ChatResponse(chatService.reply(request.message().trim()));
    }

    public record ChatRequest(
            @NotBlank @Size(max = 1000) String message) {}

    public record ChatResponse(String reply) {}
}
