package com.momobook

import android.app.Application
import com.momobook.data.Ledger

class MomoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Ledger.init(this)
    }
}
