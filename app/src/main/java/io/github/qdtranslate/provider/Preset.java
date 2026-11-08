package io.github.qdtranslate.provider;

import java.util.Arrays;
import java.util.List;

/**
 * Готовые пресеты. Почти все провайдеры говорят на OpenAI-совместимом API,
 * поэтому новый провайдер = ещё одна строка здесь (или пресет «Свой»).
 * Имена моделей у бесплатных тарифов часто меняются — в настройках есть кнопка «Список моделей».
 */
public final class Preset {
    public enum Kind { OPENAI, GIGACHAT, GOOGLE_FREE }

    public final String id, title, url, model, note;
    public final Kind kind;
    public final boolean needsKey;

    Preset(String id, String title, Kind kind, String url, String model, boolean needsKey, String note) {
        this.id = id; this.title = title; this.kind = kind; this.url = url;
        this.model = model; this.needsKey = needsKey; this.note = note;
    }

    @Override public String toString() { return title; }

    public static final List<Preset> ALL = Arrays.asList(
        new Preset("google_free", "Google Translate (без ключа)", Kind.GOOGLE_FREE,
            "https://translate.googleapis.com", "", false,
            "Не LLM, но бесплатно и без регистрации. Хорош для интерфейса."),
        new Preset("gemini", "Google Gemini (бесплатный тариф)", Kind.OPENAI,
            "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", true,
            "Ключ: aistudio.google.com. Из РФ нужен VPN."),
        new Preset("groq", "Groq (бесплатный тариф)", Kind.OPENAI,
            "https://api.groq.com/openai/v1", "qwen/qwen3-32b", true,
            "Ключ: console.groq.com. Лимит токенов в минуту маленький — лучше для интерфейса."),
        new Preset("openrouter", "OpenRouter (модели :free)", Kind.OPENAI,
            "https://openrouter.ai/api/v1", "deepseek/deepseek-chat-v3-0324:free", true,
            "Ключ: openrouter.ai. Около 50 запросов в день на :free-моделях. Выберите модель кнопкой «Список»."),
        new Preset("cerebras", "Cerebras (бесплатный тариф)", Kind.OPENAI,
            "https://api.cerebras.ai/v1", "qwen-3-235b-a22b-instruct-2507", true,
            "Ключ: cloud.cerebras.ai. Большой дневной лимит токенов."),
        new Preset("mistral", "Mistral (бесплатный режим)", Kind.OPENAI,
            "https://api.mistral.ai/v1", "mistral-small-latest", true,
            "Ключ: console.mistral.ai."),
        new Preset("zai", "Z.ai GLM (Flash — бесплатно)", Kind.OPENAI,
            "https://api.z.ai/api/paas/v4", "glm-4.5-flash", true,
            "Ключ: z.ai. Китайская модель, хорошо знает китайский."),
        new Preset("deepseek", "DeepSeek (платно, дёшево)", Kind.OPENAI,
            "https://api.deepseek.com/v1", "deepseek-chat", true,
            "Ключ: platform.deepseek.com. Одна из лучших моделей для китайского."),
        new Preset("gigachat", "GigaChat (Сбер, Freemium)", Kind.GIGACHAT,
            "https://gigachat.devices.sberbank.ru/api/v1", "GigaChat-2", true,
            "Ключ авторизации: developers.sber.ru. Работает из РФ без VPN. Нужен сертификат Минцифры (PEM) в настройках."),
        new Preset("local", "Локальная модель (Ollama / LM Studio)", Kind.OPENAI,
            "http://192.168.1.10:11434/v1", "qwen2.5:7b", false,
            "Модель на вашем ПК в локальной сети. Укажите IP компьютера."),
        new Preset("custom", "Свой OpenAI-совместимый", Kind.OPENAI,
            "https://example.com/v1", "model-name", true,
            "Любой сервис с /v1/chat/completions.")
    );

    public static Preset byId(String id) {
        for (Preset p : ALL) if (p.id.equals(id)) return p;
        return ALL.get(0);
    }

    public static int indexOf(String id) {
        for (int i = 0; i < ALL.size(); i++) if (ALL.get(i).id.equals(id)) return i;
        return 0;
    }
}
