package io.github.qdtranslate.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import io.github.qdtranslate.core.Config;
import io.github.qdtranslate.core.Engine;
import io.github.qdtranslate.hook.UiHooks;
import io.github.qdtranslate.provider.Preset;
import io.github.qdtranslate.provider.Providers;
import io.github.qdtranslate.provider.Translator;

/** Окно настроек, встроенное в Qidian. Вёрстка программная: ресурсы модуля в процессе Qidian недоступны. */
public final class SettingsDialog {
    private final Activity a;
    private final Engine e;
    private final Config cfg;
    private final LinearLayout root;

    private Switch swEnabled, swUi, swContent, swDiscovery;
    private Spinner spNovel, spUi, spEdit;
    private TextView tvNote;
    private EditText etUrl, etKey, etModel, etLang, etTemp, etChunk, etWait, etGlossary, etHooks, etPem;
    private String editingId;

    public static void show(Activity a) {
        Engine e = Engine.get();
        if (e == null) return;
        new SettingsDialog(a, e).open();
    }

    private SettingsDialog(Activity a, Engine e) {
        this.a = a;
        this.e = e;
        this.cfg = e.cfg;
        this.root = new LinearLayout(a);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        root.setPadding(p, dp(8), p, dp(8));
    }

    private void open() {
        SharedPreferences sp = cfg.prefs();

        header("Основное");
        swEnabled = sw("Перевод включён (долгое нажатие на «译» — тоже)", cfg.enabled());
        swUi = sw("Переводить интерфейс", cfg.uiEnabled());
        swContent = sw("Переводить текст глав", cfg.contentEnabled());
        etLang = field("Язык перевода (код: ru, en, uk…)", cfg.targetLang(), false);

        header("Какие нейросети использовать");
        spNovel = spinner("Для глав и длинных текстов:", cfg.novelProvider());
        spUi = spinner("Для интерфейса:", cfg.uiProvider());

        header("Настройка провайдера");
        spEdit = spinner("Провайдер:", cfg.novelProvider());
        tvNote = small("");
        etUrl = field("Адрес API (base URL)", "", false);
        etKey = field("API-ключ", "", false);
        etKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        etModel = field("Модель", "", false);
        LinearLayout row = new LinearLayout(a);
        row.addView(button("Список моделей", v -> listModels()));
        row.addView(button("Проверить", v -> test()));
        root.addView(row);
        editingId = Preset.ALL.get(spEdit.getSelectedItemPosition()).id;
        loadProvider(editingId);
        spEdit.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                String newId = Preset.ALL.get(pos).id;
                if (newId.equals(editingId)) return;
                saveProvider(editingId);
                editingId = newId;
                loadProvider(newId);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        header("Перевод глав");
        etGlossary = field("Глоссарий: по строке «原文=перевод» (имена, техники, секты)", cfg.glossaryRaw(), true);
        etTemp = field("Temperature (0–1)", sp.getString("temperature", "0.3"), false);
        etChunk = field("Размер части, символов", sp.getString("chunk_chars", "1500"), false);
        etWait = field("Ждать перевод главы, секунд", sp.getString("wait_sec", "60"), false);
        etHooks = field("Хуки текста глав: Класс#метод[@ret|@argN], по одному в строке", cfg.contentHooks(), true);
        etHooks.setTypeface(Typeface.MONOSPACE);

        header("Разведка (поиск хуков)");
        swDiscovery = sw("Режим разведки (после перезапуска Qidian)", cfg.discovery());
        File dir = a.getExternalFilesDir(null);
        small("Лог: " + new File(dir != null ? dir : a.getFilesDir(), "qdt_discovery.log").getAbsolutePath());

        header("GigaChat");
        etPem = field("Корневой сертификат Минцифры (PEM, с BEGIN/END)", cfg.gigachatPem(), true);

        header("Кэш");
        small("Строк интерфейса в словаре: " + e.cache.uiSize());
        LinearLayout row2 = new LinearLayout(a);
        row2.addView(button("Очистить интерфейс", v -> { e.cache.clearUi(); e.toast("Кэш интерфейса очищен"); }));
        row2.addView(button("Очистить главы", v -> { e.cache.clearProse(); e.toast("Кэш глав очищен"); }));
        root.addView(row2);

        ScrollView sv = UiHooks.own(new ScrollView(a));
        sv.addView(root);
        new AlertDialog.Builder(a)
            .setTitle("QDTranslate — ИИ-перевод")
            .setView(sv)
            .setPositiveButton("Сохранить", (d, w) -> save())
            .setNegativeButton("Отмена", null)
            .show();
    }

    private void loadProvider(String id) {
        Preset p = Preset.byId(id);
        tvNote.setText(p.note);
        etUrl.setText(cfg.url(id, p.url));
        etKey.setText(cfg.key(id));
        etModel.setText(cfg.model(id, p.model));
        etKey.setEnabled(p.kind != Preset.Kind.GOOGLE_FREE);
        etModel.setEnabled(p.kind != Preset.Kind.GOOGLE_FREE);
    }

