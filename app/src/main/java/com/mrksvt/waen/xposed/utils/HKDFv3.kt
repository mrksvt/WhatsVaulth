package com.mrksvt.waen.xposed.utils

class HKDFv3 : HKDF() {
    override val iterationStartOffset: Int
        get() = 1
}
