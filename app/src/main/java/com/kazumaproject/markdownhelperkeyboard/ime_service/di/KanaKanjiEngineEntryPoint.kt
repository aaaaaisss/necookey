package com.kazumaproject.markdownhelperkeyboard.ime_service.di

import com.kazumaproject.markdownhelperkeyboard.converter.engine.KanaKanjiEngine
import com.kazumaproject.markdownhelperkeyboard.custom_keyboard.database.KeyboardLayoutDao
import com.kazumaproject.markdownhelperkeyboard.dictionary_override.DictionaryBinaryReader
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface KanaKanjiEngineEntryPoint {
    fun kanaKanjiEngine(): KanaKanjiEngine
    fun userDictionaryRepository(): UserDictionaryRepository
    fun dictionaryBinaryReader(): DictionaryBinaryReader
    fun keyboardLayoutDao(): KeyboardLayoutDao
}
