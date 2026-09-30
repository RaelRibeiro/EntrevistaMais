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
abstract class EntrevistadorDatabase : RoomDatabase() {

    abstract fun entrevistaDao(): EntrevistaDao
    abstract fun roteiroDao(): RoteiroDao
    abstract fun perguntaDao(): PerguntaDao
    abstract fun respostaDao(): RespostaDao
    abstract fun experienciaDao(): ExperienciaDao
    abstract fun vagaDao(): VagaDao

    companion object {
        private const val NOME = "entrevistador.db"

        @Volatile
        private var instancia: EntrevistadorDatabase? = null

        fun obter(context: Context): EntrevistadorDatabase =
            instancia ?: synchronized(this) {
                instancia ?: criar(context.applicationContext).also { instancia = it }
            }

        private fun criar(context: Context): EntrevistadorDatabase =
            Room.databaseBuilder(context, EntrevistadorDatabase::class.java, NOME)
                .addMigrations(MIGRACAO_1_2, MIGRACAO_2_3, MIGRACAO_3_4)
                .build()

        /**
         * v1 -> v2: currículo virou arquivo/resumo e o roteiro virou formulário.
         * As entrevistas já cadastradas continuam válidas — só ganham campos
         * novos com valor padrão.
         */
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

        /**
         * v2 -> v3: a vaga ganhou empresa, contrato, limite de candidatos e o
         * horário deixou de ser texto livre para virar dois relógios.
         *
         * O `horarioTrabalho` antigo ("08:00 às 17:00") é convertido para
         * entrada/saída quando segue esse formato; vagas fora do padrão
         * continuam com os dois campos vazios e o recrutador escolhe de novo.
         */
        val MIGRACAO_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // O horário deixou de ser texto livre e virou dois campos, então a
                // tabela é recriada: o SQLite não tem DROP COLUMN confiável aqui.
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

                // "08:00 às 17:00" -> entrada 480, saída 1020. Vagas fora desse
                // formato ficam sem horário e o recrutador escolhe de novo.
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

        /**
         * v3 -> v4: horário manual por candidato e roteiro próprio por vaga.
         *
         * As duas mudanças são colunas novas, então basta o ALTER TABLE: o
         * `inicioManual` começa em falso (ou seja, segue o cálculo automático,
         * como sempre foi) e o `vagaId` do roteiro começa nulo (ou seja, o
         * roteiro padrão continua valendo para todas as entrevistas).
         */
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
