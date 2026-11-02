package com.cockpit406.diagnostic;

import android.content.*;

/** Restart only the owner-enabled update watcher after boot or this package's replacement. */
public final class UpdateRestartReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){
        if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())||Intent.ACTION_MY_PACKAGE_REPLACED.equals(intent.getAction())){RemoteUpdatesService.startIfEnabled(context);TripBackups.start(context);}
    }
}
