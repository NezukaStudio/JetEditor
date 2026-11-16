package ru.jet.editor.core;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

import ru.jet.editor.R;

/**
 * Download satu file tool (SDK / AAPT / Gradle) di foreground service supaya tetap jalan
 * walaupun dialog di-minimize atau activity ditutup. Progres tampil di notifikasi.
 * Hanya satu download dalam satu waktu.
 */
public class ToolDownloadService extends Service {

    public static final String ACTION_START = "ru.jet.editor.action.DOWNLOAD_START";
    public static final String ACTION_CANCEL = "ru.jet.editor.action.DOWNLOAD_CANCEL";
    private static final String EXTRA_NAME = "name";
    private static final String EXTRA_URL = "url";
    private static final String EXTRA_DEST = "dest";

    private static final String CHANNEL_ID = "tool_downloads";
    private static final int NOTIF_ONGOING = 1001;
    private static final int NOTIF_RESULT = 1002;

    public interface Listener {
        void onProgress(long done, long total);
        void onFinished(String name, File file, String error, boolean cancelled);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static volatile Listener listener;
    private static volatile boolean running;
    private static volatile boolean cancelRequested;
    private static volatile String runningName = "";
    private static volatile long doneBytes;
    private static volatile long totalBytes = -1;

    public static void setListener(Listener l) { listener = l; }
    public static boolean isRunning() { return running; }
    public static String getRunningName() { return runningName; }
    public static long getDoneBytes() { return doneBytes; }
    public static long getTotalBytes() { return totalBytes; }

    public static void start(Context c, String name, String url, File dest) {
        Intent i = new Intent(c, ToolDownloadService.class)
                .setAction(ACTION_START)
                .putExtra(EXTRA_NAME, name)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_DEST, dest.getAbsolutePath());
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
        else c.startService(i);
    }

    public static void cancel(Context c) {
        c.startService(new Intent(c, ToolDownloadService.class).setAction(ACTION_CANCEL));
    }

    public static String formatSize(long b) {
        if (b < 0) return "?";
        if (b < 1024) return b + " B";
        if (b < 1024L * 1024) return String.format(Locale.US, "%.1f KB", b / 1024f);
        return String.format(Locale.US, "%.1f MB", b / (1024f * 1024f));
    }

    @Override
    public void onCreate() {
        LanguageLoader.init(getApplicationContext());
        super.onCreate();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(
                    CHANNEL_ID, LanguageLoader.get("notif.channel"), NotificationManager.IMPORTANCE_LOW);
            ch.setDescription(LanguageLoader.get("notif.channel_desc"));
            nm().createNotificationChannel(ch);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (ACTION_CANCEL.equals(action)) {
            cancelRequested = true;
            return START_NOT_STICKY;
        }
        if (!ACTION_START.equals(action)) {
            if (!running) stopSelf();
            return START_NOT_STICKY;
        }

        final String name = intent.getStringExtra(EXTRA_NAME);
        final String url = intent.getStringExtra(EXTRA_URL);
        final File dest = new File(intent.getStringExtra(EXTRA_DEST));

        // Wajib dipanggil cepat setelah startForegroundService.
        startForeground(NOTIF_ONGOING, ongoing(name, 0, -1));
        if (running) return START_NOT_STICKY;

        new Thread(new Runnable() {
            public void run() { download(name, url, dest); }
        }).start();
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void download(String name, String url, File dest) {
        running = true;
        cancelRequested = false;
        runningName = name;
        doneBytes = 0;
        totalBytes = -1;

        File part = new File(dest.getParentFile(), dest.getName() + ".part");
        String error = null;
        boolean cancelled = false;
        boolean ok = false;
        HttpURLConnection conn = null;
        InputStream in = null;
        OutputStream out = null;
        try {
            if (dest.getParentFile() != null) dest.getParentFile().mkdirs();
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setInstanceFollowRedirects(true);
            int code = conn.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);

            totalBytes = conn.getContentLengthLong();
            in = conn.getInputStream();
            out = new FileOutputStream(part);

            byte[] buf = new byte[16 * 1024];
            long lastUi = 0;
            int n;
            while ((n = in.read(buf)) != -1) {
                if (cancelRequested) { cancelled = true; break; }
                out.write(buf, 0, n);
                doneBytes += n;
                long now = System.currentTimeMillis();
                if (now - lastUi >= 400) {
                    lastUi = now;
                    nm().notify(NOTIF_ONGOING, ongoing(name, doneBytes, totalBytes));
                    postProgress(doneBytes, totalBytes);
                }
            }
            out.close();
            out = null;

            if (cancelled) {
                part.delete();
            } else {
                if (dest.exists()) dest.delete();
                if (!part.renameTo(dest)) throw new IOException("Gagal menyimpan file");
                ok = true;
            }
        } catch (Exception e) {
            android.util.Log.e("ToolDownload", "Download gagal: " + url, e);
            error = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            part.delete();
        } finally {
            closeQuietly(out);
            closeQuietly(in);
            if (conn != null) conn.disconnect();
        }

        running = false;
        if (ok) {
            notifyResult(LanguageLoader.get("notif.done"), name);
        } else if (!cancelled) {
            notifyResult(LanguageLoader.get("notif.failed"), name + (error != null ? ": " + error : ""));
        }

        final Listener l = listener;
        final File result = ok ? dest : null;
        final String err = error;
        final boolean wasCancelled = cancelled;
        MAIN.post(new Runnable() {
            public void run() {
                if (l != null) l.onFinished(name, result, err, wasCancelled);
                stopForeground(true);
                stopSelf();
            }
        });
    }

    private void postProgress(final long done, final long total) {
        final Listener l = listener;
        if (l == null) return;
        MAIN.post(new Runnable() {
            public void run() { l.onProgress(done, total); }
        });
    }

    private void notifyResult(String title, String text) {
        Notification n = builder()
                .setSmallIcon(R.drawable.ic_save)
                .setContentTitle(title)
                .setContentText(text)
                .setAutoCancel(true)
                .build();
        nm().notify(NOTIF_RESULT, n);
    }

    private Notification ongoing(String name, long done, long total) {
        int pct = total > 0 ? (int) (done * 100 / total) : 0;
        String text = total > 0
                ? pct + "%  ·  " + formatSize(done) + " / " + formatSize(total)
                : LanguageLoader.get("notif.preparing");
        PendingIntent cancelPi = PendingIntent.getService(this, 0,
                new Intent(this, ToolDownloadService.class).setAction(ACTION_CANCEL),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = builder()
                .setSmallIcon(R.drawable.ic_save)
                .setContentTitle(LanguageLoader.get("notif.downloading", name))
                .setContentText(text)
                .setProgress(100, pct, total <= 0)
                .setOngoing(true)
                .addAction(R.drawable.ic_close, LanguageLoader.get("common.cancel"), cancelPi);
        if (Build.VERSION.SDK_INT >= 26) b.setOnlyAlertOnce(true);
        return b.build();
    }

    private Notification.Builder builder() {
        return Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
    }

    private NotificationManager nm() {
        return (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c == null) return;
        try { c.close(); } catch (IOException ignored) {}
    }
}
