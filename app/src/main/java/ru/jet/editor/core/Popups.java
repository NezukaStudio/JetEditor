package ru.jet.editor.core;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.View;
import android.view.Window;

/** Membuat dialog dan popup tanpa bayangan (elevation). */
public final class Popups {

    private Popups() { }

    public static void flat(Dialog d) {
        if (d != null) flat(d.getWindow());
    }

    /** Window transparan dan tanpa elevation. Sudut dan warna kartu diatur sendiri oleh isi dialog. */
    public static void flat(Window w) {
        if (w == null) return;
        w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        View decor = w.getDecorView();
        decor.setElevation(0f);
    }
}
