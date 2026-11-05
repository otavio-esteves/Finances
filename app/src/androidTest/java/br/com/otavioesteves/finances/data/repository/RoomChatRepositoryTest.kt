package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.ChatMessageEntity
import br.com.otavioesteves.finances.domain.model.ChatRole
import br.com.otavioesteves.finances.domain.model.ChatMessage
import br.com.otavioesteves.finances.domain.model.BackupModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RoomChatRepositoryTest {
    @Test
    fun restoreInvalidatesGuardedWritesFromRequestsUsingThePreviousDataset() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            val repository = RoomChatRepository(database)
            val oldRevision = repository.dataRevision
            val question = ChatMessage(0, ChatRole.USER, "Pergunta antiga", LocalDateTime.of(2026, 1, 15, 10, 0))
            repository.addMessage(question, oldRevision)

            RoomBackupRepository(database).restoreBackup(BackupModel(categories = emptyList(), transactions = emptyList()))
            assertTrue(repository.dataRevision > oldRevision)
            val failure = runCatching {
                repository.addMessage(question.copy(role = ChatRole.ASSISTANT, content = "Resposta antiga"), oldRevision)
            }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertTrue(repository.getMessages().first().isEmpty())

            repository.addMessage(question.copy(content = "Pergunta nova"), repository.dataRevision)
            assertEquals(listOf("Pergunta nova"), repository.getMessages().first().map { it.content })
        } finally {
            database.close()
        }
    }

    @Test
    fun recentMessages_returnsLatestEntriesInChronologicalOrder() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java
        ).build()
        try {
            repeat(105) { index ->
                database.chatMessageDao().insertMessage(
                    ChatMessageEntity(
                        id = 0,
                        role = ChatRole.USER,
                        content = "Mensagem ${index + 1}",
                        createdAt = LocalDateTime.of(2026, 1, 15, 10, 0).plusSeconds(index.toLong())
                    )
                )
            }

            val messages = RoomChatRepository(database).getRecentMessages(100).first()
            assertEquals(100, messages.size)
            assertEquals("Mensagem 6", messages.first().content)
            assertEquals("Mensagem 105", messages.last().content)
        } finally {
            database.close()
        }
    }
}
