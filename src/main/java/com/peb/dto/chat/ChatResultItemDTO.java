package com.peb.dto.chat;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResultItemDTO {
    private String objectId;
    private String name;
    private String objectType;
    private String objectTypeDisplayName;
    private String revision;
    private String releaseStatus;
    private String detailUrl;
}
