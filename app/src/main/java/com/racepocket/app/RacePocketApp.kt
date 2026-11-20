package com.racepocket.app

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import com.racepocket.app.data.Http
import com.racepocket.app.data.Repo
import com.racepocket.app.data.Tracks
import com.racepocket.app.notify.Notifier

class RacePocketApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        Http.init(this)
        Repo.init(this)
        Tracks.init(this)
        Notifier.createChannel(this)
        Notifier.schedulePeriodic(this)
    }

    /** Driver photos stay on the phone and show offline too. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .respectCacheHeaders(false)
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("images")).maxSizeBytes(80L * 1024 * 1024).build() }
        .crossfade(true)
        .build()
}
