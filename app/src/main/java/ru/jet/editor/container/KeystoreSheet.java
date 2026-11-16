package ru.jet.editor.container;

import ru.jet.editor.core.M3;
import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.M3Scroll;
import ru.jet.editor.core.Palette;
import android.app.Activity;
import android.os.Build;
import android.os.Environment;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Locale;

import ru.jet.editor.core.InoInputLayout;
import ru.jet.editor.core.InoProgressBar;
import ru.jet.editor.core.KeystoreMaker;
import ru.jet.editor.core.ToolPaths;

/**
 * Bottom sheet "Create new certificate": membuat keystore PKCS12 baru.
 * Hasil disimpan ke /sdcard/.JetEdit/keys/{alias}/keystore.jks.
 * Pembuatan kunci berjalan di thread latar belakang; tombol Create dikunci selama itu.
 */
public final class KeystoreSheet {

    /** Tinggi area form saat sheet belum fullscreen. */
    private static final int FORM_HEIGHT_DP = 360;

    private final Activity act;
    private final BottomSheet.Host host;

    private BottomSheet sheet;
    private InoInputLayout alias, pass, pass2, validity, cn, ou, org, city, state, country;
    private TextView pathView, errorView, createBtn;
    private InoProgressBar busy;
    private ScrollView formScroll;

    public KeystoreSheet(Activity act, BottomSheet.Host host) {
        this.act = act;
        this.host = host;
    }

    public void show() {
        sheet = host.create();
        sheet.setExpandable(true);
        LinearLayout body = sheet.body();

        body.addView(BottomSheet.text(act, LanguageLoader.get("keystore.create_title"), 22, Palette.TEXT, true));
        pathView = BottomSheet.text(act, "", 13, Palette.SUB, false);
        LinearLayout.LayoutParams pl = wrap();
        pl.topMargin = BottomSheet.dp(act, 4);
        pl.bottomMargin = BottomSheet.dp(act, 12);
        body.addView(pathView, pl);

        LinearLayout form = new LinearLayout(act);
        form.setOrientation(LinearLayout.VERTICAL);

        section(form, LanguageLoader.get("keystore.credentials"), 0);
        alias = field(form, LanguageLoader.get("keystore.cert_name"), "");
        pass = password(form, LanguageLoader.get("keystore.new_password"));
        pass2 = password(form, LanguageLoader.get("keystore.confirm_password"));
        validity = field(form, LanguageLoader.get("keystore.validity"), "25");
        validity.setInputType(InputType.TYPE_CLASS_NUMBER);

        section(form, LanguageLoader.get("keystore.certificate"), 20);
        cn = field(form, LanguageLoader.get("keystore.cn"), "");
        InoInputLayout[] r1 = row(form, LanguageLoader.get("keystore.ou"), LanguageLoader.get("keystore.org"));
        ou = r1[0];
        org = r1[1];
        InoInputLayout[] r2 = row(form, LanguageLoader.get("keystore.city"), LanguageLoader.get("keystore.state"));
        city = r2[0];
        state = r2[1];
        country = field(form, LanguageLoader.get("keystore.country"), "");
        country.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        country.getEditText().setFilters(new InputFilter[]{new InputFilter.LengthFilter(2)});

        formScroll = new ScrollView(act);
        M3Scroll.style(formScroll);
        formScroll.addView(form);
        sheet.setScrollChild(formScroll);
        body.addView(formScroll, formLayout());
        sheet.setGrowView(formScroll, BottomSheet.dp(act, FORM_HEIGHT_DP));

        errorView = BottomSheet.text(act, "", 14, Palette.ERROR, false);
        errorView.setVisibility(View.GONE);
        LinearLayout.LayoutParams el = wrap();
        el.topMargin = BottomSheet.dp(act, 10);
        body.addView(errorView, el);

        busy = new InoProgressBar(act);
        busy.setMax(100);
        busy.setTrackActiveColor(Palette.ACCENT);
        busy.setTrackInactiveColor(Palette.withAlpha(Palette.ACCENT, 0x33));
        busy.setVisibility(View.GONE);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, BottomSheet.dp(act, 6));
        bl.topMargin = BottomSheet.dp(act, 12);
        body.addView(busy, bl);

