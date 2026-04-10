package com.travel.ai.controller;

import com.travel.ai.service.KnowledgeService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    public KnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file) {
        try {
            return knowledgeService.uploadDocument(file);
        } catch (Exception e) {
            return "上传失败：" + e.getMessage();
        }
    }
}
