package com.f1pocket.app

import android.app.Application
import com.f1pocket.app.data.Http
import com.f1pocket.app.data.Repo
import com.f1pocket.app.data.Tracks
import com.f1pocket.app.notify.Notifier

class F1PocketApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        Repo.init(this)
        Tracks.init(this)
        Notifier.createChannel(this)
        Notifier.schedulePeriodic(this)
    }
}
