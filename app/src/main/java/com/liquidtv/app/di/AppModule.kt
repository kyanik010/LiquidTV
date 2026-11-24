package com.liquidtv.app.di

import android.content.Context
import androidx.room.Room
import com.liquidtv.app.data.datastore.AddonPreferencesDataStore
import com.liquidtv.app.data.datastore.UserPreferencesDataStore
import com.liquidtv.app.data.local.LiquidTVDatabase
import com.liquidtv.app.data.local.dao.*
import com.liquidtv.app.data.remote.IptvApiService
import com.liquidtv.app.data.repository.ChannelRepositoryImpl
import com.liquidtv.app.data.repository.SubscriptionRepositoryImpl
import com.liquidtv.app.data.security.AndroidKeystoreCredentialCrypto
import com.liquidtv.app.data.security.CredentialCrypto
import com.liquidtv.app.data.security.LogSanitizer
import com.liquidtv.app.domain.repository.AddonPreferencesRepository
import com.liquidtv.app.domain.repository.ChannelRepository
import com.liquidtv.app.domain.repository.SubscriptionRepository
import com.liquidtv.app.domain.repository.UserPreferencesRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): LiquidTVDatabase =
        Room.databaseBuilder(context, LiquidTVDatabase::class.java, "liquid_tv.db")
            .addMigrations(LiquidTVDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideSubscriptionDao(db: LiquidTVDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideChannelDao(db: LiquidTVDatabase): ChannelDao = db.channelDao()

    @Provides
    fun provideSeriesDao(db: LiquidTVDatabase): SeriesDao = db.seriesDao()

    @Provides
    fun provideCategoryDao(db: LiquidTVDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideWatchHistoryDao(db: LiquidTVDatabase): WatchHistoryDao = db.watchHistoryDao()

    @Provides
    fun provideEpgDao(db: LiquidTVDatabase): EpgDao = db.epgDao()
}

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val safeLogger = HttpLoggingInterceptor { message ->
            android.util.Log.d("LiquidHttp", LogSanitizer.sanitizeUrl(message))
        }.apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(safeLogger)
            .addInterceptor { chain ->
                val req = chain.request()
                val builder = req.newBuilder()
                if (req.header("User-Agent") == null) {
                    builder.header("User-Agent", "LiquidTV/1.1 (Android TV)")
                }
                chain.proceed(builder.build())
            }
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl("http://localhost/")
        .client(okHttpClient)
        .addConverterFactory(ScalarsConverterFactory.create())
        .build()

    @Provides
    @Singleton
    fun provideIptvApiService(retrofit: Retrofit): IptvApiService =
        retrofit.create(IptvApiService::class.java)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindCredentialCrypto(impl: AndroidKeystoreCredentialCrypto): CredentialCrypto

    @Binds
    @Singleton
    abstract fun bindSubscriptionRepository(impl: SubscriptionRepositoryImpl): SubscriptionRepository

    @Binds
    @Singleton
    abstract fun bindChannelRepository(impl: ChannelRepositoryImpl): ChannelRepository

    @Binds
    @Singleton
    abstract fun bindUserPreferencesRepository(impl: UserPreferencesDataStore): UserPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindAddonPreferencesRepository(impl: AddonPreferencesDataStore): AddonPreferencesRepository
}
