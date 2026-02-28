package com.htlac.hitv.core.player.di

import com.htlac.hitv.core.player.Media3Player
import com.htlac.hitv.core.player.PlayerController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    // @Binds 的意思是：如果有地方要 @Inject PlayerController，请给它注入 Media3Player
    @Binds
    @Singleton
    abstract fun bindPlayerController(
        media3Player: Media3Player
    ): PlayerController
}