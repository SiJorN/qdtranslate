package io.github.qdtranslate.core;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import io.github.qdtranslate.provider.Providers;
import io.github.qdtranslate.provider.Translator;

/**
 * Центральный движок перевода внутри процесса Qidian.
 *  - интерфейс: строки копятся в очереди и уходят пакетами (≤25 строк) раз в 400 мс;
 *  - длинные тексты (аннотации, комментарии): по одному, асинхронно;
 *  - главы: разбиваются на части по абзацам, переводятся последовательно, результат кэшируется.
 */
public final class Engine {
    private static volatile Engine INSTANCE;

    public static Engine get() { return INSTANCE; }

    public static synchronized Engine init(Context app) {
        if (INSTANCE == null) INSTANCE = new Engine(app.getApplicationContext());
        return INSTANCE;
    }

    public final Context app;
    public final Config cfg;
    public final Cache cache;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService uiExec = Executors.newSingleThreadExecutor();
    private final ExecutorService longExec = Executors.newSingleThreadExecutor();
    private final ExecutorService proseExec = Executors.newSingleThreadExecutor();

    private Engine(Context app) {
        this.app = app;
        this.cfg = new Config(app);
        this.cache = new Cache(app);
    }

    public boolean uiActive()      { return cfg.enabled() && cfg.uiEnabled(); }
    public boolean contentActive() { return cfg.enabled() && cfg.contentEnabled(); }

    // ======================= Интерфейс =======================

    public interface Sink { void apply(String src, String dst); }

    private static final int UI_BATCH = 25;
    private final Map<String, List<Sink>> waiting = new HashMap<>();
    private final LinkedHashSet<String> queued = new LinkedHashSet<>();
    private boolean flushScheduled;
    private volatile long uiBackoffUntil;

    /** Поставить короткую строку в очередь; sink будет вызван в главном потоке. */
    public void requestUi(String src, Sink sink) {
        if (System.currentTimeMillis() < uiBackoffUntil) return;
        synchronized (waiting) {
            List<Sink> l = waiting.get(src);
            if (l == null) {
                l = new ArrayList<>(2);
                waiting.put(src, l);
                queued.add(src);
            }
            if (sink != null && l.size() < 30) l.add(sink);
            if (!flushScheduled) {
                flushScheduled = true;
                main.postDelayed(this::flushUi, 400);
            }
        }
    }

    private void flushUi() {
        final List<String> batch = new ArrayList<>(UI_BATCH);
        synchronized (waiting) {
            flushScheduled = false;
            Iterator<String> it = queued.iterator();
            while (it.hasNext() && batch.size() < UI_BATCH) { batch.add(it.next()); it.remove(); }
            if (!queued.isEmpty()) { flushScheduled = true; main.postDelayed(this::flushUi, 400); }
        }
        if (batch.isEmpty()) return;
        uiExec.submit(() -> {
            List<String> res = null;
            try {
                Translator tr = Providers.create(cfg, cfg.uiProvider());
                res = tr.translateStrings(batch, cfg.targetLang());
            } catch (Exception e) {
                uiBackoffUntil = System.currentTimeMillis() + 30000;
                toast("Перевод интерфейса: " + e.getMessage());
                L.e("ui batch", e);
            }
            for (int i = 0; i < batch.size(); i++) {
                String src = batch.get(i);
                String dst = res != null && i < res.size() ? res.get(i) : null;
                List<Sink> sinks;
                synchronized (waiting) { sinks = waiting.remove(src); }
                if (dst == null || dst.trim().isEmpty()) continue;
                final String d = dst.trim();
                cache.putUi(src, d);
                if (sinks != null) for (final Sink s : sinks) main.post(() -> s.apply(src, d));
            }
        });
    }

    /** Длинный текст в TextView (аннотация книги, комментарий): переводим целиком как прозу. */
    private final Set<String> longInFlight = ConcurrentHashMap.newKeySet();

    public String cachedLong(String src) { return cache.prose(key(src)); }

    public void requestLong(final String src, final Sink sink) {
        final String k = key(src);
        if (!longInFlight.add(k)) return;
        longExec.submit(() -> {
            try {
                String r = translateProseText(src);
                cache.putProse(k, r);
                if (sink != null) main.post(() -> sink.apply(src, r));
            } catch (Exception e) {
                L.e("long text", e);
            } finally {
                longInFlight.remove(k);
            }
        });
    }

    // ======================= Главы =======================

    private final Map<String, Future<String>> jobs = new ConcurrentHashMap<>();
    private final Set<String> notifyWhenDone = ConcurrentHashMap.newKeySet();