    private void saveProvider(String id) {
        Preset p = Preset.byId(id);
        SharedPreferences.Editor ed = cfg.prefs().edit();
        String url = etUrl.getText().toString().trim();
        String model = etModel.getText().toString().trim();
        if (url.isEmpty() || url.equals(p.url)) ed.remove("url_" + id); else ed.putString("url_" + id, url);
        if (model.isEmpty() || model.equals(p.model)) ed.remove("model_" + id); else ed.putString("model_" + id, model);
        ed.putString("key_" + id, etKey.getText().toString().trim());
        ed.commit();
    }

    private void save() {
        saveProvider(editingId);
        cfg.prefs().edit()
            .putBoolean("enabled", swEnabled.isChecked())
            .putBoolean("ui_enabled", swUi.isChecked())
            .putBoolean("content_enabled", swContent.isChecked())
            .putBoolean("discovery", swDiscovery.isChecked())
            .putString("target_lang", etLang.getText().toString().trim().isEmpty() ? "ru" : etLang.getText().toString().trim())
            .putString("novel_provider", Preset.ALL.get(spNovel.getSelectedItemPosition()).id)
            .putString("ui_provider", Preset.ALL.get(spUi.getSelectedItemPosition()).id)
            .putString("glossary", etGlossary.getText().toString())
            .putString("temperature", etTemp.getText().toString().trim())
            .putString("chunk_chars", etChunk.getText().toString().trim())
            .putString("wait_sec", etWait.getText().toString().trim())
            .putString("content_hooks", etHooks.getText().toString())
            .putString("gigachat_pem", etPem.getText().toString())
            .apply();
        e.toast("Сохранено. Хуки глав и разведка применятся после перезапуска Qidian.");
    }

    private void listModels() {
        saveProvider(editingId);
        final String id = editingId;
        e.toast("Загружаю список моделей…");
        new Thread(() -> {
            try {
                Translator t = Providers.create(cfg, id);
                final List<String> models = t.listModels();
                a.runOnUiThread(() -> {
                    final String[] items = models.toArray(new String[0]);
                    new AlertDialog.Builder(a)
                        .setTitle("Модели (" + items.length + ")")
                        .setItems(items, (d, w) -> etModel.setText(items[w]))
                        .show();
                });
            } catch (Exception ex) {
                e.toast("Не удалось получить модели: " + Engine.rootMsg(ex));
            }
        }).start();
    }

    private void test() {
        saveProvider(editingId);
        final String id = editingId;
        final String lang = etLang.getText().toString().trim().isEmpty() ? "ru" : etLang.getText().toString().trim();
        e.toast("Проверяю…");
        new Thread(() -> {
            String msg;
            long t0 = System.currentTimeMillis();
            try {
                Translator t = Providers.create(cfg, id);
                String r = t.translateProse("林动抬头看向远方的青阳镇，心中暗暗发誓：总有一天，我要走出这片天地！", lang, null, null);
                msg = r + "\n\n(" + (System.currentTimeMillis() - t0) + " мс)";
            } catch (Exception ex) {
                msg = "Ошибка: " + Engine.rootMsg(ex);
            }
            final String m = msg;
            a.runOnUiThread(() -> new AlertDialog.Builder(a).setTitle("Проверка: " + Preset.byId(id).title)
                .setMessage(m).setPositiveButton("OK", null).show());
        }).start();
    }

    // ---------- конструктор формы ----------

    private void header(String t) {
        TextView v = UiHooks.own(new TextView(a));
        v.setText(t);
        v.setTextSize(16);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setPadding(0, dp(14), 0, dp(4));
        root.addView(v);
    }

    private TextView small(String t) {
        TextView v = UiHooks.own(new TextView(a));
        v.setText(t);
        v.setTextSize(12);
        v.setAlpha(0.7f);
        root.addView(v);
        return v;
    }

    private Switch sw(String label, boolean on) {
        Switch s = UiHooks.own(new Switch(a));
        s.setText(label);
        s.setChecked(on);
        s.setPadding(0, dp(4), 0, dp(4));
        root.addView(s);
        return s;
    }

    private EditText field(String hint, String value, boolean multi) {
        TextView label = UiHooks.own(new TextView(a));
        label.setText(hint);
        label.setTextSize(12);
        label.setPadding(0, dp(6), 0, 0);
        root.addView(label);
        EditText et = new EditText(a);
        et.setText(value);
        et.setTextSize(14);
        if (multi) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
            et.setMinLines(3);
            et.setMaxLines(8);
        } else {
            et.setSingleLine(true);
        }
        root.addView(et, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return et;
    }

    private Spinner spinner(String label, String selectedId) {
        TextView l = UiHooks.own(new TextView(a));
        l.setText(label);
        l.setTextSize(12);
        root.addView(l);
        Spinner s = new Spinner(a);
        ArrayAdapter<Preset> ad = new ArrayAdapter<>(a, android.R.layout.simple_spinner_item, Preset.ALL);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        s.setAdapter(ad);
        s.setSelection(Preset.indexOf(selectedId));
        root.addView(s);
        return s;
    }

    private Button button(String t, View.OnClickListener l) {
        Button b = new Button(a);
        b.setText(t);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return b;
    }

    private int dp(int v) {
        return (int) (v * a.getResources().getDisplayMetrics().density);
    }
}
