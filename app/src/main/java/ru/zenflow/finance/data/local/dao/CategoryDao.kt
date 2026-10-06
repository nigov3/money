package ru.zenflow.finance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.zenflow.finance.data.local.entity.CategoryEntity

/** DAO категорий + автокатегоризация по merchant-подсказке. */
@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Query("SELECT * FROM categories WHERE parent_id IS NULL ORDER BY name")
    fun observeRootCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    /**
     * Автокатегоризация: ищем категорию, чей match-pattern совпал с названием
     * магазина. REGEXP не встроен в SQLite Android — сравниваем LIKE по первому
     * слову паттерна на стороне Kotlin (см. CategoryMatcher), здесь берём все
     * категории с паттерном одним быстрым запросом.
     */
    @Query("SELECT * FROM categories WHERE match_pattern IS NOT NULL")
    suspend fun getPatternedCategories(): List<CategoryEntity>
}
