
package com.mtchlvr.hotel.web;

import com.mtchlvr.hotel.service.GroqChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/chat")
@PreAuthorize("hasRole('CUSTOMER')")
public class ChatController {

    private final GroqChatService chatService;

    public ChatController(GroqChatService chatService) {
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
