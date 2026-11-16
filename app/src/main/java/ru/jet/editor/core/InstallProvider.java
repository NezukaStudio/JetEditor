package ru.jet.editor.core;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.os.ParcelFileDescriptor;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

/**
 * ContentProvider kecil (tanpa androidx) untuk memberi installer bawaan akses baca ke APK hasil build.
 * Hanya APK di dalam folder build JetEditor yang bisa dibuka; exported=false, akses lewat
 * FLAG_GRANT_READ_URI_PERMISSION.
 */
public final class InstallProvider extends ContentProvider {

    public static final String AUTHORITY = "ru.jet.editor.apkprovider";
    static final String SEGMENT = "apk";
    static final String MIME_APK = "application/vnd.android.package-archive";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        return ParcelFileDescriptor.open(fileOf(uri), ParcelFileDescriptor.MODE_READ_ONLY);
    }

    /** Ubah URI menjadi File, lalu pastikan file berada di folder build dan berakhiran .apk. */
    static File fileOf(Uri uri) throws FileNotFoundException {
        List<String> segs = uri.getPathSegments();
        if (segs == null || segs.size() < 2 || !SEGMENT.equals(segs.get(0))) {
            throw new FileNotFoundException("URI tidak dikenal: " + uri);
        }
        StringBuilder path = new StringBuilder();
        for (int i = 1; i < segs.size(); i++) path.append('/').append(segs.get(i));
        try {
            File root = new File(Environment.getExternalStorageDirectory(), ".JetEdit/build").getCanonicalFile();
            File f = new File(path.toString()).getCanonicalFile();
            if (!f.getPath().startsWith(root.getPath() + File.separator)
                    || !f.getName().endsWith(".apk")
                    || !f.isFile()) {
                throw new FileNotFoundException("APK tidak boleh dibuka: " + f.getPath());
            }
            return f;
        } catch (IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public String getType(Uri uri) {
        return MIME_APK;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
