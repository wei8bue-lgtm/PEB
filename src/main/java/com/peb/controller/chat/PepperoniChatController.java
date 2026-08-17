package com.peb.controller.chat;

import com.peb.dto.chat.ChatResponseDTO;
import com.peb.service.chat.PepperoniChatService;
import com.peb.util.ObjectType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class PepperoniChatController {

    private final PepperoniChatService pepperoniChatService;

    @GetMapping("/query")
    @PreAuthorize("hasAuthority('READ')")
    public ChatResponseDTO query(
            @RequestParam("q") String userQuery,
            @RequestParam(value = "type", required = false) ObjectType objectType,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page) {

        return pepperoniChatService.query(userQuery, objectType, status, page);
    }
}
