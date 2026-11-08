package io.github.qdtranslate.provider;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.github.qdtranslate.core.Text;

/** Общая логика для чат-моделей: промпты, разбор JSON-ответа, страховка при сбоях формата. */
abstract class LlmTranslator implements Translator {
    protected final double temperature;

    LlmTranslator(double temperature) { this.temperature = temperature; }

    /** Один запрос «system + user → ответ ассистента». */
    abstract String chat(String system, String user) throws Exception;

    @Override
    public List<String> translateStrings(List<String> src, String lang) throws Exception {
        JSONArray in = new JSONArray();
        for (String s : src) in.put(s);
        List<String> out = Text.parseStringArray(chat(Prompts.ui(lang), in.toString()));
        if (out != null && out.size() == src.size()) return out;

        // Модель сбилась с формата — переводим по одной, чтобы не терять пакет целиком
        List<String> res = new ArrayList<>(src.size());
        for (String s : src) {
            try {
                JSONArray one = new JSONArray();
                one.put(s);
                List<String> r = Text.parseStringArray(chat(Prompts.ui(lang), one.toString()));
                res.add(r != null && r.size() == 1 ? r.get(0) : null);
            } catch (Exception e) {
                res.add(null);
            }
        }
        return res;
    }

    @Override
    public String translateProse(String chunk, String lang, Map<String, String> glossary, String context) throws Exception {
        return Text.cleanLlm(chat(Prompts.prose(lang, glossary, context), chunk));
    }
}
