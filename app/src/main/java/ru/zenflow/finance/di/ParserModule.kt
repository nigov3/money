package ru.zenflow.finance.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ru.zenflow.finance.core.parser.RegexTransactionParser
import ru.zenflow.finance.core.parser.TransactionParser
import javax.inject.Singleton

/** Связываем контракт парсинга с regex-реализацией. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ParserModule {

    @Binds
    @Singleton
    abstract fun bindTransactionParser(impl: RegexTransactionParser): TransactionParser
}
