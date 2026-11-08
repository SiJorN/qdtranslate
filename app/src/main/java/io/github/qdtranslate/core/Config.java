package io.github.qdtranslate.core;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Настройки хранятся в SharedPreferences самого Qidian (мод работает внутри его процесса),
 * поэтому не нужна межпроцессная передача настроек и всё одинаково работает в LSPosed и LSPatch.
 */
public final class Config {
    private final SharedPreferences p;

    public Config(Context ctx) {
        p = ctx.getSharedPreferences("qdtranslate_cfg", Context.MODE_PRIVATE);
    }

    public SharedPreferences prefs() { return p; }

    // --- общие переключатели ---
    public boolean enabled()        { return p.getBoolean("enabled", true); }
    public boolean uiEnabled()      { return p.getBoolean("ui_enabled", true); }
    public boolean contentEnabled() { return p.getBoolean("content_enabled", true); }
    public boolean discovery()      { return p.getBoolean("discovery", false); }

    // --- выбор провайдеров ---
    public String uiProvider()    { return p.getString("ui_provider", "google_free"); }
    public String novelProvider() { return p.getString("novel_provider", "gemini"); }

    // --- параметры конкретного провайдера (храним раздельно, чтобы ключи не терялись) ---
    public String key(String id)   { return p.getString("key_" + id, ""); }
    public String model(String id, String def) {
        String m = p.getString("model_" + id, "");
        return m.isEmpty() ? def : m;
    }
    public String url(String id, String def) {
        String u = p.getString("url_" + id, "");
        return u.isEmpty() ? def : u;
    }

    // --- перевод ---
    public String targetLang() { return p.getString("target_lang", "ru"); }
    public double temperature() {
        try { return Double.parseDouble(p.getString("temperature", "0.3")); } catch (Exception e) { return 0.3; }
    }
    public int chunkChars() { return intOf("chunk_chars", 1500); }
    public int waitSec()    { return intOf("wait_sec", 60); }
    public String glossaryRaw() { return p.getString("glossary", ""); }
    public String contentHooks() { return p.getString("content_hooks", ""); }
    public String gigachatPem()  { return p.getString("gigachat_pem", ""); }
    public String gigachatScope() { return p.getString("gigachat_scope", "GIGACHAT_API_PERS"); }

    /** Глоссарий: строки вида «原文=перевод». */
    public Map<String, String> glossary() {
        Map<String, String> m = new LinkedHashMap<>();
        for (String line : glossaryRaw().split("\n")) {
            int i = line.indexOf('=');
            if (i <= 0) continue;
            String k = line.substring(0, i).trim(), v = line.substring(i + 1).trim();
            if (!k.isEmpty() && !v.isEmpty()) m.put(k, v);
        }
        return m;
    }

    private int intOf(String k, int def) {
        try { return Integer.parseInt(p.getString(k, String.valueOf(def)).trim()); } catch (Exception e) { return def; }
    }
}
