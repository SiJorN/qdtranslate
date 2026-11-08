package io.github.qdtranslate.core;

import org.json.JSONArray;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class Text {
    private Text() {}

    private static final Pattern THINK = Pattern.compile("(?s)<think>.*?</think>");

    public static boolean isCjk(char c) {
        return (c >= 0x4E00 && c <= 0x9FFF)   // CJK Unified Ideographs
            || (c >= 0x3400 && c <= 0x4DBF)   // Extension A
            || (c >= 0xF900 && c <= 0xFAFF);  // Compatibility Ideographs
    }

    public static boolean hasCjk(CharSequence s) {
        if (s == null) return false;
        for (int i = 0, n = s.length(); i < n; i++) if (isCjk(s.charAt(i))) return true;
        return false;
    }

    public static int cjkCount(CharSequence s) {
        if (s == null) return 0;
        int k = 0;
        for (int i = 0, n = s.length(); i < n; i++) if (isCjk(s.charAt(i))) k++;
        return k;
    }

    /** Похоже ли на текст главы (длинный китайский текст). */
    public static boolean looksLikeProse(CharSequence s) {
        return s != null && s.length() >= 200 && cjkCount(s) >= 100;
    }

    public static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    /** Убирает рассуждения reasoning-моделей и markdown-обёртки. */
    public static String cleanLlm(String s) {
        if (s == null) return "";
        s = THINK.matcher(s).replaceAll("");
        s = s.trim();
        if (s.startsWith("```")) {
            int nl = s.indexOf('\n');
            if (nl > 0) s = s.substring(nl + 1);
            if (s.endsWith("```")) s = s.substring(0, s.length() - 3);
        }
        return s.trim();
    }

    /** Достаёт JSON-массив строк из ответа модели. null — если не удалось. */
    public static List<String> parseStringArray(String s) {
        s = cleanLlm(s);
        int a = s.indexOf('['), b = s.lastIndexOf(']');
        if (a < 0 || b <= a) return null;
        try {
            JSONArray arr = new JSONArray(s.substring(a, b + 1));
            List<String> out = new ArrayList<>(arr.length());
            for (int i = 0; i < arr.length(); i++) out.add(arr.isNull(i) ? null : String.valueOf(arr.get(i)));
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    public static String targetName(String code) {
        switch (code == null ? "ru" : code) {
            case "en": return "English";
            case "uk": return "Ukrainian";
            case "be": return "Belarusian";
            case "kk": return "Kazakh";
            default: return "Russian";
        }
    }
}
