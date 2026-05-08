package com.travel.ai.controller;

import com.travel.ai.dto.KnowledgeListResponse;
import com.travel.ai.service.KnowledgeFileNotFoundException;
import com.travel.ai.service.KnowledgeService;
import com.travel.ai.service.LegacyKnowledgeNotDeletableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/travel/knowledge")
public class TravelKnowledgeController {

    private final KnowledgeService knowledgeService;

    public TravelKnowledgeController(KnowledgeService knowledgeService) {
        this.knowledgeService = knowledgeService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public KnowledgeListResponse listMine() {
        return knowledgeService.listMine();
    }

    @DeleteMapping("/{fileId}")
    public ResponseEntity<?> deleteMine(@PathVariable String fileId) {
        try {
            knowledgeService.deleteMine(fileId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException ex) {
            return json(HttpStatus.BAD_REQUEST, "INVALID_KNOWLEDGE_FILE_ID",
                    ex.getMessage() != null ? ex.getMessage() : "invalid fileId");
        } catch (LegacyKnowledgeNotDeletableException ex) {
            return json(HttpStatus.CONFLICT, "LEGACY_KNOWLEDGE_NOT_DELETABLE",
                    ex.getMessage() != null ? ex.getMessage() : "legacy knowledge is not deletable");
        } catch (KnowledgeFileNotFoundException ex) {
            return json(HttpStatus.NOT_FOUND, "KNOWLEDGE_FILE_NOT_FOUND",
                    ex.getMessage() != null ? ex.getMessage() : "knowledge file not found");
        }
    }

    private static ResponseEntity<Map<String, String>> json(HttpStatus status, String code, String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("error", code);
        m.put("message", message);
        return ResponseEntity.status(status).body(m);
    }
}
