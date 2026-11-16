package ru.jet.editor.container;

import ru.jet.editor.R;
import ru.jet.editor.core.IconDrawable;
import ru.jet.editor.core.LanguageLoader;
import ru.jet.editor.core.M3Scroll;
import ru.jet.editor.core.Palette;
import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Map;

import ru.jet.editor.core.BuildNotifier;
import ru.jet.editor.core.DebugBuild;
import ru.jet.editor.core.InoCircularProgress;
import ru.jet.editor.core.InoProgressBar;
import android.widget.FrameLayout;

/**
 * Bottom sheet progres build. Satu baris per tahap dengan ikon M3: lingkaran kosong (menunggu),
 * refresh (berjalan), centang (selesai), atau silang merah (gagal).
 * Di atas tombol Tutup ada progress bar: terisi sesuai jumlah tahap yang selesai.
 * Sheet bisa ditarik sampai layar penuh; saat itu daftar tahap ikut memanjang.
 * onStep bisa dipanggil dari thread latar belakang; UI diperbarui lewat runOnUiThread.
 */
public final class BuildSheet implements DebugBuild.Listener {

    /** Tinggi area log saat sheet belum fullscreen. */
    private static final int LOG_HEIGHT_DP = 260;

    private final Activity act;
    private final BottomSheet.Host host;
    private final String appName;
    private final String label;
    private final Map<String, TextView> rows = new HashMap<String, TextView>();
    /** Ikon ✓ / ✕ per tahap (drawable). */
    private final Map<String, ImageView> marks = new HashMap<String, ImageView>();
    /** Lingkaran progres per tahap: busur statis saat menunggu, berputar saat berjalan. */
    private final Map<String, InoCircularProgress> rings = new HashMap<String, InoCircularProgress>();

    private BottomSheet sheet;
    private TextView title;
    private TextView resultView;
    private ScrollView logScroll;
    private InoProgressBar progress;
    private int doneCount;
    /** Token pembatalan build ini; dipakai tombol Batalkan dan tombol di notifikasi. */
    private final DebugBuild.Cancel cancel = new DebugBuild.Cancel();
    private BuildNotifier notifier;
    private TextView cancelBtn;

    public DebugBuild.Cancel cancelToken() { return cancel; }

    /** label: judul sheet, mis. "Build debug" atau "Build release". */
    public BuildSheet(Activity act, BottomSheet.Host host, String appName, String label) {
        this.act = act;
        this.host = host;
        this.appName = appName;
        this.label = label;
    }

    /** Tampilkan sheet dengan semua tahap dalam keadaan menunggu. Panggil dari UI thread. */
    public void open() {
        sheet = host.create();
        sheet.setExpandable(true);
        LinearLayout body = sheet.body();

        title = BottomSheet.text(act, label, 22, Palette.TEXT, true);
        body.addView(title);
        body.addView(BottomSheet.text(act, appName, 14, Palette.SUB, false));

        LinearLayout list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        for (String step : DebugBuild.STEPS) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, BottomSheet.dp(act, 6), 0, BottomSheet.dp(act, 6));

            FrameLayout holder = new FrameLayout(act);
            InoCircularProgress ring = new InoCircularProgress(act);
            ring.setMax(100);
            ring.setTrackActiveColor(Palette.ACCENT);
            ring.setTrackInactiveColor(Palette.withAlpha(Palette.ACCENT, 0x33));
            holder.addView(ring, new FrameLayout.LayoutParams(BottomSheet.dp(act, 24), BottomSheet.dp(act, 24), Gravity.CENTER));
            ImageView mark = new ImageView(act);
            mark.setScaleType(ImageView.ScaleType.CENTER);
            mark.setVisibility(View.GONE);
            holder.addView(mark, new FrameLayout.LayoutParams(BottomSheet.dp(act, 24), BottomSheet.dp(act, 24), Gravity.CENTER));
            row.addView(holder, new LinearLayout.LayoutParams(BottomSheet.dp(act, 24), BottomSheet.dp(act, 24)));

            TextView t = BottomSheet.text(act, LanguageLoader.get(step), 15, Palette.SUB, false);
            LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            tl.leftMargin = BottomSheet.dp(act, 12);
            row.addView(t, tl);

