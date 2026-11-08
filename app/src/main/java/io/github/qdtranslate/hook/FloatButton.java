package io.github.qdtranslate.hook;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import io.github.qdtranslate.core.Engine;
import io.github.qdtranslate.core.L;
import io.github.qdtranslate.ui.SettingsDialog;

/**
 * Плавающая кнопка «译» поверх каждого экрана Qidian (добавляется в DecorView активности,
 * поэтому разрешение «поверх других окон» не нужно).
 * Нажатие — настройки, долгое нажатие — быстро включить/выключить перевод, перетаскивание — переместить.
 */
public final class FloatButton {
    private FloatButton() {}

    static void install() {
        XposedHelpers.findAndHookMethod(Activity.class, "onResume", new XC_MethodHook() {
            @Override protected void afterHookedMethod(MethodHookParam p) {
                try { attach((Activity) p.thisObject); } catch (Throwable t) { L.e("float button", t); }
            }
        });
    }

    @SuppressLint("ClickableViewAccessibility")
    private static void attach(final Activity a) {
        final Engine e = Engine.get();
        if (e == null || a.getWindow() == null) return;
        View decorView = a.getWindow().getDecorView();
        if (!(decorView instanceof FrameLayout)) return;
        final FrameLayout decor = (FrameLayout) decorView;
        if (decor.findViewWithTag(UiHooks.MARK) != null) {
            refresh(decor.findViewWithTag(UiHooks.MARK), e);
            return;
        }

        DisplayMetrics dm = a.getResources().getDisplayMetrics();
        final int size = (int) (44 * dm.density);
        final TextView b = UiHooks.own(new TextView(a));
        b.setText("译");
        b.setTextSize(18);
        b.setTextColor(Color.WHITE);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(0xDD1E88E5);
        b.setBackground(bg);
        b.setElevation(8 * dm.density);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size, Gravity.TOP | Gravity.START);
        decor.addView(b, lp);
        b.setX(e.cfg.prefs().getFloat("btn_x", dm.widthPixels - size - 8 * dm.density));
        b.setY(e.cfg.prefs().getFloat("btn_y", dm.heightPixels * 0.62f));
        refresh(b, e);

        b.setOnClickListener(v -> SettingsDialog.show(a));
        b.setOnLongClickListener(v -> {
            boolean on = !e.cfg.enabled();
            e.cfg.prefs().edit().putBoolean("enabled", on).apply();
            refresh(v, e);
            e.toast(on ? "Перевод включён" : "Перевод выключен (оригинал после перезахода на экран)");
            return true;
        });

        final float slop = 10 * dm.density;
        b.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy, sx, sy;
            boolean drag;

            @Override public boolean onTouch(View v, MotionEvent ev) {
                switch (ev.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = ev.getRawX(); sy = ev.getRawY();
                        dx = v.getX() - sx; dy = v.getY() - sy;
                        drag = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        if (!drag && Math.hypot(ev.getRawX() - sx, ev.getRawY() - sy) > slop) {
                            drag = true;
                            v.cancelLongPress();
                            v.setPressed(false);
                        }
                        if (drag) {
                            ViewGroup parent = (ViewGroup) v.getParent();
                            v.setX(clamp(ev.getRawX() + dx, 0, parent.getWidth() - v.getWidth()));
                            v.setY(clamp(ev.getRawY() + dy, 0, parent.getHeight() - v.getHeight()));
                            return true;
                        }
                        return false;
                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (drag) {
                            e.cfg.prefs().edit().putFloat("btn_x", v.getX()).putFloat("btn_y", v.getY()).apply();
                            v.setPressed(false);
                            return true;
                        }
                        return false;
                }
                return false;
            }
        });
    }

    private static void refresh(View b, Engine e) {
        b.setAlpha(e.cfg.enabled() ? 0.85f : 0.35f);
    }

    private static float clamp(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
