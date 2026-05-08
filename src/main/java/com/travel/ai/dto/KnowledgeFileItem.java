package com.travel.ai.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KnowledgeFileItem(
        @JsonProperty("file_id") String fileId,
        @JsonProperty("filename") String filename,
        @JsonProperty("chunk_count") int chunkCount,
        @JsonProperty("created_at") String createdAt,
        @JsonProperty("content_hash") String contentHash,
        boolean legacy,
        boolean deletable
) {
}
