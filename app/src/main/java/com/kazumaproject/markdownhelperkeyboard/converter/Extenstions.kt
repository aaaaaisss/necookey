package com.kazumaproject

import android.util.SparseArray
import androidx.core.util.isEmpty
import androidx.core.util.size
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.Normalizer
import java.util.BitSet


fun List<Boolean>.toBitSet(): BitSet {
    val bitSet = BitSet(this.size)
    this.forEachIndexed { index, value ->
        if (value) {
            bitSet.set(index, true)
        }
    }
    return bitSet
}

fun String.hiraToKata() =
    this.map {
        when (it) {
            'ゔ' -> 'ヴ'
            'づ' -> 'ヅ'  // づ is handled here
            'ゐ' -> 'ヰ'
            'ゑ' -> 'ヱ'
            in 'ぁ'..'ん' -> (it.code + 0x60).toChar()  // 通常のひらがなは0x60を加算してカタカナに変換
            else -> it  // その他の文字はそのまま
        }
    }.joinToString("")


fun List<Char>.toByteArrayFromListChar(): ByteArray {
    return this.map { it.code }.toByteArray()
}

fun ByteArray.toListChar(): MutableList<Char> {
    return this.toListInt().map { it.toChar() }.toMutableList()
}

fun List<Int>.toByteArray(): ByteArray {
    val buffer = ByteBuffer.allocate(this.size * 4) // Each Int occupies 4 bytes
    this.forEach { buffer.putInt(it) }
    return buffer.array()
}

fun ByteArray.toListInt(): MutableList<Int> {
    val intList = mutableListOf<Int>()
    for (i in indices step 4) {
        val value = (this[i].toInt() shl 24) or
                ((this[i + 1].toInt() and 0xFF) shl 16) or
                ((this[i + 2].toInt() and 0xFF) shl 8) or
                (this[i + 3].toInt() and 0xFF)
        intList.add(value)
    }
    return intList
}

fun List<Short>.toByteArrayFromListShort(): ByteArray {
    val byteArray = ByteArray(this.size * 2) // Each Short occupies 2 bytes
    for (i in this.indices) {
        val shortValue = this[i]
        byteArray[i * 2] = (shortValue.toInt() shr 8).toByte() // High byte
        byteArray[i * 2 + 1] = shortValue.toByte() // Low byte
    }
    return byteArray
}

fun BitSet.toBooleanArray(): BooleanArray {
    return BooleanArray(this.length()) { this[it] }
}

fun BooleanArray.preprocessLBSIntoBooleanArray(): IntArray {
    val prefixSum = IntArray(this.size + 1)
    for (i in this.indices) {
        prefixSum[i + 1] = prefixSum[i] + if (this[i]) 0 else 1
    }
    return prefixSum
}

fun String.convertFullWidthToHalfWidth(): String {
    return Normalizer.normalize(this, Normalizer.Form.NFKC)
}

fun String.toFullWidthDigitsEfficient(): String {
    val sb = StringBuilder()
    for (ch in this) {
        sb.append(if (ch in '0'..'9') '０' + (ch - '0') else ch)
    }
    return sb.toString()
}

inline fun <T> SparseArray<T>.computeIfAbsent(
    key: Int,
    defaultValue: () -> T
): T {
    val current = get(key)
    return if (current == null) {
        val newValue = defaultValue()
        put(key, newValue)
        newValue
    } else {
        current
    }
}

/** Returns the maximum key in a SparseArray, or null if empty. */
fun SparseArray<*>.maxKeyOrNull(): Int? {
    if (isEmpty()) return null
    var maxKey = keyAt(0)
    for (i in 1 until size) {
        val key = keyAt(i)
        if (key > maxKey) maxKey = key
    }
    return maxKey
}