package com.example.entrevistador

import android.app.Application
import com.example.entrevistador.di.AppContainer
import com.example.entrevistador.di.DefaultAppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class EntrevistadorApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
        scope.launch { container.roteiroRepository.garantirPadrao() }
    }
}
