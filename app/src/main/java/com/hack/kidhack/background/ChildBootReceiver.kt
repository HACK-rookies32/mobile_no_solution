package com.hack.kidhack.background

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.koin.core.context.GlobalContext

class ChildBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return
        runCatching { GlobalContext.get().get<ChildPlatformController>() }
    }
}
