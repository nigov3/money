package ru.zenflow.finance.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.zenflow.finance.data.local.entity.AccountEntity

@Dao
interface AccountDao {

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Query("SELECT * FROM accounts WHERE is_archived = 0 ORDER BY created_at")
    fun observeActiveAccounts(): Flow<List<AccountEntity>>

    /** Поиск «родного» счёта для банка-источника уведомления (привязка по пакету). */
    @Query("SELECT * FROM accounts WHERE source_package = :pkg AND is_archived = 0 LIMIT 1")
    suspend fun findBySourcePackage(pkg: String): AccountEntity?

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    /**
     * Инкремент кэша баланса при вставке транзакции.
     * EXPENSE уменьшает, INCOME/TRANSFER(приходная нога) увеличивают —
     * знак считаем в SQL, чтобы не делать read-modify-write из Kotlin (race-free).
     */
    @Query(
        """
        UPDATE accounts SET cached_balance_minor = cached_balance_minor + CASE
            WHEN :type = 'EXPENSE' THEN -:amountMinor
            ELSE :amountMinor
        END
        WHERE id = :accountId
        """
    )
    suspend fun applyBalanceDelta(accountId: Long, amountMinor: Long, type: String)
}
