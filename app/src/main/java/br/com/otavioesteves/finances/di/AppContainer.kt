package br.com.otavioesteves.finances.di

import android.content.Context
import br.com.otavioesteves.finances.data.DefaultDateProvider
import br.com.otavioesteves.finances.data.ai.RuleBasedLocalAiRepository
import br.com.otavioesteves.finances.data.local.AppDatabase
import br.com.otavioesteves.finances.data.repository.RoomCategoriesRepository
import br.com.otavioesteves.finances.data.repository.RoomChatRepository
import br.com.otavioesteves.finances.data.repository.RoomStatementImportRepository
import br.com.otavioesteves.finances.data.repository.RoomTransactionsRepository
import br.com.otavioesteves.finances.data.statement.CsvOfxStatementParser
import br.com.otavioesteves.finances.domain.DateProvider
import br.com.otavioesteves.finances.domain.repository.CategoriesRepository
import br.com.otavioesteves.finances.domain.repository.ChatRepository
import br.com.otavioesteves.finances.domain.repository.LocalAiRepository
import br.com.otavioesteves.finances.domain.repository.StatementImportRepository
import br.com.otavioesteves.finances.domain.repository.StatementParserRepository
import br.com.otavioesteves.finances.domain.repository.TransactionsRepository
import br.com.otavioesteves.finances.domain.usecase.AddTransactionUseCase
import br.com.otavioesteves.finances.domain.usecase.ConfirmStatementImportUseCase
import br.com.otavioesteves.finances.domain.usecase.GetCategorySummariesUseCase
import br.com.otavioesteves.finances.domain.usecase.GetChatHistoryUseCase
import br.com.otavioesteves.finances.domain.usecase.GetMonthlyBalanceUseCase
import br.com.otavioesteves.finances.domain.usecase.GetTransactionsByMonthUseCase
import br.com.otavioesteves.finances.domain.usecase.ImportStatementUseCase
import br.com.otavioesteves.finances.domain.usecase.SendChatMessageUseCase
import br.com.otavioesteves.finances.domain.usecase.SynthesizeStatementUseCase
import kotlinx.coroutines.CoroutineScope

interface AppContainer {
    val categoriesRepository: CategoriesRepository
    val transactionsRepository: TransactionsRepository
    val addTransactionUseCase: AddTransactionUseCase
    val getCategorySummariesUseCase: GetCategorySummariesUseCase
    val getMonthlyBalanceUseCase: GetMonthlyBalanceUseCase
    val getTransactionsByMonthUseCase: GetTransactionsByMonthUseCase
    val getTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.GetTransactionUseCase
    val deleteTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase
    val updateTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.UpdateTransactionUseCase
    val createBackupUseCase: br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase
    val restoreBackupUseCase: br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase
    val dateProvider: DateProvider
    val statementParserRepository: StatementParserRepository
    val localAiRepository: LocalAiRepository
    val chatRepository: ChatRepository
    val statementImportRepository: StatementImportRepository
    val importStatementUseCase: ImportStatementUseCase
    val synthesizeStatementUseCase: SynthesizeStatementUseCase
    val confirmStatementImportUseCase: ConfirmStatementImportUseCase
    val sendChatMessageUseCase: SendChatMessageUseCase
    val getChatHistoryUseCase: GetChatHistoryUseCase
}

class DefaultAppContainer(
    private val context: Context,
    private val applicationScope: CoroutineScope
) : AppContainer {

    override val dateProvider: DateProvider by lazy {
        DefaultDateProvider()
    }
    
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context, applicationScope)
    }

    override val categoriesRepository: CategoriesRepository by lazy {
        RoomCategoriesRepository(database.categoryDao(), database.transactionDao())
    }

    override val transactionsRepository: TransactionsRepository by lazy {
        RoomTransactionsRepository(database.transactionDao())
    }

    override val addTransactionUseCase: AddTransactionUseCase by lazy {
        AddTransactionUseCase(transactionsRepository)
    }

    override val getCategorySummariesUseCase: GetCategorySummariesUseCase by lazy {
        GetCategorySummariesUseCase(categoriesRepository)
    }

    override val getMonthlyBalanceUseCase: GetMonthlyBalanceUseCase by lazy {
        GetMonthlyBalanceUseCase(transactionsRepository)
    }

    override val getTransactionsByMonthUseCase: GetTransactionsByMonthUseCase by lazy {
        GetTransactionsByMonthUseCase(transactionsRepository)
    }

    override val getTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.GetTransactionUseCase by lazy {
        br.com.otavioesteves.finances.domain.usecase.GetTransactionUseCase(transactionsRepository)
    }

    override val deleteTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase by lazy {
        br.com.otavioesteves.finances.domain.usecase.DeleteTransactionUseCase(transactionsRepository)
    }

    override val updateTransactionUseCase: br.com.otavioesteves.finances.domain.usecase.UpdateTransactionUseCase by lazy {
        br.com.otavioesteves.finances.domain.usecase.UpdateTransactionUseCase(transactionsRepository)
    }

    private val backupRepository: br.com.otavioesteves.finances.domain.repository.BackupRepository by lazy {
        br.com.otavioesteves.finances.data.repository.RoomBackupRepository(database)
    }

    override val createBackupUseCase: br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase by lazy {
        br.com.otavioesteves.finances.domain.usecase.CreateBackupUseCase(backupRepository)
    }

    override val restoreBackupUseCase: br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase by lazy {
        br.com.otavioesteves.finances.domain.usecase.RestoreBackupUseCase(backupRepository)
    }

    override val statementParserRepository: StatementParserRepository by lazy {
        CsvOfxStatementParser()
    }

    override val localAiRepository: LocalAiRepository by lazy {
        RuleBasedLocalAiRepository(dateProvider)
    }

    override val chatRepository: ChatRepository by lazy {
        RoomChatRepository(database.chatMessageDao())
    }

    override val statementImportRepository: StatementImportRepository by lazy {
        RoomStatementImportRepository(database.statementImportDao())
    }

    override val importStatementUseCase: ImportStatementUseCase by lazy {
        ImportStatementUseCase(statementParserRepository)
    }

    override val synthesizeStatementUseCase: SynthesizeStatementUseCase by lazy {
        SynthesizeStatementUseCase(localAiRepository, categoriesRepository)
    }

    override val confirmStatementImportUseCase: ConfirmStatementImportUseCase by lazy {
        ConfirmStatementImportUseCase(addTransactionUseCase, statementImportRepository, dateProvider)
    }

    override val sendChatMessageUseCase: SendChatMessageUseCase by lazy {
        SendChatMessageUseCase(
            localAiRepository,
            chatRepository,
            getMonthlyBalanceUseCase,
            getCategorySummariesUseCase,
            dateProvider
        )
    }

    override val getChatHistoryUseCase: GetChatHistoryUseCase by lazy {
        GetChatHistoryUseCase(chatRepository)
    }
}
