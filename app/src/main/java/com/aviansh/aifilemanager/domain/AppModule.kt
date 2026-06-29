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

    @Provides
    @Singleton
    fun provideGeminiAIProvider(
        @ApplicationContext context: Context
    ): GeminiAIProvider {
        // Get API key from BuildConfig or secure storage
        val apiKey = try {
            context.javaClass.getField("GEMINI_API_KEY")
                .get(null) as? String
                ?: System.getenv("GEMINI_API_KEY")
                ?: throw IllegalStateException("GEMINI_API_KEY not configured")
        } catch (e: Exception) {
            throw IllegalStateException("Failed to load GEMINI_API_KEY", e)
        }

        return GeminiAIProvider(
            apiKey = apiKey,
            modelName = "gemini-3.1-flash-lite",
            maxRetries = 3,
            timeoutMillis = 30000L
        )
    }
}