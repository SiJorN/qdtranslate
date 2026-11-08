package io.github.qdtranslate.hook;

import android.graphics.Canvas;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.qdtranslate.core.Engine;
import io.github.qdtranslate.core.L;
import io.github.qdtranslate.core.Text;

/**
 * «Режим разведки»: помогает найти, где в обфусцированном коде Qidian появляется текст главы.
 * Перехватывает места, через которые обычно проходит текст главы:
 *   - javax.crypto.Cipher.doFinal — расшифровка платных глав;
 *   - java.lang.StringFactory.newStringFromBytes — превращение байтов в строку;
 *   - org.json.JSONObject.getString/optString — разбор ответа сервера;
 *   - Canvas.drawText — кто рисует строки на странице читалки.
 * Для каждого уникального места пишет стек вызовов в файл
 *   /sdcard/Android/data/com.qidian.QDReader/files/qdt_discovery.log
 * Методы из стека — кандидаты для поля «Хуки текста глав».
 * Режим тяжёлый: включайте только на время поиска.
 */
final class Discovery {
    private static final Set<String> seen = ConcurrentHashMap.newKeySet();
    private static final AtomicInteger reports = new AtomicInteger();
    private static final int MAX_REPORTS = 300;
    private static final ThreadLocal<Boolean> BUSY = ThreadLocal.withInitial(() -> false);
    private static volatile long lastDraw;
    private static File logFile;
    private static Engine engine;

    private Discovery() {}

    static void install(Engine e) {
        engine = e;
        File dir = e.app.getExternalFilesDir(null);
        logFile = new File(dir != null ? dir : e.app.getFilesDir(), "qdt_discovery.log");
        write("===== Сессия разведки " + now() + " =====\n");
        e.toast("Режим разведки включён. Откройте главу. Лог: " + logFile.getAbsolutePath());

        // 1. Расшифровка
        hookAll(XposedHelpers.findClassIfExists("javax.crypto.Cipher", null), "doFinal", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object r = p.getResult();
                if (r instanceof byte[] && ((byte[]) r).length > 600 && !BUSY.get()) {
                    BUSY.set(true);
                    try {
                        String s = new String((byte[]) r, StandardCharsets.UTF_8);
                        if (Text.cjkCount(s) > 200) report("Cipher.doFinal → байты главы", s);
                    } finally { BUSY.set(false); }
                }
            }
        });

        // 2. byte[] → String (в ART конструкторы String реализованы через StringFactory)
        hookAll(XposedHelpers.findClassIfExists("java.lang.StringFactory", null), "newStringFromBytes", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object r = p.getResult();
                if (!BUSY.get() && r instanceof String && ((String) r).length() > 500 && Text.cjkCount((String) r) > 300)
                    report("new String(byte[]) → текст главы", (String) r);
            }
        });

        // 3. JSON
        XC_MethodHook json = new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                Object r = p.getResult();
                if (r instanceof String && ((String) r).length() > 500 && Text.cjkCount((String) r) > 300)
                    report("JSONObject." + p.method.getName() + "(\"" + p.args[0] + "\")", (String) r);
            }
        };
        hookAll(org.json.JSONObject.class, "getString", json);
        hookAll(org.json.JSONObject.class, "optString", json);

        // 4. Отрисовка строк страницы
        hookAll(Canvas.class, "drawText", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                long t = System.currentTimeMillis();
                if (t - lastDraw < 300) return; // drawText вызывается на каждом кадре — берём выборку
                lastDraw = t;
                Object a = p.args.length > 0 ? p.args[0] : null;
                CharSequence s = a instanceof CharSequence ? (CharSequence) a
                    : a instanceof char[] ? new String((char[]) a) : null;
                if (s != null && Text.cjkCount(s) >= 8) report("Canvas.drawText (строка страницы)", s.toString());
            }
        });

        L.i("режим разведки установлен, лог: " + logFile);
    }

    private static void hookAll(Class<?> c, String name, XC_MethodHook h) {
        if (c == null) return;
        try { XposedBridge.hookAllMethods(c, name, h); }
        catch (Throwable t) { L.e("discovery hook " + c.getName() + "." + name, t); }
    }

    private static void report(String where, String sample) {
        if (reports.get() >= MAX_REPORTS) return;
        boolean wasBusy = BUSY.get();
        BUSY.set(true);
        try { doReport(where, sample); } finally { BUSY.set(wasBusy); }
    }

    private static void doReport(String where, String sample) {
        StackTraceElement[] st = new Throwable().getStackTrace();
        StringBuilder frames = new StringBuilder();
        StringBuilder sig = new StringBuilder(where);
        int k = 0;
        for (StackTraceElement f : st) {
            String c = f.getClassName();
            if (c.startsWith("java.") || c.startsWith("javax.") || c.startsWith("android.") || c.startsWith("androidx.")
                || c.startsWith("dalvik.") || c.startsWith("com.android.") || c.startsWith("de.robv.")
                || c.startsWith("org.lsposed.") || c.startsWith("io.github.qdtranslate.") || c.startsWith("org.json.")
                || c.startsWith("kotlin.") || c.startsWith("kotlinx.") || c.contains("LSPHooker") || c.startsWith("J."))
                continue;
            if (k < 3) sig.append('|').append(c).append('#').append(f.getMethodName());
            frames.append("    ").append(c).append('#').append(f.getMethodName())
                  .append("   (").append(f.getFileName()).append(':').append(f.getLineNumber()).append(")\n");
            if (++k >= 15) break;
        }
        if (k == 0 || !seen.add(sig.toString())) return;
        int n = reports.incrementAndGet();
        String one = sample.replace('\n', ' ');
        if (one.length() > 80) one = one.substring(0, 80) + "…";
        write("\n[" + now() + "] #" + n + "  " + where + "\n  длина " + sample.length() + ": " + one + "\n  стек (сверху — ближе к месту):\n" + frames);
        if (n <= 5) engine.toast("Разведка: найдено «" + where + "» (#" + n + ")");
    }

    private static synchronized void write(String s) {
        L.i("DISCOVERY " + s);
        try (Writer w = new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8)) {
            w.write(s);
        } catch (Exception ignored) {}
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
    }
}
