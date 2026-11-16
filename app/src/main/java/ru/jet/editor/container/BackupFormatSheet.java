package ru.jet.editor.container;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.text.Editable;
import android.text.TextWatcher;
import android.widget.LinearLayout;
import android.widget.TextView;

import ru.jet.editor.core.AppPrefs;
import ru.jet.editor.core.BackupName;
import ru.jet.editor.core.InoInputLayout;
import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.Palette;

/**
 * Bottom sheet "Format nama file backup". Gaya sama dengan sheet lain (BottomSheet),
 * dengan pratinjau nama file dan tombol Reset / Batal / Simpan.
 */
public final class BackupFormatSheet {

    /** Dipanggil saat user menekan Simpan dengan format yang valid. */
    public interface OnSave {
        void onSave(String format);
    }

    private final Activity act;
    private final BottomSheet.Host host;
    private final String current;
    private final OnSave onSave;

    public BackupFormatSheet(Activity act, BottomSheet.Host host, String current, OnSave onSave) {
        this.act = act;
        this.host = host;
        this.current = current;
        this.onSave = onSave;
    }

    public void show() {
        final BottomSheet s = host.create();
        LinearLayout body = s.body();

        body.addView(BottomSheet.text(act, LanguageLoader.get("backup_format.title"), 22, Palette.TEXT, true));

        TextView desc = BottomSheet.text(act, LanguageLoader.get("backup_format.desc"), 14, Palette.SUB, false);
        LinearLayout.LayoutParams dl = BottomSheet.wrap();
        dl.topMargin = BottomSheet.dp(act, 4);
        dl.bottomMargin = BottomSheet.dp(act, 16);
        body.addView(desc, dl);

        final InoInputLayout field = BottomSheet.formInput(act, LanguageLoader.get("backup_format.hint"), current);
        body.addView(field, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        final TextView preview = BottomSheet.text(act, "", 13, Palette.SUB, false);
        LinearLayout.LayoutParams pl = BottomSheet.wrap();
        pl.topMargin = BottomSheet.dp(act, 12);
        body.addView(preview, pl);

        final TextView error = BottomSheet.text(act, "", 14, Palette.ERROR, false);
        error.setVisibility(View.GONE);
        LinearLayout.LayoutParams el = BottomSheet.wrap();
        el.topMargin = BottomSheet.dp(act, 8);
        body.addView(error, el);

        field.getEditText().addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence x, int a, int b, int c) {}
            public void onTextChanged(CharSequence x, int a, int b, int c) {}
            public void afterTextChanged(Editable e) {
                error.setVisibility(View.GONE);
                updatePreview(preview, field.getText());
            }
        });
        updatePreview(preview, current);

        LinearLayout buttons = new LinearLayout(act);
        buttons.setOrientation(LinearLayout.HORIZONTAL);

        // Reset: tombol teks kecil di kiri (lebarnya sesuai teks, tidak terpotong).
        TextView reset = BottomSheet.button(act, LanguageLoader.get("backup_format.reset"), false, new View.OnClickListener() {
            public void onClick(View v) { field.setText(AppPrefs.DEFAULT_BACKUP_FORMAT); }
        });
        reset.setPadding(BottomSheet.dp(act, 12), 0, BottomSheet.dp(act, 12), 0);
        buttons.addView(reset, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, BottomSheet.dp(act, 52)));

        // Batal | Simpan: dua tombol lebar sama besar, Simpan sedikit lebih lebar (utama).
        TextView cancel = BottomSheet.confirmButton(act, LanguageLoader.get("common.cancel"), new View.OnClickListener() {
            public void onClick(View v) { s.dismiss(); }
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, BottomSheet.dp(act, 52), 1f);
        cl.leftMargin = BottomSheet.dp(act, 12);
        cl.rightMargin = BottomSheet.dp(act, 12);
        buttons.addView(cancel, cl);

        TextView save = BottomSheet.button(act, LanguageLoader.get("common.save"), true, new View.OnClickListener() {
            public void onClick(View v) {
                String f = field.getText().trim();
                if (f.isEmpty()) {
                    error.setText(LanguageLoader.get("backup_format.empty"));
                    error.setVisibility(View.VISIBLE);
                    return;
                }
                onSave.onSave(f);
                s.dismiss();
            }
        });
        buttons.addView(save, new LinearLayout.LayoutParams(0, BottomSheet.dp(act, 52), 1.2f));

        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bl.topMargin = BottomSheet.dp(act, 20);
        body.addView(buttons, bl);

        s.show();
    }

    /** Pratinjau memakai contoh tetap, supaya hasilnya mudah dibaca dan tidak tergantung proyek. */
    private void updatePreview(TextView preview, String format) {
        String sample = BackupName.fileName(format, "MyApp", "1.0", "1", "com.example.app",
                System.currentTimeMillis());
        preview.setText(LanguageLoader.get("backup_format.preview", sample));
    }
}
