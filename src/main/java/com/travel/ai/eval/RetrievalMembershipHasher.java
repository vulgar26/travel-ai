package com.travel.ai.eval;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

/**
 * eval-upgrade.md E7：hashed membership（{@code k_case} 与 per-hit HMAC），与 vagent-eval 判定口径一致。
 */
public final class RetrievalMembershipHasher {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String KEY_PREFIX = "hitid-key/v1|";

    private RetrievalMembershipHasher() {
    }

    /**
     * 与 vagent-eval {@code CitationMembership.canonicalChunkId} 对齐：trim + {@link Locale#ROOT} 小写，
     * 保证 {@code sources[*].id} 与 {@code meta.retrieval_hit_id_hashes[]} 派生口径一致。
     */
    public static String canonicalChunkId(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * {@code k_case = HMAC-SHA256( X_EVAL_TOKEN, "hitid-key/v1|" + targetId + "|" + datasetId + "|" + caseId )}
     */
    public static byte[] deriveKCase(String evalToken, String targetId, String datasetId, String caseId)
            throws GeneralSecurityException {
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(evalToken.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
        String msg = KEY_PREFIX + targetId + "|" + datasetId + "|" + caseId;
        return mac.doFinal(msg.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * {@code hitIdHash = HMAC-SHA256(k_case, hitId)}，输出 64 位小写 hex。
     */
    public static String hitIdHashHex(byte[] kCase, String hitId) throws GeneralSecurityException {
        String canonical = canonicalChunkId(hitId);
        Mac mac = Mac.getInstance(HMAC_SHA256);
        mac.init(new SecretKeySpec(kCase, HMAC_SHA256));
        byte[] raw = mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8));
        return toLowerHex(raw);
    }

    /**
     * 对多个 hitId 计算 hash，排序后返回（eval 允许任意顺序；排序便于回归稳定）。
     */
    public static List<String> sortedHitIdHashes(byte[] kCase, Iterable<String> hitIds) throws GeneralSecurityException {
        TreeSet<String> sorted = new TreeSet<>();
        for (String id : hitIds) {
            if (id == null || id.isBlank()) {
                continue;
            }
            sorted.add(hitIdHashHex(kCase, id));
        }
        return new ArrayList<>(sorted);
    }

    public static List<String> sortedHitIdHashes(
            String evalToken,
            String targetId,
            String datasetId,
            String caseId,
            Iterable<String> hitIds
    ) {
        try {
            byte[] kCase = deriveKCase(evalToken, targetId, datasetId, caseId);
            return sortedHitIdHashes(kCase, hitIds);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 not available", e);
        }
    }

    public static String toLowerHex(byte[] raw) {
        char[] hex = "0123456789abcdef".toCharArray();
        char[] out = new char[raw.length * 2];
        for (int i = 0; i < raw.length; i++) {
            int v = raw[i] & 0xff;
            out[i * 2] = hex[v >>> 4];
            out[i * 2 + 1] = hex[v & 0x0f];
        }
        return new String(out);
    }

    public static boolean isValidMembershipHashHex(String s) {
        if (s == null || s.length() != 64) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c < '0' || c > '9') && (c < 'a' || c > 'f')) {
                return false;
            }
        }
        return true;
    }
}
