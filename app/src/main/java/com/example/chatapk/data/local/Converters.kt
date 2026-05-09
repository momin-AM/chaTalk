package com.example.chatapk.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    @TypeConverter
    fun fromStringList(value: List<String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = Json.decodeFromString(value)

    @TypeConverter
    fun fromStringMap(value: Map<String, String>): String = Json.encodeToString(value)

    @TypeConverter
    fun toStringMap(value: String): Map<String, String> = Json.decodeFromString(value)

    @TypeConverter
    fun fromLongMap(value: Map<String, Long>): String = Json.encodeToString(value)

    @TypeConverter
    fun toLongMap(value: String): Map<String, Long> = Json.decodeFromString(value)

    @TypeConverter
    fun fromBooleanMap(value: Map<String, Boolean>): String = Json.encodeToString(value)

    @TypeConverter
    fun toBooleanMap(value: String): Map<String, Boolean> = Json.decodeFromString(value)
}
