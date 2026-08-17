package com.peb.dto.chat;

import com.peb.util.ObjectType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDTO {
    private String botName;
    private String userQuery;
    private String normalizedQuery;
    private String responseText;
    private ObjectType appliedType;
    private String appliedStatus;
    private long totalHits;
    private int page;
    private int totalPages;
    private boolean searchUnavailable;
    private List<ChatResultItemDTO> results;
}
