package com.example.entrevistador.data.local

import androidx.room.TypeConverter
import com.example.entrevistador.domain.model.RespostaAutomatica
import com.example.entrevistador.domain.model.StatusEntrevista
import com.example.entrevistador.domain.model.TipoCurriculo
import com.example.entrevistador.domain.model.TipoResposta
import java.time.Instant
import java.time.LocalDate

class Converters {

    @TypeConverter
    fun localDateParaLong(data: LocalDate?): Long? = data?.toEpochDay()

    @TypeConverter
    fun longParaLocalDate(valor: Long?): LocalDate? = valor?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun instantParaLong(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun longParaInstant(valor: Long?): Instant? = valor?.let(Instant::ofEpochMilli)

    @TypeConverter
    fun statusParaString(status: StatusEntrevista?): String? = status?.name

    @TypeConverter
    fun stringParaStatus(valor: String?): StatusEntrevista? = valor?.let(StatusEntrevista::from)

    @TypeConverter
    fun tipoCurriculoParaString(tipo: TipoCurriculo?): String? = tipo?.name

    @TypeConverter
    fun stringParaTipoCurriculo(valor: String?): TipoCurriculo? = valor?.let(TipoCurriculo::from)

    @TypeConverter
    fun tipoRespostaParaString(tipo: TipoResposta?): String? = tipo?.name

    @TypeConverter
    fun stringParaTipoResposta(valor: String?): TipoResposta? =
        valor?.let { nome -> TipoResposta.entries.firstOrNull { it.name == nome } }

    @TypeConverter
    fun automaticaParaString(valor: RespostaAutomatica?): String? = valor?.name

    @TypeConverter
    fun stringParaAutomatica(valor: String?): RespostaAutomatica? =
        valor?.let { nome -> RespostaAutomatica.entries.firstOrNull { it.name == nome } }
}
