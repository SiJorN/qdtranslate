package io.github.qdtranslate.ui;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;

/** Экран самого модуля (вне Qidian): только инструкция. Все настройки — внутри Qidian по кнопке «译». */
public class InfoActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        TextView t = new TextView(this);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        t.setPadding(p, p, p, p);
        t.setTextSize(15);
        t.setText("QDTranslate — ИИ-перевод для Qidian (起点读书)\n\n"
            + "1. С root: включите модуль в LSPosed, область — com.qidian.QDReader, перезапустите Qidian.\n"
            + "   Без root: в LSPatch Manager пропатчите Qidian и добавьте этот модуль.\n\n"
            + "2. Откройте Qidian — справа появится синяя кнопка «译».\n"
            + "   Нажатие — настройки, долгое нажатие — вкл/выкл перевод, перетаскивание — переместить.\n\n"
            + "3. В настройках выберите нейросеть, впишите ключ и нажмите «Проверить».\n\n"
            + "4. Для перевода глав один раз включите «Режим разведки», откройте главу и найдите в логе "
            + "метод, который отдаёт текст главы. Подробно — в README проекта.");
        ScrollView sv = new ScrollView(this);
        sv.addView(t);
        setContentView(sv);
    }
}