        LinearLayout buttons = new LinearLayout(act);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView cancel = BottomSheet.confirmButton(act, LanguageLoader.get("common.cancel"), new View.OnClickListener() {
            public void onClick(View v) { sheet.dismiss(); }
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, BottomSheet.dp(act, 52), 1f);
        cl.rightMargin = BottomSheet.dp(act, 12);
        buttons.addView(cancel, cl);

        createBtn = BottomSheet.button(act, LanguageLoader.get("common.create"), true, new View.OnClickListener() {
            public void onClick(View v) { submit(); }
        });
        buttons.addView(createBtn, new LinearLayout.LayoutParams(0, BottomSheet.dp(act, 52), 1.4f));

        LinearLayout.LayoutParams bt = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bt.topMargin = BottomSheet.dp(act, 20);
        body.addView(buttons, bt);

        alias.getEditText().addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable e) { updatePath(); }
        });
        updatePath();
        sheet.show();
    }

    // ---------- layout helpers ----------

    /** Tinggi tetap; saat layar penuh BottomSheet yang menambah tingginya (lihat setGrowView). */
    private LinearLayout.LayoutParams formLayout() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                BottomSheet.dp(act, FORM_HEIGHT_DP));
    }

    private void section(LinearLayout col, String text, int topDp) {
        TextView t = BottomSheet.text(act, text, 14, Palette.ACCENT, true);   // header seksi: aksen M3
        LinearLayout.LayoutParams lp = wrap();
        lp.topMargin = BottomSheet.dp(act, topDp);
        lp.bottomMargin = BottomSheet.dp(act, 8);
        col.addView(t, lp);
    }

    private InoInputLayout field(LinearLayout col, String hint, String text) {
        InoInputLayout e = input(hint, text);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = BottomSheet.dp(act, 12);
        col.addView(e, lp);
        return e;
    }

    /** Satu field InoInputLayout satu baris, warna sesuai tema sheet. */
    private InoInputLayout input(String label, String text) {
        InoInputLayout f = new InoInputLayout(act);
        f.setLabelText(label);
        f.setIsMultiline(false);
        f.setColors(Palette.ACCENT, Palette.OUTLINE);
        f.setTextColor(Palette.TEXT);
        f.setTextSize(16);
        f.setCornerRadius(16);
        f.setVerticalMargins(8, 0);
        f.setText(text == null ? "" : text);
        return f;
    }

    private InoInputLayout password(LinearLayout col, String hint) {
        InoInputLayout e = field(col, hint, "");
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        return e;
    }

    private InoInputLayout[] row(LinearLayout col, String hintA, String hintB) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        InoInputLayout a = input(hintA, "");
        InoInputLayout b = input(hintB, "");
        LinearLayout.LayoutParams la = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        la.rightMargin = BottomSheet.dp(act, 6);
        LinearLayout.LayoutParams lb = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lb.leftMargin = BottomSheet.dp(act, 6);
        r.addView(a, la);
        r.addView(b, lb);
        LinearLayout.LayoutParams lr = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lr.bottomMargin = BottomSheet.dp(act, 12);
        col.addView(r, lr);
        return new InoInputLayout[]{a, b};
    }

    private static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private void updatePath() {
        String n = text(alias).toLowerCase(Locale.ROOT);
        pathView.setText(LanguageLoader.get("keystore.export_path", n.isEmpty() ? "<alias>" : n));
    }

    private void showError(String msg) {
        errorView.setText(msg);
        errorView.setVisibility(View.VISIBLE);
    }

    private void setBusy(boolean on) {
        busy.setVisibility(on ? View.VISIBLE : View.GONE);
        busy.setIndeterminate(on);
        createBtn.setEnabled(!on);
        createBtn.setAlpha(on ? 0.5f : 1f);
        errorView.setVisibility(View.GONE);
    }

    private static String text(InoInputLayout e) {
        return e.getText().trim();
    }

    // ---------- submit ----------

    private void submit() {
        String name = text(alias).toLowerCase(Locale.ROOT);
        String p1 = pass.getText().toString();
        String p2 = pass2.getText().toString();
        String yearsText = text(validity);
        String cnValue = text(cn);
        String countryValue = text(country).toUpperCase(Locale.ROOT);

        if (!name.matches("[a-z0-9_-]{3,32}")) {
            showError(LanguageLoader.get("keystore.err_alias"));
            return;
        }
        if (p1.length() < 6) {
            showError(LanguageLoader.get("keystore.err_password_short"));
            return;
        }
        if (!p1.equals(p2)) {
            showError(LanguageLoader.get("keystore.err_confirm"));
            return;
        }
        int years;
        try {
            years = Integer.parseInt(yearsText);
        } catch (NumberFormatException e) {
            years = 0;
        }
        if (years < 1 || years > 100) {
            showError(LanguageLoader.get("keystore.err_validity"));
            return;
        }
        if (cnValue.isEmpty()) {
            showError(LanguageLoader.get("keystore.err_cn"));
            return;
        }
        if (countryValue.length() > 0 && !countryValue.matches("[A-Z]{2}")) {
            showError(LanguageLoader.get("keystore.err_country"));
            return;
        }
        if (Build.VERSION.SDK_INT >= 30 && !Environment.isExternalStorageManager()) {
            showError(LanguageLoader.get("keystore.err_storage"));
            return;
        }

        final File out = new File(ToolPaths.keysRoot(), name + "/keystore.jks");
        if (out.exists()) {
            showError(LanguageLoader.get("keystore.err_exists"));
            return;
        }

        final KeystoreMaker.Subject subject = new KeystoreMaker.Subject();
        subject.cn = cnValue;
        subject.ou = text(ou);
        subject.o = text(org);
        subject.l = text(city);
        subject.st = text(state);
        subject.c = countryValue;

        final char[] password = p1.toCharArray();
        final int validityYears = years;
        final String aliasName = name;

        setBusy(true);
        new Thread(new Runnable() {
            public void run() {
                try {
                    KeystoreMaker.create(out, aliasName, password, validityYears, subject);
                    act.runOnUiThread(new Runnable() {
                        public void run() {
                            setBusy(false);
                            M3.toast(act, LanguageLoader.get("keystore.created", out.getAbsolutePath()),
                                    Toast.LENGTH_LONG);
                            sheet.dismiss();
                        }
                    });
                } catch (final Exception e) {
                    act.runOnUiThread(new Runnable() {
                        public void run() {
                            setBusy(false);
                            showError(LanguageLoader.get("keystore.failed", e.getMessage()));
                        }
                    });
                }
            }
        }).start();
    }
}
