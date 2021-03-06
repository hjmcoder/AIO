package com.worldsnas.aio

import android.app.Application
import android.util.Log
import com.crashlytics.android.Crashlytics
import com.crashlytics.android.core.CrashlyticsCore
import com.facebook.common.logging.FLog
import com.facebook.drawee.backends.pipeline.Fresco
import com.facebook.imagepipeline.core.ImagePipelineConfig
import com.squareup.leakcanary.LeakCanary
import com.squareup.leakcanary.RefWatcher
import com.worldsnas.aio.BuildConfig.DEBUG
import com.worldsnas.base.RefWatcherProvider
import com.worldsnas.daggercore.CoreComponent
import com.worldsnas.daggercore.CoreComponentProvider
import com.worldsnas.daggercore.DaggerCoreComponent
import com.worldsnas.daggercore.modules.DatabaseModule
import io.fabric.sdk.android.Fabric

abstract class BaseApp : Application(), CoreComponentProvider, RefWatcherProvider {

    private lateinit var frescoConfig: ImagePipelineConfig
    private lateinit var refWatcher: RefWatcher

    protected val coreComponent by lazy {
        DaggerCoreComponent
            .builder()
            .setApplication(this)
            .setDatabaseModule(DatabaseModule())
            .build() as CoreComponent
    }

    override fun core(): CoreComponent =
        coreComponent

    override fun getRefWatcher(): RefWatcher =
        refWatcher

    override fun onCreate() {
        super.onCreate()
        frescoConfig = coreComponent.frescoConfig()

        refWatcher = LeakCanary.install(this)

        Fresco.initialize(this, frescoConfig)

        FLog.setMinimumLoggingLevel(
            if (DEBUG)
                Log.VERBOSE
            else
                Log.ERROR
        )

        initCrashlytics()
    }

    private fun initCrashlytics() {
        val crashlyticsCore = CrashlyticsCore
            .Builder()
            .disabled(DEBUG)
            .build()

        Fabric.with(
            this,
            Crashlytics.Builder()
                .core(crashlyticsCore)
                .build()
        )
    }
}