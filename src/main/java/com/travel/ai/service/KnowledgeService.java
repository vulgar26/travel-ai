package com.travel.ai.service;

import com.travel.ai.dto.KnowledgeUploadResult;
import com.travel.ai.dto.KnowledgeListResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface KnowledgeService {
    KnowledgeUploadResult uploadDocument(MultipartFile file) throws IOException;

    KnowledgeListResponse listMine();

    void deleteMine(String fileId);
}
