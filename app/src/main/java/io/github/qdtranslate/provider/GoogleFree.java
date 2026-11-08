package io.github.qdtranslate.provider;

import org.json.JSONArray;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.github.qdtranslate.core.Http;

/** Бесплатный веб-эндпоинт Google Translate (client=gtx). Без ключа, но неофициальный — может ограничить частые запросы. */
final class GoogleFree implements Translator {

    private String translateOne(String text, String lang) throws Exception {
        String body = "q=" + URLEncoder.encode(text, "UTF-8");
        Map<String, String> h = new HashMap<>();
        h.put("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=zh-CN&tl=" + lang + "&dt=t";
        JSONArray root = new JSONArray(Http.request("POST", url, h, body, null, 30000));
        JSONArray parts = root.getJSONArray(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONArray p = parts.optJSONArray(i);
            if (p != null && !p.isNull(0)) sb.append(p.getString(0));
        }
        return sb.toString();
    }

    @Override
    public List<String> translateStrings(List<String> src, String lang) {
        List<String> out = new ArrayList<>(src.size());
        for (String s : src) {
            try { out.add(translateOne(s, lang)); } catch (Exception e) { out.add(null); }
        }
        return out;
    }

    @Override
    public String translateProse(String chunk, String lang, Map<String, String> glossary, String context) throws Exception {
        // Глоссарий: подставляем перевод имён до отправки, чтобы Google не транслитерировал их по-своему
        if (glossary != null) for (Map.Entry<String, String> e : glossary.entrySet()) chunk = chunk.replace(e.getKey(), e.getValue());
        return translateOne(chunk, lang);
    }

    @Override
    public List<String> listModels() { return Collections.singletonList("(у Google Translate нет выбора модели)"); }
}
