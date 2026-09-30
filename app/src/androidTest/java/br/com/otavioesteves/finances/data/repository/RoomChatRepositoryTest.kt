package br.com.otavioesteves.finances.data.repository

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.local.entity.ChatMessageEntity
import br.com.otavioesteves.finances.domain.model.ChatRole
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class RoomChatRepositoryTest {
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

            val messages = RoomChatRepository(database.chatMessageDao()).getRecentMessages(100).first()
            assertEquals(100, messages.size)
            assertEquals("Mensagem 6", messages.first().content)
            assertEquals("Mensagem 105", messages.last().content)
        } finally {
            database.close()
        }
    }
}
