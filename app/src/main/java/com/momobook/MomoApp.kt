package com.momobook

import android.app.Application
import com.momobook.data.Ledger
import com.momobook.security.AppLock

class MomoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Ledger.init(this)
        AppLock.init(this)
    }
}
