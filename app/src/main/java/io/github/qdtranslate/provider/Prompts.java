package io.github.qdtranslate.provider;

import java.util.Map;

import io.github.qdtranslate.core.Text;

final class Prompts {
    private Prompts() {}

    static String ui(String lang) {
        String name = Text.targetName(lang);
        return "You translate user-interface strings of the Chinese web-novel app Qidian (起点读书) into " + name + ".\n"
            + "Input: a JSON array of strings. Output: ONLY a JSON array of the same length and order containing the translations.\n"
            + "Rules: keep translations short like UI labels (buttons, tabs, menu items); keep numbers, placeholders (%s, %d, {0}), emoji and symbols; "
            + "convert 《》 to «»; translate book titles and genre names naturally; never add comments or explanations.";
    }

    static String prose(String lang, Map<String, String> glossary, String context) {
        String name = Text.targetName(lang);
        StringBuilder sb = new StringBuilder();
        sb.append("You are a professional literary translator of Chinese web novels (xianxia, xuanhuan, wuxia, urban, sci-fi) into ").append(name).append(".\n")
          .append("Translate the user's text faithfully and fluently, keeping the tone, style and every detail.\n")
          .append("Keep paragraph structure: exactly one output line per input line, in the same order.\n");
        if ("ru".equals(lang)) {
            sb.append("Transliterate Chinese personal and place names using the Palladius system (e.g. 林动 → Линь Дун, 萧炎 → Сяо Янь). ")
              .append("Translate cultivation ranks, techniques and sect names by meaning and keep them consistent.\n");
        }
        if (glossary != null && !glossary.isEmpty()) {
            sb.append("Glossary — always use these exact translations:\n");
            for (Map.Entry<String, String> e : glossary.entrySet()) sb.append(e.getKey()).append(" = ").append(e.getValue()).append('\n');
        }
        if (context != null && !context.isEmpty()) {
            sb.append("For continuity, the previous translated passage ended with:\n").append(context).append('\n');
        }
        sb.append("Output ONLY the translation, with no notes, titles or quotes around it.");
        return sb.toString();
    }
}
