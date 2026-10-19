package com.example.entrevistador.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.entrevistador.data.local.dao.EntrevistaDao
import com.example.entrevistador.data.local.dao.ExperienciaDao
import com.example.entrevistador.data.local.dao.PerguntaDao
import com.example.entrevistador.data.local.dao.RespostaDao
import com.example.entrevistador.data.local.dao.RoteiroDao
import com.example.entrevistador.data.local.dao.VagaDao
import com.example.entrevistador.data.local.entity.EntrevistaEntity
import com.example.entrevistador.data.local.entity.ExperienciaEntity
import com.example.entrevistador.data.local.entity.PerguntaEntity
import com.example.entrevistador.data.local.entity.RespostaEntity
import com.example.entrevistador.data.local.entity.RoteiroEntity
import com.example.entrevistador.data.local.entity.VagaEntity

@Database(
    entities = [
        EntrevistaEntity::class,
        RoteiroEntity::class,
        PerguntaEntity::class,
        RespostaEntity::class,
        ExperienciaEntity::class,
        VagaEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class EntrevistaMaisDatabase : RoomDatabase() {

    abstract fun entrevistaDao(): EntrevistaDao
    abstract fun roteiroDao(): RoteiroDao
    abstract fun perguntaDao(): PerguntaDao
    abstract fun respostaDao(): RespostaDao
    abstract fun experienciaDao(): ExperienciaDao
    abstract fun vagaDao(): VagaDao

    companion object {

        private const val NOME = "entrevistador.db"

        @Volatile
        private var instancia: EntrevistaMaisDatabase? = null

        fun obter(context: Context): EntrevistaMaisDatabase =
            instancia ?: synchronized(this) {
                instancia ?: criar(context.applicationContext).also { instancia = it }
            }

        private fun criar(context: Context): EntrevistaMaisDatabase =
            Room.databaseBuilder(context, EntrevistaMaisDatabase::class.java, NOME)
                .addMigrations(MIGRACAO_1_2, MIGRACAO_2_3, MIGRACAO_3_4)
                .build()

        val MIGRACAO_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE entrevistas ADD COLUMN tipoCurriculo TEXT NOT NULL DEFAULT 'RESUMO'"
                )
                db.execSQL(
                    "ALTER TABLE entrevistas ADD COLUMN caminhoCurriculo TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE entrevistas ADD COLUMN nomeArquivoCurriculo TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL("ALTER TABLE entrevistas ADD COLUMN vagaId INTEGER")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS perguntas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        roteiroId INTEGER NOT NULL,
                        ordem INTEGER NOT NULL,
                        titulo TEXT NOT NULL,
                        tipo TEXT NOT NULL,
                        obrigatoria INTEGER NOT NULL,
                        dica TEXT NOT NULL,
                        respostaAutomatica TEXT
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS respostas (
                        entrevistaId INTEGER NOT NULL,
                        perguntaId INTEGER NOT NULL,
                        texto TEXT NOT NULL,
                        PRIMARY KEY(entrevistaId, perguntaId)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS experiencias (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        entrevistaId INTEGER NOT NULL,
                        ordem INTEGER NOT NULL,
                        local TEXT NOT NULL,
                        ano TEXT NOT NULL,
                        duracao TEXT NOT NULL,
                        cargo TEXT NOT NULL,
                        motivoSaida TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_experiencias_entrevistaId " +
                        "ON experiencias (entrevistaId)"
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS vagas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        titulo TEXT NOT NULL,
                        horarioTrabalho TEXT NOT NULL,
                        salarioBeneficios TEXT NOT NULL,
                        tempoExperiencia TEXT NOT NULL,
                        escolaridade TEXT NOT NULL,
                        exigeHabilitacao TEXT NOT NULL,
                        resumoAtividades TEXT NOT NULL,
                        ativa INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }


        val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {

                db.execSQL(
                    """
                    CREATE TABLE vagas_novo (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        titulo TEXT NOT NULL,
                        empresa TEXT NOT NULL DEFAULT '',
                        entradaMinutos INTEGER NOT NULL DEFAULT -1,
                        saidaMinutos INTEGER NOT NULL DEFAULT -1,
                        tipoContrato TEXT NOT NULL DEFAULT '',
                        salarioBeneficios TEXT NOT NULL,
                        tempoExperiencia TEXT NOT NULL,
                        escolaridade TEXT NOT NULL,
                        exigeHabilitacao TEXT NOT NULL,
                        resumoAtividades TEXT NOT NULL,
                        limiteCandidatos INTEGER NOT NULL DEFAULT 0,
                        ativa INTEGER NOT NULL
                    )
                    """.trimIndent()
                )



                db.query("SELECT id, titulo, horarioTrabalho, salarioBeneficios, tempoExperiencia, escolaridade, exigeHabilitacao, resumoAtividades, ativa FROM vagas")
                    .use { cursor ->
                        while (cursor.moveToNext()) {
                            val horarios = Regex("""(\d{1,2}):(\d{2})""")
                                .findAll(cursor.getString(2).orEmpty())
                                .toList()
                            val entrada = if (horarios.size >= 2) {
                                horasParaMinutos(horarios[0])
                            } else {
                                -1
                            }
                            val saida = if (horarios.size >= 2) {
                                horasParaMinutos(horarios[1])
                            } else {
                                -1
                            }
                            db.execSQL(
                                """
                                INSERT INTO vagas_novo (
                                    id, titulo, empresa, entradaMinutos, saidaMinutos, tipoContrato,
                                    salarioBeneficios, tempoExperiencia, escolaridade,
                                    exigeHabilitacao, resumoAtividades, limiteCandidatos, ativa
                                ) VALUES (?, ?, '', ?, ?, '', ?, ?, ?, ?, ?, 0, ?)
                                """.trimIndent(),
                                arrayOf(
                                    cursor.getLong(0),
                                    cursor.getString(1),
                                    entrada,
                                    saida,
                                    cursor.getString(3),
                                    cursor.getString(4),
                                    cursor.getString(5),
                                    cursor.getString(6),
                                    cursor.getString(7),
                                    cursor.getInt(8),
                                ),
                            )
                        }
                    }

                db.execSQL("DROP TABLE vagas")
                db.execSQL("ALTER TABLE vagas_novo RENAME TO vagas")
            }

            private fun horasParaMinutos(achado: MatchResult): Int =
                (achado.groupValues[1].toInt() * 60) + achado.groupValues[2].toInt()
        }

        val MIGRACAO_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE entrevistas ADD COLUMN inicioManual INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL("ALTER TABLE roteiros ADD COLUMN vagaId INTEGER")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_roteiros_vagaId ON roteiros (vagaId)"
                )
            }
        }
    }
}
