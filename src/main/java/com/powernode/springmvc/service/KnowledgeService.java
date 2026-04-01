package com.powernode.springmvc.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface KnowledgeService {
    String uploadDocument(MultipartFile file) throws IOException;
}
