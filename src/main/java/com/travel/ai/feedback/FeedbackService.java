package com.travel.ai.feedback;

import com.travel.ai.config.AppFeedbackProperties;
import com.travel.ai.conversation.ConversationIdValidator;
import com.travel.ai.feedback.dto.FeedbackCreatedResponse;
import com.travel.ai.feedback.dto.FeedbackItemResponse;
import com.travel.ai.feedback.dto.FeedbackListResponse;
import com.travel.ai.feedback.dto.FeedbackSubmitRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FeedbackService {

    private static final int MAX_EVAL_TAGS = 32;
    private static final int MAX_TAG_LEN = 128;
    private static final int MAX_EVAL_CASE_ID_LEN = 256;
    private static final int MAX_REQUEST_ID_LEN = 128;

    private final FeedbackJdbcRepository repository;
    private final AppFeedbackProperties properties;

    public FeedbackService(FeedbackJdbcRepository repository, AppFeedbackProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public FeedbackCreatedResponse submit(FeedbackSubmitRequest req) {
        String user = requireUsername();
        if (req == null) {
            throw new IllegalArgumentException("body required");
        }
        String conversationId = normalizeOptional(req.getConversationId());
        if (conversationId != null && !ConversationIdValidator.isValid(conversationId)) {
            throw new IllegalArgumentException("invalid conversation_id");
        }
        String thumb = normalizeThumb(req.getThumb());
        Integer rating = req.getRating();
        if (rating != null && (rating < 1 || rating > 5)) {
            throw new IllegalArgumentException("rating must be 1..5");
        }
        String comment = normalizeComment(req.getComment());
        String evalCaseId = truncateNullable(req.getEvalCaseId(), MAX_EVAL_CASE_ID_LEN);
        String requestId = truncateNullable(req.getRequestId(), MAX_REQUEST_ID_LEN);
        List<String> evalTags = normalizeEvalTags(req.getEvalTags());

        if (thumb == null && rating == null && (comment == null || comment.isEmpty())) {
            throw new IllegalArgumentException("need thumb, rating, or non-empty comment");
        }

        long id = repository.insert(new FeedbackJdbcRepository.InsertRow(
                user,
                conversationId,
                thumb,
                rating,
                comment,
                evalCaseId,
                evalTags,
                requestId
        ));
        Instant created = repository.findCreatedAtById(id);
        return new FeedbackCreatedResponse(id, created);
    }

    public FeedbackListResponse listMine(int limit, int offset) {
        String user = requireUsername();
        int lim = Math.min(Math.max(limit, 1), properties.getMaxPageSize());
        int off = Math.max(offset, 0);
        List<FeedbackJdbcRepository.LoadedRow> rows = repository.listByUserId(user, lim, off);
        List<FeedbackItemResponse> items = new ArrayList<>(rows.size());
        for (FeedbackJdbcRepository.LoadedRow r : rows) {
            FeedbackItemResponse it = new FeedbackItemResponse();
            it.setId(r.id());
            it.setConversationId(r.conversationId());
            it.setThumb(r.thumb());
            it.setRating(r.rating());
            it.setComment(r.comment());
            it.setEvalCaseId(r.evalCaseId());
            it.setEvalTags(r.evalTags() != null ? r.evalTags() : List.of());
            it.setRequestId(r.requestId());
            it.setCreatedAt(r.createdAt());
            items.add(it);
        }
        return new FeedbackListResponse(items);
    }

    private static String normalizeOptional(String s) {
        if (s == null) {
            return null;
        }
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    private static String normalizeThumb(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String t = raw.trim().toLowerCase(Locale.ROOT);
        if ("up".equals(t) || "down".equals(t)) {
            return t;
        }
        throw new IllegalArgumentException("thumb must be up or down");
    }

    private String normalizeComment(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.strip();
        if (t.isEmpty()) {
            return null;
        }
        int max = properties.getMaxCommentChars();
        if (t.length() > max) {
            return t.substring(0, max);
        }
        return t;
    }

    private static String truncateNullable(String s, int max) {
        String n = normalizeOptional(s);
        if (n == null) {
            return null;
        }
        return n.length() <= max ? n : n.substring(0, max);
    }

    private static List<String> normalizeEvalTags(List<String> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        List<String> out = new ArrayList<>(Math.min(in.size(), MAX_EVAL_TAGS));
        for (String s : in) {
            if (s == null) {
                continue;
            }
            String t = s.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.length() > MAX_TAG_LEN) {
                t = t.substring(0, MAX_TAG_LEN);
            }
            out.add(t);
            if (out.size() >= MAX_EVAL_TAGS) {
                break;
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    private static String requireUsername() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new IllegalStateException("unauthenticated");
        }
        return auth.getName();
    }
}
