package io.github.qdtranslate.hook;

import android.os.Looper;

import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import io.github.qdtranslate.core.Engine;
import io.github.qdtranslate.core.L;
import io.github.qdtranslate.core.Text;

/**
 * Перевод текста глав. Классы читалки Qidian обфусцированы и меняются от версии к версии,
 * поэтому точки перехвата не зашиты в код, а задаются в настройках (поле «Хуки текста глав»):
 *
 *   com.example.ChapterLoader#getContent          — подменить возвращаемую строку (по умолчанию)
 *   com.example.ChapterLoader#getContent@ret      — то же явно
 *   com.example.Parser#parse@arg0                 — подменить строковый аргумент №0 перед вызовом
 *
 * Подменять текст нужно ДО разбивки на страницы — тогда движок читалки сам сверстает русский текст.
 * Найти такие методы помогает «Режим разведки» (см. Discovery).
 */
final class ContentHooks {
    private ContentHooks() {}

    static void install(final Engine e, ClassLoader cl) {
        String spec = e.cfg.contentHooks();
        if (spec.trim().isEmpty()) {
            L.i("хуки глав не заданы — включите режим разведки, чтобы найти их");
            return;
        }
        for (String raw : spec.split("\n")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("//") || line.startsWith("#")) continue;
            try {
                installOne(e, cl, line);
            } catch (Throwable t) {
                L.e("хук главы «" + line + "»", t);
                e.toast("Не удалось поставить хук: " + line);
            }
        }
    }

    private static void installOne(final Engine e, ClassLoader cl, String line) {
        String target = "ret";
        int at = line.lastIndexOf('@');
        if (at > 0) { target = line.substring(at + 1).trim(); line = line.substring(0, at).trim(); }
        int hash = line.indexOf('#');
        if (hash <= 0) throw new IllegalArgumentException("ожидается Класс#метод");
        String cls = line.substring(0, hash), method = line.substring(hash + 1);

        Class<?> c = XposedHelpers.findClass(cls, cl);
        final boolean ret = "ret".equals(target);
        final int argIdx = ret ? -1 : Integer.parseInt(target.replace("arg", ""));

        XC_MethodHook hook = new XC_MethodHook() {
            @Override protected void beforeHookedMethod(MethodHookParam p) {
                if (ret || !e.contentActive() || argIdx >= p.args.length) return;
                Object a = p.args[argIdx];
                if (a instanceof CharSequence) {
                    String t = translate(e, a.toString());
                    if (t != null) p.args[argIdx] = t;
                }
            }

            @Override protected void afterHookedMethod(MethodHookParam p) {
                if (!ret || !e.contentActive()) return;
                Object r = p.getResult();
                if (r instanceof CharSequence) {
                    String t = translate(e, r.toString());
                    if (t != null) p.setResult(t);
                }
            }
        };

        int n = 0;
        if ("<init>".equals(method)) {
            Set<XC_MethodHook.Unhook> u = XposedBridge.hookAllConstructors(c, hook);
            n = u.size();
        } else {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals(method)) continue;
                Class<?> rt = m.getReturnType();
                if (ret && rt != Object.class && !CharSequence.class.isAssignableFrom(rt)) continue;
                XposedBridge.hookMethod((Member) m, hook);
                n++;
            }
        }
        if (n == 0) throw new IllegalStateException("метод не найден: " + method);
        L.i("хук главы: " + cls + "#" + method + " @" + target + " (перегрузок: " + n + ")");
    }

    /** Кэш → мгновенно; фоновый поток → ждём до waitSec; главный поток → переводим в фоне и сообщаем. */
    private static String translate(Engine e, String src) {
        if (!Text.looksLikeProse(src)) return null;
        String c = e.cachedProse(src);
        if (c != null) return c;
        if (Looper.myLooper() == Looper.getMainLooper()) {
            e.proseInBackground(src);
            return null;
        }
        return e.proseBlocking(src, e.cfg.waitSec() * 1000L);
    }
}
