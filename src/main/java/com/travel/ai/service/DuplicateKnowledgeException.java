package com.travel.ai.service;

public class DuplicateKnowledgeException extends RuntimeException {

    private final String fileId;
    private final String fileName;

    public DuplicateKnowledgeException(String message, String fileId, String fileName) {
        super(message);
        this.fileId = fileId;
        this.fileName = fileName;
    }

    public String getFileId() {
        return fileId;
    }

    public String getFileName() {
        return fileName;
    }
}
