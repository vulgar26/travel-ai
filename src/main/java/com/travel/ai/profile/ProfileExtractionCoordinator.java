package com.travel.ai.profile;

import com.travel.ai.config.AppMemoryProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;

/**
 * 在主线 SSE 完成后异步触发「从对话写画像」：默认写入待确认（Redis），可选自动落库。
 */
@Component
public class ProfileExtractionCoordinator {

    private static final Logger log = LoggerFactory.getLogger(ProfileExtractionCoordinator.class);

    private final AppMemoryProperties appMemoryProperties;
    private final ProfileExtractionService profileExtractionService;
    private final UserProfilePendingExtractionStore pendingExtractionStore;
    private final UserProfileService userProfileService;

    public ProfileExtractionCoordinator(
            AppMemoryProperties appMemoryProperties,
            ProfileExtractionService profileExtractionService,
            UserProfilePendingExtractionStore pendingExtractionStore,
            UserProfileService userProfileService) {
        this.appMemoryProperties = appMemoryProperties;
        this.profileExtractionService = profileExtractionService;
        this.pendingExtractionStore = pendingExtractionStore;
        this.userProfileService = userProfileService;
    }

    /**
     * @param username 须在订阅线程上预先解析（异步路径无 {@code SecurityContext}）
     */
    public void onChatCompleted(String username, String conversationId, String requestId) {
        var ae = appMemoryProperties.getAutoExtract();
        if (!ae.isEnabled() || !ae.isAfterChat()) {
            return;
        }
        if (username == null || username.isBlank()) {
            return;
        }
        Mono.fromRunnable(() -> runAfterChat(username, conversationId, requestId))
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        unused -> {
                        },
                        err -> log.warn("[profile-extract] after_chat_failed conversationId={} requestId={} err={}",
                                conversationId, requestId, err.toString())
                );
    }

    private void runAfterChat(String username, String conversationId, String requestId) {
        ProfileExtractionService.ExtractionResult result = profileExtractionService.extract(username, conversationId, requestId);
        if (result.isEmptySuggestion()) {
            return;
        }
        if (appMemoryProperties.getAutoExtract().isRequireConfirm()) {
            Duration ttl = appMemoryProperties.getAutoExtract().getPendingTtl();
            var payload = new UserProfilePendingExtractionStore.PendingPayload(
                    result.suggestedPatch().deepCopy(),
                    result.mergedPreview().deepCopy(),
                    System.currentTimeMillis()
            );
            pendingExtractionStore.save(username, conversationId, payload, ttl);
            log.info("[profile-extract] pending_saved conversationId={} requestId={} patch_keys={}",
                    conversationId, requestId, result.suggestedPatch().size());
        } else {
            userProfileService.upsertNormalizedForUser(username, result.mergedPreview());
            log.info("[profile-extract] auto_applied conversationId={} requestId={} keys={}",
                    conversationId, requestId, result.mergedPreview().size());
        }
    }
}
