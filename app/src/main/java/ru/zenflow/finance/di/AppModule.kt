package ru.zenflow.finance.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ru.zenflow.finance.BuildConfig
import ru.zenflow.finance.core.parser.BankTemplateRegistry
import ru.zenflow.finance.core.parser.BankTemplate
import ru.zenflow.finance.data.local.FinanceDatabase
import ru.zenflow.finance.data.local.dao.AccountDao
import ru.zenflow.finance.data.local.dao.BudgetDao
import ru.zenflow.finance.data.local.dao.CategoryDao
import ru.zenflow.finance.data.local.dao.TransactionDao
import javax.inject.Singleton

/**
 * Hilt-модуль уровня приложения: БД, DAO, реестр шаблонов парсера.
 * Парсер связывается с интерфейсом через @Binds в ParserModule.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): FinanceDatabase =
        Room.databaseBuilder(context, FinanceDatabase::class.java, FinanceDatabase.NAME)
            // В debug разрушительная миграция допустима; в release — только явные Migration(v1->v2...)
            .apply { if (BuildConfig.DEBUG) fallbackToDestructiveMigration() }
            .build()

    @Provides fun provideTransactionDao(db: FinanceDatabase): TransactionDao = db.transactionDao()
    @Provides fun provideCategoryDao(db: FinanceDatabase): CategoryDao = db.categoryDao()
    @Provides fun provideAccountDao(db: FinanceDatabase): AccountDao = db.accountDao()
    @Provides fun provideBudgetDao(db: FinanceDatabase): BudgetDao = db.budgetDao()

    /** Реестр банковских шаблонов как отдельная зависимость — чтобы можно было
     *  переопределить его тестовым списком или (позже) конфигом с сервера. */
    @Provides @Singleton
    fun provideBankTemplates(): List<BankTemplate> = BankTemplateRegistry.templates
}
