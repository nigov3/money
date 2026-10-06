package ru.zenflow.finance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import ru.zenflow.finance.data.local.converters.FinanceConverters
import ru.zenflow.finance.data.local.dao.AccountDao
import ru.zenflow.finance.data.local.dao.BudgetDao
import ru.zenflow.finance.data.local.dao.CategoryDao
import ru.zenflow.finance.data.local.dao.TransactionDao
import ru.zenflow.finance.data.local.entity.AccountEntity
import ru.zenflow.finance.data.local.entity.BudgetEntity
import ru.zenflow.finance.data.local.entity.CategoryEntity
import ru.zenflow.finance.data.local.entity.TransactionEntity

/**
 * База данных приложения.
 *
 * exportSchema=true + schemaLocation заданы в build.gradle.kts — JSON-схемы
 * коммитим, на их базе делаем миграции (fallbackToDestructiveMigration только
 * для debug-сборки, см. DI-модуль).
 */
@Database(
    entities = [
        TransactionEntity::class,
        CategoryEntity::class,
        AccountEntity::class,
        BudgetEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(FinanceConverters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun accountDao(): AccountDao
    abstract fun budgetDao(): BudgetDao

    companion object {
        const val NAME = "zenflow.db"
    }
}
