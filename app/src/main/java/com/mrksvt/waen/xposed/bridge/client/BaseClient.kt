package com.mrksvt.waen.xposed.bridge.client

import com.mrksvt.waen.xposed.bridge.WaeIIFace

abstract class BaseClient {
    abstract val service: WaeIIFace?

    abstract suspend fun connect(): Boolean

    abstract fun tryReconnect()
}
