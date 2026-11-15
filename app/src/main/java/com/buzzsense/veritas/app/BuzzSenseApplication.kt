package com.buzzsense.veritas.app

import android.app.Application
import android.content.Context
import com.buzzsense.veritas.app.agent.AppContainer

/** Holds the single [AppContainer] for the process (manual dependency injection). */
class BuzzSenseApplication : Application() {
    val container: AppContainer by lazy { AppContainer(this) }
}

val Context.appContainer: AppContainer
    get() = (applicationContext as BuzzSenseApplication).container
