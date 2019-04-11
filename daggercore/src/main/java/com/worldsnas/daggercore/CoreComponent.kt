package com.worldsnas.daggercore

import com.squareup.moshi.Moshi
import com.worldsnas.domain.DomainModule
import com.worldsnas.daggercore.modules.network.NetworkModule
import com.worldsnas.daggercore.scope.AppScope
import dagger.Component
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import javax.inject.Singleton

@Singleton
@AppScope
@Component(modules = [NetworkModule::class, DomainModule::class])
interface CoreComponent {

    fun retrofit() : Retrofit
    fun okHttp() : OkHttpClient
    fun moshi(): Moshi

}