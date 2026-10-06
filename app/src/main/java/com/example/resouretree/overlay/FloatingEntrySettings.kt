package com.example.resouretree.overlay

import android.content.Context
import kotlin.math.roundToInt

/** Shares the existing position preferences; size changes never touch resource data. */
class FloatingEntrySettings(context: Context) {
    val preferences = context.applicationContext.getSharedPreferences("floating-entry", Context.MODE_PRIVATE)
    val sizeDp: Int get() = normalize(preferences.getInt(KEY_SIZE, DEFAULT_SIZE))

    fun setSize(value: Float): Int = normalize(value.roundToInt()).also {
        preferences.edit().putInt(KEY_SIZE, it).apply()
    }

    companion object {
        const val KEY_SIZE = "size-dp"
        const val MIN_SIZE = 36
        const val MAX_SIZE = 96
        const val DEFAULT_SIZE = 56
        fun normalize(value: Int): Int = (MIN_SIZE + ((value.coerceIn(MIN_SIZE, MAX_SIZE) - MIN_SIZE) / 4f).roundToInt() * 4)
            .coerceIn(MIN_SIZE, MAX_SIZE)
    }
}
