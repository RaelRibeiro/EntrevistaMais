package com.example.entrevistador.core

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.entrevistador.EntrevistaMaisApplication
import com.example.entrevistador.MainActivity
import com.example.entrevistador.R
import com.example.entrevistador.domain.model.StatusEntrevista
import kotlinx.coroutines.flow.first
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

object Lembretes {

    private const val CANAL = "lembretes-entrevista"
    private const val TRABALHO = "lembretes-entrevista"

    fun agendar(context: Context) {
        val pedido = PeriodicWorkRequestBuilder<LembreteWorker>(15, TimeUnit.MINUTES)
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TRABALHO,
            ExistingPeriodicWorkPolicy.KEEP,
            pedido,
        )
    }

    fun criarCanal(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val gerente = context.getSystemService(NotificationManager::class.java) ?: return
        if (gerente.getNotificationChannel(CANAL) != null) return
        val canal = NotificationChannel(
            CANAL,
            "Lembretes de entrevista",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Avisos pouco antes do horário de cada entrevista."
        }
        gerente.createNotificationChannel(canal)
    }

    fun avisar(context: Context, id: Long, nome: String, horario: String) {
        criarCanal(context)
        val abertura = PendingIntent.getActivity(
            context,
            id.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notificacao = NotificationCompat.Builder(context, CANAL)
            .setSmallIcon(R.drawable.image_app)
            .setContentTitle("Entrevista em instantes")
            .setContentText("$nome às $horario")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(abertura)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(id.toInt(), notificacao)
        }
    }
}

class LembreteWorker(
    context: Context,
    parametros: WorkerParameters,
) : CoroutineWorker(context, parametros) {

    override suspend fun doWork(): Result {
        Lembretes.criarCanal(applicationContext)
        val container = (applicationContext as EntrevistaMaisApplication).container
        val agora = LocalDateTime.now()
        val hoje = LocalDate.now()

        val entrevistas = runCatching {
            container.entrevistaRepository.observarPorData(hoje).first()
        }.getOrDefault(emptyList())

        val memoria = applicationContext.getSharedPreferences("lembretes", Context.MODE_PRIVATE)

        entrevistas
            .filter { it.status == StatusEntrevista.AGENDADA }
            .forEach { entrevista ->
                val inicio = hoje.atTime(entrevista.horaInicio)
                val minutos = Duration.between(agora, inicio).toMinutes()
                if (minutos in 0..15) {
                    val chave = "${entrevista.id}-$hoje"
                    if (!memoria.getBoolean(chave, false)) {
                        Lembretes.avisar(
                            applicationContext,
                            entrevista.id,
                            entrevista.nome,
                            inicio.paraHoraCurta(),
                        )
                        memoria.edit().putBoolean(chave, true).apply()
                    }
                }
            }

        return Result.success()
    }
}

private fun LocalDateTime.paraHoraCurta(): String = "%02d:%02d".format(hour, minute)
