package io.github.qdtranslate.hook;

import android.app.Application;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import io.github.qdtranslate.core.Engine;
import io.github.qdtranslate.core.L;

/** Точка входа Xposed (указана в assets/xposed_init). Одинаково работает в LSPosed (root) и LSPatch (без root). */
public class Entry implements IXposedHookLoadPackage {
    public static final String TARGET = "com.qidian.QDReader";
    private static boolean done;

    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lp) {
        if (!TARGET.equals(lp.packageName)) return;
        // Только главный процесс: у Qidian есть служебные процессы (push, web и т.д.)
        if (lp.processName != null && !lp.processName.equals(TARGET)) return;
        if (done) return;
        done = true;

        L.i("загружен в " + lp.processName);

        // Хуки интерфейса ставим сразу — они безопасны до инициализации движка
        UiHooks.install();

        XposedHelpers.findAndHookMethod(Application.class, "onCreate", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam param) {
                Application app = (Application) param.thisObject;
                try {
                    Engine e = Engine.init(app);
                    ClassLoader cl = app.getClassLoader() != null ? app.getClassLoader() : lp.classLoader;
                    ContentHooks.install(e, cl);
                    if (e.cfg.discovery()) Discovery.install(e);
                    FloatButton.install();
                    L.i("движок готов");
                } catch (Throwable t) {
                    L.e("init", t);
                }
            }
        });
    }
}
