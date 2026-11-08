package io.github.qdtranslate.provider;

import io.github.qdtranslate.core.Config;

public final class Providers {
    private Providers() {}

    public static Translator create(Config cfg, String id) throws Exception {
        Preset p = Preset.byId(id);
        String url = cfg.url(p.id, p.url);
        String key = cfg.key(p.id);
        String model = cfg.model(p.id, p.model);
        if (p.needsKey && key.isEmpty()) throw new IllegalStateException("Не задан API-ключ для «" + p.title + "»");
        switch (p.kind) {
            case GOOGLE_FREE: return new GoogleFree();
            case GIGACHAT:    return new GigaChat(url, key, model, cfg.gigachatScope(), cfg.gigachatPem(), cfg.temperature());
            default:          return new OpenAiCompat(url, key, model, cfg.temperature());
        }
    }
}
