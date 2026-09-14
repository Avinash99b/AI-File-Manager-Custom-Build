package com.aviansh.aifilemanager.domain

import android.content.Context
import com.aviansh.aifilemanager.domain.repository.FileRepository
import com.aviansh.aifilemanager.domain.security.AndroidKeystoreSecretStore
import com.aviansh.aifilemanager.domain.security.SecretStore
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
    fun provideSecretStore(
        @ApplicationContext context: Context
    ): SecretStore {
        return AndroidKeystoreSecretStore(context)
    }
}
