package com.example.entrevistador.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.entrevistador.data.local.entity.EntrevistaEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * Contagem de candidatos por vaga, observável.
 *
 * A contagem precisa mudar quando um candidato entra ou sai da vaga, e não só
 * quando a vaga é editada. Por isso vem direto do banco como Flow, agrupada no
 * SQL, em vez de ser consultada uma vez por vaga na interface.
 */
data class ContagemPorVaga(
    val vagaId: Long,
    val quantidade: Int,
)

@Dao
interface EntrevistaDao {

    @Query("SELECT * FROM entrevistas WHERE data = :data ORDER BY ordem ASC")
    fun observarPorData(data: LocalDate): Flow<List<EntrevistaEntity>>

    @Query("SELECT * FROM entrevistas WHERE data = :data ORDER BY ordem ASC")
    suspend fun listarPorData(data: LocalDate): List<EntrevistaEntity>

    @Query("SELECT * FROM entrevistas WHERE id = :id")
    suspend fun buscarPorId(id: Long): EntrevistaEntity?

    @Query("SELECT * FROM entrevistas WHERE data = :data AND status = 'EM_ANDAMENTO' LIMIT 1")
    suspend fun buscarEmAndamento(data: LocalDate): EntrevistaEntity?

    @Query("SELECT COUNT(*) FROM entrevistas WHERE data = :data")
    suspend fun contarPorData(data: LocalDate): Int

    /** Quantos candidatos já estão na vaga, em qualquer dia — para o limite por vaga. */
    @Query("SELECT COUNT(*) FROM entrevistas WHERE vagaId = :vagaId")
    suspend fun contarPorVaga(vagaId: Long): Int

    /** Mesma contagem, mas reemitindo sempre que a tabela de entrevistas muda. */
    @Query(
        """
        SELECT vagaId AS vagaId, COUNT(*) AS quantidade
        FROM entrevistas
        WHERE vagaId IS NOT NULL
        GROUP BY vagaId
        """
    )
    fun observarContagemPorVaga(): Flow<List<ContagemPorVaga>>

    @Query("SELECT * FROM entrevistas WHERE data = :data AND status = 'EM_ANDAMENTO' LIMIT 1")
    fun observarEmAndamento(data: LocalDate): Flow<EntrevistaEntity?>

    @Query("SELECT * FROM entrevistas WHERE id = :id")
    fun observarPorId(id: Long): Flow<EntrevistaEntity?>

    @Insert
    suspend fun inserir(entrevista: EntrevistaEntity): Long

    @Update
    suspend fun atualizar(entrevista: EntrevistaEntity)

    @Update
    suspend fun atualizarTodas(entrevistas: List<EntrevistaEntity>)

    @Query("DELETE FROM entrevistas WHERE id = :id")
    suspend fun excluirPorId(id: Long)
}
