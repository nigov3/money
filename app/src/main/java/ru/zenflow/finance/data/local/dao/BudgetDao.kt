package ru.zenflow.finance.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.zenflow.finance.data.local.converters.FinanceConverters
import ru.zenflow.finance.data.local.entity.BudgetEntity
import androidx.room.TypeConverters

/** DAO бюджетов: spent vs limit считается JOIN'ом прямо в SQL — без второго раунда в Kotlin. */
@Dao
@TypeConverters(FinanceConverters::class)
interface BudgetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(budget: BudgetEntity): Long

    @Update
    suspend fun update(budget: BudgetEntity)

    @Delete
    suspend fun delete(budget: BudgetEntity)

    @Query("SELECT * FROM budgets WHERE isActive = 1")
    fun observeActive(): Flow<List<BudgetEntity>>

    /** Бюджет + фактические траты по категории за период (для progress-баров на экране аналитики). */
    @Query(
        """
        SELECT b.* FROM budgets b
        WHERE b.isActive = 1
          AND (b.category_id IS NULL OR b.category_id IN (:categoryIds))
        """
    )
    fun observeForCategories(categoryIds: List<Long>): Flow<List<BudgetEntity>>
}
