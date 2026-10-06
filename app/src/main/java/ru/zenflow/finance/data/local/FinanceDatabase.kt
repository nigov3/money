package ru.zenflow.finance.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
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

        /**
         * Seed при первом создании БД: базовые категории и «дефолтный кошелёк».
         *
         * Зачем нужен дефолтный счёт: use-case перехвата пишет транзакцию на счёт,
         * найденный по пакету банка, а иначе — на ПЕРВЫЙ активный. Без seed первое
         * же уведомление из Сбербанка некуда сохранить, и онбординг превращается в
         * обязательный блокер. С seed приложение полезно с первой секунды, а
         * онбординг лишь «улучшает» привязку счетов.
         *
         * match_pattern — словарь автокатегоризации (ProcessCapturedMessageUseCase):
         * регулярка без якорей, регистронезависимая, проверяется containsMatchIn.
         */
        val POPULATE_SEED_SQL = arrayOf(
            """INSERT OR IGNORE INTO categories (name, color, icon, system, match_pattern) VALUES
               ('Продукты',        -526345,  'ShoppingCart',   1, 'пятёр|пятер|магнит|лента|вкусвилл|азбука|перекрёст|перекрест|метро market|globus|светофор|fix ?price'),
               ('Кафе и рестораны',-3499780, 'Restaurant',     1, 'кофе|кафе|burger|бургер|kfc|мак|вкусно и точка|subway|додо|pizza|пицца|ресторан|cafe|coffee|starbucks|шоколадница'),
               ('Транспорт',       -13382144,'DirectionsBus',  1, 'такси|taxi|uber|ситимобил|ржд|сапсан|метрополитен|тройка|стрелка|автобус|трамва|маршрутк'),
               ('Коммунальные',    -4629892, 'Home',           1, 'жкх|коммунал|мосэнерг|водоканал|ростелеком|мгтс|межрегион|домофон'),
               ('Связь и интернет',-6684862, 'Wifi',           1, 'мтс|билайн|beeline|мегафон|tele2|т-мобайл|ростелеком|интернет'),
               ('Здоровье',        -1418890, 'LocalHospital',  1, 'аптек|apteka|здравсит|добрая аптека|медси|клиник|стоматолог|лаборатор|invitro|гемотест'),
               ('Развлечения',     -3403370, 'SportsEsports',  1, 'кинотеатр|кино|cinema|karaoke|steam|playstation|xbox|concert|концерт|театр|музей|netflix|ivi|кинопоиск'),
               ('Одежда и обувь',  -8372096, 'Checkroom',      1, 'wildberries|ozon|lamoda|zara|h&m|uniqlo|sportmaster|декатлон|asics|nike'),
               ('Образование',     -1234567, 'School',         1, 'skillbox|нетология|geekbrains|курсы|читай-город|библио-глобус'),
               ('Путешествия',     -2345678, 'Flight',         1, 'авиа|аэрофлот|s7|pobeda|билет|отель|booking|островок|тур'),
               ('Переводы',        -5592405, 'SwapHoriz',      1, 'перевод|перевёл|perevod|с карты на карту'),
               ('Доходы',          -3499780, 'TrendingUp',     1, 'зарплата|zarp|возврат|кэшбек|пенсия')
            """,
            // Дефолтный «Кошелёк»: унитарный счёт до привязки банков.
            """INSERT INTO accounts (name, currency, initial_balance_minor, cached_balance_minor, source_package, is_archived, created_at)
               VALUES ('Кошелёк', 'RUB', 0, 0, NULL, 0, (strftime('%s','now') * 1000))""",
        )

        /** Callback вызывается один раз — после создания файла БД (не при каждом открытии). */
        fun buildSeedCallback(): Callback = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                POPULATE_SEED_SQL.forEach { db.execSQL(it) }
            }
        }

        /** Хелпер для instrumented-тестов/отладки: быстрый доступ без Hilt. */
        fun createInMemory(context: Context): FinanceDatabase =
            Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java)
                .addCallback(buildSeedCallback())
                .allowMainThreadQueries() // ТОЛЬКО для отладки/тестов!
                .build()
    }
}