    private String key(String src) { return Text.md5(cfg.targetLang() + "|" + cfg.novelProvider() + "|" + src); }

    public String cachedProse(String src) { return cache.prose(key(src)); }

    public synchronized Future<String> proseAsync(final String src) {
        final String k = key(src);
        Future<String> f = jobs.get(k);
        if (f != null && !f.isDone()) return f;
        if (jobs.size() > 50) jobs.values().removeIf(Future::isDone);
        f = proseExec.submit(() -> {
            String r = translateProseText(src);
            cache.putProse(k, r);
            if (notifyWhenDone.remove(k)) toast("Глава переведена — откройте её заново");
            return r;
        });
        jobs.put(k, f);
        return f;
    }

    /** Запустить перевод в фоне и сообщить, когда готово (для вызовов из главного потока). */
    public void proseInBackground(String src) {
        notifyWhenDone.add(key(src));
        proseAsync(src);
        toast("Перевожу главу…");
    }

    /** Ждём перевод не дольше timeoutMs (только из фонового потока!). null — не успели или ошибка. */
    public String proseBlocking(String src, long timeoutMs) {
        String c = cachedProse(src);
        if (c != null) return c;
        Future<String> f = proseAsync(src);
        try {
            return f.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            notifyWhenDone.add(key(src));
            toast("Перевод ещё идёт — откройте главу чуть позже");
        } catch (Exception e) {
            toast("Ошибка перевода главы: " + rootMsg(e));
            L.e("prose", e);
        }
        return null;
    }

    /** Разбивка на части по абзацам, перевод с глоссарием и контекстом предыдущей части. */
    private String translateProseText(String src) throws Exception {
        Translator tr = Providers.create(cfg, cfg.novelProvider());
        String text = src.replace("\r\n", "\n");
        String sep = text.contains("\n\n") ? "\n\n" : "\n";

        List<String> paras = new ArrayList<>();
        String indent = null;
        for (String line : text.split("\n")) {
            String t = line.trim().replaceAll("^[\\u3000\\s]+|[\\u3000\\s]+$", "");
            if (t.isEmpty()) continue;
            if (indent == null) {
                int i = 0;
                while (i < line.length() && (line.charAt(i) == '　' || line.charAt(i) == ' ')) i++;
                indent = i > 0 ? "　　" : "";
            }
            paras.add(t);
        }
        if (indent == null) indent = "";

        List<String> chunks = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int limit = Math.max(300, cfg.chunkChars());
        for (String p : paras) {
            if (cur.length() > 0 && cur.length() + p.length() > limit) { chunks.add(cur.toString()); cur.setLength(0); }
            if (cur.length() > 0) cur.append('\n');
            cur.append(p);
        }
        if (cur.length() > 0) chunks.add(cur.toString());

        Map<String, String> glossary = cfg.glossary();
        StringBuilder out = new StringBuilder();
        String ctx = "";
        for (String ch : chunks) {
            String tr1 = tr.translateProse(ch, cfg.targetLang(), glossaryFor(glossary, ch), ctx);
            if (tr1 == null || tr1.trim().isEmpty()) throw new IllegalStateException("пустой ответ модели");
            for (String line : tr1.split("\n")) {
                String t = line.trim();
                if (t.isEmpty()) continue;
                if (out.length() > 0) out.append(sep);
                out.append(indent).append(t);
            }
            ctx = tr1.length() > 300 ? tr1.substring(tr1.length() - 300) : tr1;
        }
        return out.toString();
    }

    private static Map<String, String> glossaryFor(Map<String, String> all, String chunk) {
        Map<String, String> m = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : all.entrySet()) if (chunk.contains(e.getKey())) m.put(e.getKey(), e.getValue());
        return m;
    }

    // ======================= Служебное =======================

    private volatile String lastToast;
    private volatile long lastToastAt;

    public void toast(final String msg) {
        long now = System.currentTimeMillis();
        if (msg.equals(lastToast) && now - lastToastAt < 20000) return;
        lastToast = msg;
        lastToastAt = now;
        main.post(() -> Toast.makeText(app, msg, Toast.LENGTH_LONG).show());
    }

    public void runInBackground(Runnable r) { longExec.submit(r); }

    public void post(Runnable r) { main.post(r); }

    public static String rootMsg(Throwable t) {
        while (t.getCause() != null) t = t.getCause();
        return t.getMessage() != null ? t.getMessage() : t.getClass().getSimpleName();
    }
}
