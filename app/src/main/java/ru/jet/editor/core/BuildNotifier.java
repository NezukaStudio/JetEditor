package ru.jet.editor.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import ru.jet.editor.R;

/**
 * Notifikasi progres build: judul "Membangun {app}", progres bar, dan tombol Batalkan.
 * Setelah selesai, notifikasi berubah menjadi hasil (selesai / gagal / dibatalkan).
 * Dibuat per build; panggilan dari thread build aman (NotificationManager thread-safe).
 */
public final class BuildNotifier {

    public static final int NOTIFICATION_ID = 4101;
    public static final String CHANNEL_ID = "jet_build";
    static final String ACTION_CANCEL = "ru.jet.editor.BUILD_CANCEL";

    /** Build yang sedang berjalan, dipakai tombol Batalkan di notifikasi. */
    private static volatile DebugBuild.Cancel active;

    private final Context app;
    private final String appName;
    private final DebugBuild.Cancel cancel;
    private final NotificationManager nm;
    private final int total = DebugBuild.STEPS.length;
    private int doneCount;

    public BuildNotifier(Context c, String appName, DebugBuild.Cancel cancel) {
        this.app = c.getApplicationContext();
        this.appName = appName;
        this.cancel = cancel;
        this.nm = (NotificationManager) app.getSystemService(Context.NOTIFICATION_SERVICE);
        ensureChannel();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < 26 || nm == null) return;
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel ch = new NotificationChannel(CHANNEL_ID,
                LanguageLoader.get("notif.channel"), NotificationManager.IMPORTANCE_LOW);
        ch.setShowBadge(false);
        nm.createNotificationChannel(ch);
    }

    /** Tampilkan notifikasi awal (0%). */
    public void start() {
        active = cancel;
        post(builder(LanguageLoader.get("notif.building", appName), "")
                .setProgress(100, 0, false)
                .setOngoing(true)
                .addAction(0, LanguageLoader.get("notif.cancel"), cancelIntent())
                .build());
    }

    /** Dipanggil setiap tahap berubah (state = DebugBuild.RUN / OK / FAIL). */
    public void onStep(String step, String state) {
        if (DebugBuild.RUN.equals(state)) {
            update(LanguageLoader.get(step), (doneCount * 100 + 50) / total);
        } else if (DebugBuild.OK.equals(state)) {
            doneCount++;
            update(LanguageLoader.get(step), doneCount * 100 / total);
        }
    }

    private void update(String text, int percent) {
        post(builder(LanguageLoader.get("notif.building", appName), text)
                .setProgress(100, Math.max(0, Math.min(99, percent)), false)
                .setOngoing(true)
                .addAction(0, LanguageLoader.get("notif.cancel"), cancelIntent())
                .build());
    }

    public void finishOk() {
        end(LanguageLoader.get("notif.done", appName), "");
    }

    public void finishFailed(String detail) {
        end(LanguageLoader.get("notif.failed", appName), detail == null ? "" : detail);
    }

    public void finishCancelled() {
        end(LanguageLoader.get("notif.cancelled", appName), "");
    }

    private void end(String title, String text) {
        if (active == cancel) active = null;
        // Build sudah selesai: notifikasi progres dihapus.
        if (nm != null) {
            try { nm.cancel(NOTIFICATION_ID); } catch (SecurityException ignored) { }
        }
    }

    private Notification.Builder builder(String title, String text) {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(app, CHANNEL_ID)
                : new Notification.Builder(app);
        b.setSmallIcon(R.drawable.ic_code)
                .setContentTitle(title)
                .setContentText(text)
                .setShowWhen(false);
        if (Build.VERSION.SDK_INT >= 26) b.setOnlyAlertOnce(true);   // API 26+ saja
        return b;
    }

    private PendingIntent cancelIntent() {
        Intent i = new Intent(app, BuildCancelReceiver.class).setAction(ACTION_CANCEL);
        return PendingIntent.getBroadcast(app, NOTIFICATION_ID, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void post(Notification n) {
        if (nm == null) return;
        try {
            nm.notify(NOTIFICATION_ID, n);
        } catch (SecurityException ignored) {
            // izin notifikasi (Android 13+) belum diberikan: build tetap jalan tanpa notifikasi
        }
    }

    /** Dipanggil dari BuildCancelReceiver saat tombol Batalkan di notifikasi ditekan. */
    static void cancelActive() {
        DebugBuild.Cancel c = active;
        if (c != null) c.cancel();
    }
}
