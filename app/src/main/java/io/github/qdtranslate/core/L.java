package io.github.qdtranslate.core;

import android.util.Log;

import de.robv.android.xposed.XposedBridge;

/** Логирование и в logcat (тег QDT), и в журнал LSPosed. */
public final class L {
    public static final String TAG = "QDT";

    private L() {}

    public static void i(String msg) {
        Log.i(TAG, msg);
        try { XposedBridge.log("[QDT] " + msg); } catch (Throwable ignored) {}
    }

    public static void e(String msg, Throwable t) {
        Log.e(TAG, msg, t);
        try { XposedBridge.log("[QDT] " + msg + ": " + Log.getStackTraceString(t)); } catch (Throwable ignored) {}
    }
}
