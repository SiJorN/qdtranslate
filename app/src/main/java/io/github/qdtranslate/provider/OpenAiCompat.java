package io.github.qdtranslate.provider;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.qdtranslate.core.Http;

/** Любой сервис с OpenAI-совместимым /chat/completions: Gemini, Groq, OpenRouter, Cerebras, Mistral, Z.ai, DeepSeek, Ollama… */
final class OpenAiCompat extends LlmTranslator {
    private final String base, key, model;

    OpenAiCompat(String base, String key, String model, double temperature) {
        super(temperature);
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.key = key;
        this.model = model;
    }

    private Map<String, String> headers() {
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/json");
        if (key != null && !key.isEmpty()) h.put("Authorization", "Bearer " + key);
        if (base.contains("openrouter.ai")) {
            h.put("HTTP-Referer", "https://github.com/qdtranslate");
            h.put("X-Title", "QDTranslate");
        }
        return h;
    }

    @Override
    String chat(String system, String user) throws Exception {
        JSONObject body = new JSONObject()
            .put("model", model)
            .put("temperature", temperature)
            .put("stream", false)
            .put("messages", new JSONArray()
                .put(new JSONObject().put("role", "system").put("content", system))
                .put(new JSONObject().put("role", "user").put("content", user)));
        String resp = Http.request("POST", base + "/chat/completions", headers(), body.toString(), null, 180000);
        JSONObject j = new JSONObject(resp);
        return j.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
    }

    @Override
    public List<String> listModels() throws Exception {
        String resp = Http.request("GET", base + "/models", headers(), null, null, 30000);
        JSONObject j = new JSONObject(resp);
        JSONArray data = j.has("data") ? j.getJSONArray("data") : j.optJSONArray("models");
        List<String> out = new ArrayList<>();
        if (data != null) for (int i = 0; i < data.length(); i++) {
            Object o = data.get(i);
            String id = o instanceof JSONObject ? ((JSONObject) o).optString("id", ((JSONObject) o).optString("name")) : String.valueOf(o);
            if (id.startsWith("models/")) id = id.substring(7); // формат Gemini
            if (!id.isEmpty()) out.add(id);
        }
        Collections.sort(out);
        return out;
    }
}
