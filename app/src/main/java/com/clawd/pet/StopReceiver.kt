package com.clawd.pet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class StopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        context.stopService(Intent(context, OverlayService::class.java))
    }
}
