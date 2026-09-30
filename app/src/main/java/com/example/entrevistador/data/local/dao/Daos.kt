package com.example.entrevistador.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.entrevistador.data.local.entity.ExperienciaEntity
import com.example.entrevistador.data.local.entity.PerguntaEntity
import com.example.entrevistador.data.local.entity.RespostaEntity
import com.example.entrevistador.data.local.entity.RoteiroEntity
import com.example.entrevistador.data.local.entity.VagaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PerguntaDao {

    @Query("SELECT * FROM perguntas ORDER BY roteiroId ASC, ordem ASC")
    fun observarTodos(): Flow<List<PerguntaEntity>>

    @Query("SELECT * FROM perguntas WHERE roteiroId = :roteiroId ORDER BY ordem ASC")
    fun observarDoRoteiro(roteiroId: Long): Flow<List<PerguntaEntity>>

    @Query("SELECT * FROM perguntas WHERE roteiroId = :roteiroId ORDER BY ordem ASC")
    suspend fun listarDoRoteiro(roteiroId: Long): List<PerguntaEntity>

    @Query("SELECT * FROM perguntas WHERE id = :id")
    suspend fun buscarPorId(id: Long): PerguntaEntity?

    @Insert
    suspend fun inserir(pergunta: PerguntaEntity): Long

    @Insert
    suspend fun inserirTodas(perguntas: List<PerguntaEntity>)

    @Update
    suspend fun atualizar(pergunta: PerguntaEntity)

    @Delete
    suspend fun excluir(pergunta: PerguntaEntity)

    @Query("DELETE FROM perguntas WHERE roteiroId = :roteiroId")
    suspend fun excluirDoRoteiro(roteiroId: Long)
}

@Dao
interface RespostaDao {

    @Query("SELECT * FROM respostas WHERE entrevistaId = :entrevistaId")
    fun observarDaEntrevista(entrevistaId: Long): Flow<List<RespostaEntity>>

    @Query("SELECT * FROM respostas WHERE entrevistaId = :entrevistaId")
    suspend fun listarDaEntrevista(entrevistaId: Long): List<RespostaEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvar(resposta: RespostaEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun salvarTodas(respostas: List<RespostaEntity>)

    @Query("DELETE FROM respostas WHERE entrevistaId = :entrevistaId")
    suspend fun excluirDaEntrevista(entrevistaId: Long)
}

@Dao
interface ExperienciaDao {

    @Query("SELECT * FROM experiencias WHERE entrevistaId = :entrevistaId ORDER BY ordem ASC")
    fun observarDaEntrevista(entrevistaId: Long): Flow<List<ExperienciaEntity>>

    @Query("SELECT * FROM experiencias WHERE entrevistaId = :entrevistaId ORDER BY ordem ASC")
    suspend fun listarDaEntrevista(entrevistaId: Long): List<ExperienciaEntity>

    @Insert
    suspend fun inserir(experiencia: ExperienciaEntity): Long

    @Update
    suspend fun atualizar(experiencia: ExperienciaEntity)

    @Delete
    suspend fun excluir(experiencia: ExperienciaEntity)

    @Query("DELETE FROM experiencias WHERE entrevistaId = :entrevistaId")
    suspend fun excluirDaEntrevista(entrevistaId: Long)
}

@Dao
interface VagaDao {

    @Query("SELECT * FROM vagas ORDER BY ativa DESC, titulo ASC")
    fun observarTodas(): Flow<List<VagaEntity>>

    @Query("SELECT * FROM vagas WHERE id = :id")
    suspend fun buscarPorId(id: Long): VagaEntity?

    @Query("SELECT COUNT(*) FROM vagas")
    suspend fun contar(): Int

    @Insert
    suspend fun inserir(vaga: VagaEntity): Long

    @Update
    suspend fun atualizar(vaga: VagaEntity)

    @Query("DELETE FROM vagas WHERE id = :id")
    suspend fun excluirPorId(id: Long)
}

@Dao
interface RoteiroDao {

    /** Só os roteiros do processo; os de cada vaga têm busca própria. */
    @Query("SELECT * FROM roteiros WHERE vagaId IS NULL ORDER BY ordem ASC, id ASC")
    fun observarTodos(): Flow<List<RoteiroEntity>>

    @Query("SELECT * FROM roteiros WHERE padrao = 1 ORDER BY ordem ASC, id ASC LIMIT 1")
    fun observarPadrao(): Flow<RoteiroEntity?>

    @Query("SELECT * FROM roteiros WHERE padrao = 1 ORDER BY ordem ASC, id ASC LIMIT 1")
    suspend fun buscarPadrao(): RoteiroEntity?

    /** Roteiro próprio da vaga, se existir. Nulo = ela ainda usa o padrão. */
    @Query("SELECT * FROM roteiros WHERE vagaId = :vagaId ORDER BY id ASC LIMIT 1")
    fun observarDaVaga(vagaId: Long): Flow<RoteiroEntity?>

    @Query("SELECT * FROM roteiros WHERE vagaId = :vagaId ORDER BY id ASC LIMIT 1")
    suspend fun buscarDaVaga(vagaId: Long): RoteiroEntity?

    @Query("SELECT COUNT(*) FROM roteiros")
    suspend fun contar(): Int

    @Insert
    suspend fun inserir(roteiro: RoteiroEntity): Long

    @Update
    suspend fun atualizar(roteiro: RoteiroEntity)

    @Query("DELETE FROM roteiros WHERE id = :id")
    suspend fun excluirPorId(id: Long)
}
