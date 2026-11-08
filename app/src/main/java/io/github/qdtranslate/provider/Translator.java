package io.github.qdtranslate.provider;

import java.util.List;
import java.util.Map;

public interface Translator {
    /** Пакетный перевод коротких строк интерфейса. Элемент null — не удалось перевести. */
    List<String> translateStrings(List<String> src, String targetLang) throws Exception;

    /** Перевод фрагмента художественного текста (несколько абзацев, разделённых \n). */
    String translateProse(String chunk, String targetLang, Map<String, String> glossary, String context) throws Exception;

    /** Список доступных моделей (для кнопки в настройках). */
    List<String> listModels() throws Exception;
}
