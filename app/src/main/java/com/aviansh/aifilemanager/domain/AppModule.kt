package com.aviansh.aifilemanager.domain

import android.content.Context
import com.aviansh.aifilemanager.domain.ai.providers.GeminiAIProvider
import com.aviansh.aifilemanager.domain.repository.FileRepository
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
    fun provideFileRepository(
        @ApplicationContext context: Context
    ): FileRepository {
        return FileRepository(context)
    }

}