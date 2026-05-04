package com.shellbox

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import java.security.Security

@HiltAndroidApp
class ShellBoxApp : Application() {
    override fun onCreate() {
        // 必须在 super.onCreate() 之前注册，确保所有组件初始化时 SC 已就位
        registerSpongyCastle()
        super.onCreate()
    }

    private fun registerSpongyCastle() {
        try {
            // 移除 Android 内置的残缺版 BC，插入完整的 SpongyCastle
            Security.removeProvider("BC")
            Security.insertProviderAt(
                org.spongycastle.jce.provider.BouncyCastleProvider(), 1
            )
        } catch (_: Exception) {}
    }
}
