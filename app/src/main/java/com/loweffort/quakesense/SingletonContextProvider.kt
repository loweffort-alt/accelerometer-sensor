package com.loweffort.quakesense

import android.content.Context
import java.lang.ref.WeakReference

object SingletonContextProvider {
    private var contextRef: WeakReference<Context>? = null

    fun setContext(ctx: Context) {
        contextRef = WeakReference(ctx.applicationContext)
    }

    fun getContext(): Context? {
        return contextRef?.get()
    }
}
