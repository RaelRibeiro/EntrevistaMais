package com.example.entrevistador.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.example.entrevistador.data.local.entity.EntrevistaEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

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

    @Query("SELECT COUNT(*) FROM entrevistas WHERE vagaId = :vagaId")
    suspend fun contarPorVaga(vagaId: Long): Int

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
