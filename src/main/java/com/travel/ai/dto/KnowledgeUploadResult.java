package com.travel.ai.dto;

/**
 * 知识库上传成功结果（供 {@link com.travel.ai.controller.KnowledgeController} 序列化为 JSON）。
 */
public record KnowledgeUploadResult(String fileName, int chunkCount) {
}
