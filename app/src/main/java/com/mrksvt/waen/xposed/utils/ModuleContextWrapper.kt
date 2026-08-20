package com.mrksvt.waen.xposed.utils

import android.content.Context
import android.content.res.AssetManager
import android.content.res.Resources
import android.view.ContextThemeWrapper
import com.mrksvt.waen.R
import com.mrksvt.waen.xposed.core.FeatureLoader


class ModuleContextWrapper(private val base: Context) :
    ContextThemeWrapper(base, R.style.AppTheme) {

    override fun getApplicationContext(): Context {
        return base.applicationContext ?: base
    }

    override fun getClassLoader(): ClassLoader {
        return ModuleContextWrapper::class.java.classLoader
            ?: super.getClassLoader()
    }

    override fun getResources(): Resources {
        return FeatureLoader.moduleContext.resources
    }

    override fun getAssets(): AssetManager {
        return FeatureLoader.moduleContext.assets
    }
}
