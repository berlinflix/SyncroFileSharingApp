package com.syncro.app

import android.app.Application

class SyncroApp : Application() {
    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        graph = AppGraph(this)
    }
}

val android.content.Context.graph: AppGraph
    get() = (applicationContext as SyncroApp).graph
