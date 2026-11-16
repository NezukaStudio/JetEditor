package ru.jet.editor.container;

import android.app.Activity;

import ru.jet.editor.R;
import ru.jet.editor.core.AppPrefs;
import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.PreviewSurface;

/** Bottom sheet "Size display": memilih ukuran layar HP untuk preview layout. */
public final class PreviewSizeSheet {

    private static final String[] KEYS = {
            "preview.size_small", "preview.size_standard", "preview.size_large", "preview.size_tablet"};

    private PreviewSizeSheet() { }

    public static void show(final Activity act, BottomSheet.Host host, final Runnable onChange) {
        int cur = AppPrefs.previewSize(act);
        BottomSheet.Menu menu = host.menu(LanguageLoader.get("preview.size_title"),
                LanguageLoader.get("preview.size_desc"));
        for (int i = 0; i < KEYS.length; i++) {
            final int idx = i;
            String label = (i == cur ? "✓  " : "    ") + LanguageLoader.get(KEYS[i])
                    + "  ·  " + PreviewSurface.SIZE_W[i] + "×" + PreviewSurface.SIZE_H[i] + " dp";
            menu.item(R.drawable.ic_image, label, new Runnable() {
                public void run() {
                    AppPrefs.setPreviewSize(act, idx);
                    onChange.run();
                }
            });
        }
        menu.show();
    }
}
