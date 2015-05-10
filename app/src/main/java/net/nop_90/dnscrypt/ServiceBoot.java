package net.nop_90.dnscrypt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

/**
 * Created by nop-90 on 29/01/15.
 */
public class ServiceBoot extends BroadcastReceiver
{
    private static SharedPreferences prefs;
    private boolean start;

    @Override
    public void onReceive(Context context, Intent intent) {
        prefs = PreferenceManager.getDefaultSharedPreferences(context);

        start = prefs.getBoolean("boot", false);
        if (start) {
            Intent myIntent = new Intent(context, MainActivity.class);
            myIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(myIntent);
        }
    }
}


