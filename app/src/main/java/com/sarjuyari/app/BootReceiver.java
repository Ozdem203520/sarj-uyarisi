package com.sarjuyari.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;

/** Telefon yeniden başladığında pil izleme servisini otomatik başlatır. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && Settings.canDrawOverlays(context)) {
            context.startForegroundService(new Intent(context, BatteryService.class));
        }
    }
}
