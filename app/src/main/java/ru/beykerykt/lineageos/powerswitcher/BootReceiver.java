package ru.beykerykt.lineageos.powerswitcher;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import static ru.beykerykt.lineageos.powerswitcher.Constants.isServiceEnabled;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (isServiceEnabled(context)) {
            Intent startServiceIntent = new Intent(context, PowerSwitcherService.class);
            context.startForegroundService(startServiceIntent);
        }
    }
}