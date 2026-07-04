package com.comunidapp.app

import android.app.Application

class LeoverApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: LeoverApplication
            private set
    }
}
