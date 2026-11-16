package ru.jet.editor.core;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Menerima tombol Batalkan dari notifikasi build. */
public final class BuildCancelReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent intent) {
        if (BuildNotifier.ACTION_CANCEL.equals(intent.getAction())) BuildNotifier.cancelActive();
    }
}