            rows.put(step, t);
            rings.put(step, ring);
            marks.put(step, mark);
            setPending(step);
            list.addView(row);
        }
        resultView = BottomSheet.text(act, "", 13, Palette.SUB, false);
        resultView.setPadding(0, BottomSheet.dp(act, 10), 0, BottomSheet.dp(act, 10));
        list.addView(resultView);

        logScroll = new ScrollView(act);
        M3Scroll.style(logScroll);
        logScroll.addView(list);
        sheet.setScrollChild(logScroll);
        body.addView(logScroll, logLayout());
        sheet.setGrowView(logScroll, BottomSheet.dp(act, LOG_HEIGHT_DP));

        progress = new InoProgressBar(act);
        progress.setMax(100);
        progress.setTrackActiveColor(Palette.ACCENT);
        progress.setTrackInactiveColor(Palette.withAlpha(Palette.ACCENT, 0x33));
        LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, BottomSheet.dp(act, 6));
        pl.topMargin = BottomSheet.dp(act, 16);
        body.addView(progress, pl);

        // Batalkan di kiri, Tutup di kanan (Tutup hanya menutup sheet; build tetap jalan di latar).
        LinearLayout buttons = new LinearLayout(act);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        cancelBtn = BottomSheet.confirmButton(act, LanguageLoader.get("build.cancel"), new View.OnClickListener() {
            public void onClick(View v) { onCancel(); }
        });
        LinearLayout.LayoutParams cbl = new LinearLayout.LayoutParams(0, BottomSheet.dp(act, 52), 1f);
        cbl.rightMargin = BottomSheet.dp(act, 12);
        buttons.addView(cancelBtn, cbl);

        TextView close = BottomSheet.button(act, LanguageLoader.get("common.close"), true, new View.OnClickListener() {
            public void onClick(View v) {
                sheet.dismiss();
            }
        });
        buttons.addView(close, new LinearLayout.LayoutParams(0, BottomSheet.dp(act, 52), 1.4f));

        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        bl.topMargin = BottomSheet.dp(act, 12);
        body.addView(buttons, bl);

        notifier = new BuildNotifier(act, appName, cancel);
        notifier.start();
        sheet.show();
    }

    /** Batalkan build: proses aapt2 dihentikan dan build berhenti di sela tahap berikutnya. */
    private void onCancel() {
        if (cancel.isCancelled()) return;
        cancel.cancel();
        if (cancelBtn != null) {
            cancelBtn.setEnabled(false);
            cancelBtn.setAlpha(0.5f);
        }
    }

    /** Tinggi tetap; saat layar penuh BottomSheet menambah tinggi area log (lihat setGrowView). */
    private LinearLayout.LayoutParams logLayout() {
        return new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,
                BottomSheet.dp(act, LOG_HEIGHT_DP));
    }

    public void onStep(final String step, final String state, final String info) {
        if (notifier != null) notifier.onStep(step, state);
        act.runOnUiThread(new Runnable() {
            public void run() {
                updateRow(step, state, info == null ? "" : info);
            }
        });
    }

    /** Tampilkan hasil akhir: pesan sukses (path APK) atau error beserta log. */
    public void finish(final boolean ok, final String message) {
        if (notifier != null) {
            if (ok) notifier.finishOk();
            else notifier.finishFailed(message == null ? "" : message.split("\n")[0]);
        }
        act.runOnUiThread(new Runnable() {
            public void run() {
                hideCancel();
                if (title != null) {
                    title.setText(ok ? LanguageLoader.get("build.done") : LanguageLoader.get("build.failed"));
                    title.setTextColor(ok ? Palette.ACCENT : Palette.ERROR);
                }
                if (resultView != null) resultView.setText(message);
                if (progress != null && ok) progress.setProgress(100);
            }
        });
    }

    /** Build dibatalkan pengguna: bukan error, jadi tampil sebagai "dibatalkan". */
    public void finishCancelled() {
        if (notifier != null) notifier.finishCancelled();
        act.runOnUiThread(new Runnable() {
            public void run() {
                hideCancel();
                if (title != null) {
                    title.setText(LanguageLoader.get("build.cancelled"));
                    title.setTextColor(Palette.SUB);
                }
                if (resultView != null) resultView.setText("");
            }
        });
    }

    private void hideCancel() {
        if (cancelBtn != null) cancelBtn.setVisibility(View.GONE);
    }

    private void updateRow(String step, String state, String info) {
        TextView row = rows.get(step);
        InoCircularProgress ring = rings.get(step);
        ImageView mark = marks.get(step);
        if (row == null || ring == null || mark == null) return;
        int total = DebugBuild.STEPS.length;
        if (DebugBuild.RUN.equals(state)) {
            row.setText(LanguageLoader.get(step));
            row.setTextColor(Palette.TEXT);
            showRing(ring, mark, true);
            // tahap yang sedang jalan dihitung setengah jalan
            setPercent((doneCount * 100 + 50) / total);
        } else if (DebugBuild.OK.equals(state)) {
            row.setText(LanguageLoader.get(step) + (info.length() == 0 ? "" : "  ·  " + info));
            row.setTextColor(Palette.ACCENT);
            showMark(ring, mark, R.drawable.ic_check, Palette.ACCENT);
            doneCount++;
            setPercent(doneCount * 100 / total);
        } else {
            row.setText(LanguageLoader.get(step) + (info.length() == 0 ? "" : "\n    " + info));
            row.setTextColor(Palette.ERROR);
            showMark(ring, mark, R.drawable.ic_close, Palette.ERROR);
        }
    }

    /** Tahap menunggu: lingkaran busur statis (tanpa putaran). */
    private void setPending(String step) {
        InoCircularProgress ring = rings.get(step);
        ImageView mark = marks.get(step);
        if (ring == null || mark == null) return;
        ring.setTrackActiveColor(Palette.ACCENT);
        ring.setTrackInactiveColor(Palette.withAlpha(Palette.ACCENT, 0x33));
        ring.setIndeterminate(false);
        ring.setProgress(0);
        ring.setVisibility(View.VISIBLE);
        mark.setVisibility(View.GONE);
    }

    /** Tahap berjalan: lingkaran berputar (indeterminate). */
    private void showRing(InoCircularProgress ring, ImageView mark, boolean spin) {
        ring.setTrackActiveColor(Palette.ACCENT);
        ring.setTrackInactiveColor(Palette.withAlpha(Palette.ACCENT, 0x33));
        ring.setIndeterminate(spin);
        ring.setVisibility(View.VISIBLE);
        mark.setVisibility(View.GONE);
    }

    /** Tahap selesai / gagal: ikon drawable menggantikan lingkaran. */
    private void showMark(InoCircularProgress ring, ImageView mark, int iconRes, int color) {
        ring.setIndeterminate(false);
        ring.setVisibility(View.GONE);
        mark.setImageDrawable(IconDrawable.of(act, iconRes, color, BottomSheet.dp(act, 20)));
        mark.setVisibility(View.VISIBLE);
    }

    private void setPercent(int p) {
        if (progress != null) progress.setProgress(Math.max(0, Math.min(99, p)));
    }
}
