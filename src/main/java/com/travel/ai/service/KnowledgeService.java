package com.travel.ai.service;

import com.travel.ai.dto.KnowledgeUploadResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface KnowledgeService {
    KnowledgeUploadResult uploadDocument(MultipartFile file) throws IOException;
}
