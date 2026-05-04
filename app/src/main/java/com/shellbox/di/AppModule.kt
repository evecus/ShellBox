package com.shellbox.di

import android.content.Context
import androidx.room.Room
import com.shellbox.data.db.ServerDao
import com.shellbox.data.db.ShellBoxDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShellBoxDatabase =
        Room.databaseBuilder(context, ShellBoxDatabase::class.java, "shellbox.db")
            .build()

    @Provides
    fun provideServerDao(db: ShellBoxDatabase): ServerDao = db.serverDao()
}
