package io.github.qdtranslate.core;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Кэш переводов в SQLite внутри данных Qidian.
 * ui    — короткие строки интерфейса (целиком держим в памяти для мгновенной подстановки);
 * prose — главы и длинные тексты (по md5 исходника).
 */
public final class Cache extends SQLiteOpenHelper {
    private final Map<String, String> ui = new ConcurrentHashMap<>();
    private final java.util.Set<String> outputs = ConcurrentHashMap.newKeySet();

    public Cache(Context ctx) {
        super(ctx, "qdtranslate.db", null, 1);
        loadUi();
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE ui (src TEXT PRIMARY KEY, dst TEXT NOT NULL)");
        db.execSQL("CREATE TABLE prose (k TEXT PRIMARY KEY, dst TEXT NOT NULL, ts INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}

    private void loadUi() {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT src, dst FROM ui", null)) {
            while (c.moveToNext()) {
                ui.put(c.getString(0), c.getString(1));
                outputs.add(c.getString(1));
            }
        } catch (Exception e) {
            L.e("cache load", e);
        }
        L.i("UI-словарь загружен: " + ui.size() + " строк");
    }

    public String ui(String src) { return ui.get(src); }

    /** Уже переведённая нами строка (чтобы не переводить перевод, если в нём остались иероглифы). */
    public boolean isUiOutput(String s) { return outputs.contains(s); }

    public void putUi(String src, String dst) {
        ui.put(src, dst);
        outputs.add(dst);
        ContentValues v = new ContentValues();
        v.put("src", src);
        v.put("dst", dst);
        getWritableDatabase().insertWithOnConflict("ui", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public String prose(String key) {
        try (Cursor c = getReadableDatabase().rawQuery("SELECT dst FROM prose WHERE k=?", new String[]{key})) {
            return c.moveToFirst() ? c.getString(0) : null;
        }
    }

    public void putProse(String key, String dst) {
        ContentValues v = new ContentValues();
        v.put("k", key);
        v.put("dst", dst);
        v.put("ts", System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("prose", null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public int uiSize() { return ui.size(); }

    public void clearUi() {
        ui.clear();
        outputs.clear();
        getWritableDatabase().delete("ui", null, null);
    }

    public void clearProse() {
        getWritableDatabase().delete("prose", null, null);
    }
}
