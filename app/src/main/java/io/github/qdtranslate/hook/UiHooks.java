package io.github.qdtranslate.hook;

import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.text.style.ImageSpan;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.qdtranslate.core.Engine;
import io.github.qdtranslate.core.L;
import io.github.qdtranslate.core.Text;

/**
 * Перевод интерфейса — по принципу встроенного переводчика Alipay:
 * перехватываем все setText/setHint у TextView. Если перевод уже в кэше, подменяем текст сразу,
 * если нет — показываем оригинал, ставим строку в очередь и обновляем view, когда придёт перевод.
 */
public final class UiHooks {
    /** Метка наших собственных view (кнопка «译», диалог настроек) — их не трогаем. */
    public static final String MARK = "qdt-own-view";

    private static final int SHORT_MAX = 150;   // короче — пакетный перевод интерфейса
    private static final int LONG_MAX = 5000;   // длиннее — скорее всего, это читалка; не трогаем

    private UiHooks() {}

    static void install() {
        try {
            XposedHelpers.findAndHookMethod(TextView.class, "setText",
                CharSequence.class, TextView.BufferType.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        TextView tv = (TextView) param.thisObject;
                        if (tv instanceof EditText) return; // пользовательский ввод не переводим
                        CharSequence r = translateFor(tv, (CharSequence) param.args[0], false);
                        if (r != null) param.args[0] = r;
                    }
                });

            XposedHelpers.findAndHookMethod(TextView.class, "setHint", CharSequence.class, new XC_MethodHook() {
                @Override protected void beforeHookedMethod(MethodHookParam param) {
                    CharSequence r = translateFor((TextView) param.thisObject, (CharSequence) param.args[0], true);
                    if (r != null) param.args[0] = r;
                }
            });

            XposedHelpers.findAndHookMethod(Toast.class, "makeText",
                android.content.Context.class, CharSequence.class, int.class, new XC_MethodHook() {
                    @Override protected void beforeHookedMethod(MethodHookParam param) {
                        CharSequence r = translateFor(null, (CharSequence) param.args[1], false);
                        if (r != null) param.args[1] = r;
                    }
                });
            L.i("хуки интерфейса установлены");
        } catch (Throwable t) {
            L.e("ui hooks", t);
        }
    }

    /** Возвращает текст для немедленной подстановки или null (оставить как есть). */
    static CharSequence translateFor(TextView tv, CharSequence cs, final boolean hint) {
        Engine e = Engine.get();
        if (e == null || cs == null || cs.length() == 0 || !e.uiActive()) return null;
        if (tv != null && MARK.equals(tv.getTag())) return null;
        if (!Text.hasCjk(cs)) return null;
        if (cs instanceof Spanned && hasSpecialSpans((Spanned) cs)) return null; // ссылки/картинки сломаются
        final String src = cs.toString();
        if (src.length() > LONG_MAX || e.cache.isUiOutput(src)) return null;
        // Длинный текст, где иероглифов мало, — это уже наш перевод с оставшимися именами; не гоняем по кругу
        if (src.length() > SHORT_MAX && Text.cjkCount(src) * 5 < src.length()) return null;

        final WeakReference<TextView> ref = tv == null ? null : new WeakReference<>(tv);
        Engine.Sink sink = ref == null ? null : (s, d) -> {
            TextView v = ref.get();
            if (v == null) return;
            CharSequence now = hint ? v.getHint() : v.getText();
            if (now != null && s.equals(now.toString())) { // view не успел показать другое
                if (hint) v.setHint(d); else v.setText(d);
            }
        };

        if (src.length() <= SHORT_MAX) {
            String d = e.cache.ui(src);
            if (d != null) return d;
            e.requestUi(src, sink);
        } else {
            String d = e.cachedLong(src);
            if (d != null) return d;
            e.requestLong(src, sink);
        }
        return null;
    }

    private static boolean hasSpecialSpans(Spanned s) {
        return s.getSpans(0, s.length(), ClickableSpan.class).length > 0
            || s.getSpans(0, s.length(), ImageSpan.class).length > 0;
    }

    /** Пометить свою view, чтобы хуки её не переводили. */
    public static <T extends View> T own(T v) {
        v.setTag(MARK);
        return v;
    }
}
