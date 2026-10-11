package com.kazumaproject.dictionary

import com.kazumaproject.bitset.rank1Common
import com.kazumaproject.bitset.rank1CommonShort
import com.kazumaproject.bitset.select0Common
import com.kazumaproject.bitset.select0CommonShort
import com.kazumaproject.dictionary.models.Dictionary
import com.kazumaproject.dictionary.models.TokenEntry
import com.kazumaproject.toBitSet
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.compact.PackedIntArray
import java.io.ObjectOutput
import java.io.ObjectInput
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.text.Normalizer
import java.nio.ShortBuffer
import java.util.BitSet
import com.kazumaproject.Louds.LOUDS

class TokenArray {
    // 圧縮辞書ではメモリマップ領域のビュー（ヒープに複写しない）。
    private var posTableIndexList: ShortBuffer = ShortBuffer.allocate(0)
    private var wordCostList: ShortBuffer = ShortBuffer.allocate(0)
    private var nodeIdList: IntArray = intArrayOf()
    private var packedNodeIds: PackedIntArray? = null
    private val posTableIndexListTemp: MutableList<Short> = arrayListOf()
    private val wordCostListTemp: MutableList<Short> = arrayListOf()
    private val nodeIdListTemp: MutableList<Int> = arrayListOf()
    private var bitListTemp: MutableList<Boolean> = arrayListOf()
    var bitvector: BitSet = BitSet()
    var leftIds: ShortArray = shortArrayOf()
        private set
    var rightIds: ShortArray = shortArrayOf()
        private set

    fun getNodeIds(): IntArray {
        return packedNodeIds?.toIntArray() ?: nodeIdList
    }

    fun getPosTableIndices(): ShortArray = posTableIndexList.toShortArray()

    fun getWordCosts(): ShortArray = wordCostList.toShortArray()

    private fun nodeIdAt(index: Int): Int = packedNodeIds?.get(index) ?: nodeIdList[index]

    fun maxPosTableIndex(): Int =
        posTableIndexList.toShortArray().maxOrNull()?.toInt() ?: -1

    fun minPosTableIndex(): Int =
        posTableIndexList.toShortArray().minOrNull()?.toInt() ?: 0

    fun getListDictionaryByYomiTermId(
        nodeId: Int,
        rank0ArrayTokenArrayBitvector: IntArray,
        rank1ArrayTokenArrayBitvector: IntArray
    ): List<TokenEntry> {
        val startRank = bitvector.rank1Common(
            bitvector.select0Common(nodeId, rank0ArrayTokenArrayBitvector),
            rank1ArrayTokenArrayBitvector
        )
        val endRank = bitvector.rank1Common(
            bitvector.select0Common(
                nodeId + 1,
                rank0ArrayTokenArrayBitvector
            ), rank1ArrayTokenArrayBitvector
        )

        val tempList2 = mutableListOf<TokenEntry>().apply {
            for (i in startRank until endRank) {
                add(
                    TokenEntry(
                        posTableIndex = posTableIndexList[i],
                        wordCost = wordCostList[i],
                        nodeId = nodeIdAt(i)
                    )
                )
            }
        }
        return tempList2
    }

    fun getListDictionaryByYomiTermId(
        nodeId: Int,
        succinctBitVector: SuccinctBitVector
    ): List<TokenEntry> {
        val startSelect0 = succinctBitVector.select0(nodeId)
        val endSelect0 = succinctBitVector.select0(nodeId + 1)
        val startRank1 = succinctBitVector.rank1(startSelect0)
        val endRank1 = succinctBitVector.rank1(endSelect0)

        val tempList2 = mutableListOf<TokenEntry>().apply {
            for (i in startRank1 until endRank1) {
                add(
                    TokenEntry(
                        posTableIndex = posTableIndexList[i],
                        wordCost = wordCostList[i],
                        nodeId = nodeIdAt(i)
                    )
                )
            }
        }
        return tempList2
    }

    fun forEachDictionaryByYomiTermId(
        nodeId: Int,
        succinctBitVector: SuccinctBitVector,
        block: (posTableIndex: Short, wordCost: Short, nodeId: Int) -> Unit,
    ) {
        val startSelect0 = succinctBitVector.select0(nodeId)
        val endSelect0 = succinctBitVector.select0(nodeId + 1)
        val startRank1 = succinctBitVector.rank1(startSelect0)
        val endRank1 = succinctBitVector.rank1(endSelect0)

        for (i in startRank1 until endRank1) {
            block(posTableIndexList[i], wordCostList[i], nodeIdAt(i))
        }
    }

    /**
     * Visits at most [scratchIndices].size tokens in stable ascending word-cost order without
     * materialising and sorting every token for the reading. [scratchIndices] belongs to the
     * caller so repeated typo-frontier hits do not allocate a temporary list per result.
     */
    fun forEachLowestCostDictionaryByYomiTermId(
        nodeId: Int,
        succinctBitVector: SuccinctBitVector,
        scratchIndices: IntArray,
        block: (posTableIndex: Short, wordCost: Short, nodeId: Int) -> Unit,
    ) {
        if (scratchIndices.isEmpty()) return
        val startSelect0 = succinctBitVector.select0(nodeId)
        val endSelect0 = succinctBitVector.select0(nodeId + 1)
        val startRank1 = succinctBitVector.rank1(startSelect0)
        val endRank1 = succinctBitVector.rank1(endSelect0)
        var selectedCount = 0
        for (tokenIndex in startRank1 until endRank1) {
            var insertionIndex = selectedCount
            while (
                insertionIndex > 0 &&
                wordCostList[tokenIndex] < wordCostList[scratchIndices[insertionIndex - 1]]
            ) {
                insertionIndex--
            }
            if (insertionIndex >= scratchIndices.size) continue
            val newCount = (selectedCount + 1).coerceAtMost(scratchIndices.size)
            var shiftIndex = newCount - 1
            while (shiftIndex > insertionIndex) {
                scratchIndices[shiftIndex] = scratchIndices[shiftIndex - 1]
                shiftIndex--
            }
            scratchIndices[insertionIndex] = tokenIndex
            selectedCount = newCount
        }
        for (index in 0 until selectedCount) {
            val tokenIndex = scratchIndices[index]
            block(
                posTableIndexList[tokenIndex],
                wordCostList[tokenIndex],
                nodeIdAt(tokenIndex),
            )
        }
    }

    fun getListDictionaryByYomiTermIdShortArray(
        nodeId: Short,
        rank0ArrayTokenArrayBitvector: ShortArray,
        rank1ArrayTokenArrayBitvector: ShortArray,
    ): List<TokenEntry> {
        val b = bitvector.rank1CommonShort(
            bitvector.select0CommonShort(
                nodeId,
                rank0ArrayTokenArrayBitvector
            ).toInt(), rank1ArrayTokenArrayBitvector
        )
        val c = bitvector.rank1CommonShort(
            bitvector.select0CommonShort(
                (nodeId + 1).toShort(),
                rank0ArrayTokenArrayBitvector
            ).toInt(), rank1ArrayTokenArrayBitvector
        )
        val tempList2 = mutableListOf<TokenEntry>()
        for (i in b until c) {
            tempList2.add(
                TokenEntry(
                    posTableIndex = posTableIndexList[i],
                    wordCost = wordCostList[i],
                    nodeId = nodeIdAt(i),
                )
            )
        }
        return tempList2
    }

    fun getListDictionaryByYomiTermIdShortArray(
        nodeId: Short,
        succinctBitVector: SuccinctBitVector
    ): List<TokenEntry> {
        val startSelect0 = succinctBitVector.select0(nodeId.toInt())
        val startRank1 = succinctBitVector.rank1(startSelect0)
        val endSelect0 = succinctBitVector.select0(nodeId + 1)
        val endRank1 = succinctBitVector.rank1(endSelect0)
        val tempList2 = mutableListOf<TokenEntry>()
        for (i in startRank1 until endRank1) {
            tempList2.add(
                TokenEntry(
                    posTableIndex = posTableIndexList[i],
                    wordCost = wordCostList[i],
                    nodeId = nodeIdAt(i),
                )
            )
        }
        return tempList2
    }

    fun forEachDictionaryByYomiTermIdShortArray(
        nodeId: Short,
        succinctBitVector: SuccinctBitVector,
        block: (posTableIndex: Short, wordCost: Short, nodeId: Int) -> Unit,
    ) {
        val startSelect0 = succinctBitVector.select0(nodeId.toInt())
        val startRank1 = succinctBitVector.rank1(startSelect0)
        val endSelect0 = succinctBitVector.select0(nodeId + 1)
        val endRank1 = succinctBitVector.rank1(endSelect0)

        for (i in startRank1 until endRank1) {
            block(posTableIndexList[i], wordCostList[i], nodeIdAt(i))
        }
    }

    fun readExternal(
        objectInput: ObjectInput,
    ): TokenArray {
        objectInput.apply {
            try {
                posTableIndexList = ShortBuffer.wrap(readObject() as ShortArray)
                wordCostList = ShortBuffer.wrap(readObject() as ShortArray)
                val loadedNodeIds = readObject() as IntArray
                if (loadedNodeIds.size >= PACKED_NODE_ID_THRESHOLD) {
                    packedNodeIds = PackedIntArray.from(loadedNodeIds)
                    nodeIdList = intArrayOf()
                } else {
                    packedNodeIds = null
                    nodeIdList = loadedNodeIds
                }
                bitvector = readObject() as BitSet
                close()
            } catch (e: Exception) {
                println(e.stackTraceToString())
            }
        }
        return TokenArray()
    }

    fun buildTokenArray(
        dictionaries: Map<String, List<Dictionary>>,
        tangoTrie: LOUDS,
        out: ObjectOutput,
        posIndexMap: Map<Pair<Short, Short>, Int>,
    ) {
        val posTableIndices = mutableListOf<Short>()
        val wordCosts = mutableListOf<Short>()
        val nodeIds = mutableListOf<Int>()
        val bits = mutableListOf<Boolean>()

        dictionaries.forEach { (yomi, dictionaryList) ->
            bits.add(false)
            dictionaryList.forEach { dictionary ->
                bits.add(true)
                val posIndex = posIndexMap.getValue(dictionary.leftId to dictionary.rightId)
                posTableIndices.add(posIndex.toShort())
                wordCosts.add(dictionary.cost)
                nodeIds.add(getNodeIdForDictionary(dictionary, tangoTrie, yomi))
            }
        }

        posTableIndexList = ShortBuffer.wrap(posTableIndices.toShortArray())
        wordCostList = ShortBuffer.wrap(wordCosts.toShortArray())
        nodeIdList = nodeIds.toIntArray()
        packedNodeIds = null
        bitvector = bits.toBitSet()
        writeExternalNotCompress(out)
    }

    private fun getNodeIdForDictionary(
        dictionary: Dictionary,
        tangoTrie: LOUDS,
        yomi: String,
    ): Int {
        val tango = dictionary.tango
        return when {
            yomi == tango -> -2
            tango.isHiraganaOnly() -> -2
            tango.isKatakanaOnly() -> -1
            else -> tangoTrie.getNodeIndex(Normalizer.normalize(tango, Normalizer.Form.NFC))
        }
    }

    fun writeExternalNotCompress(out: ObjectOutput) {
        out.apply {
            writeObject(posTableIndexList.toShortArray())
            writeObject(wordCostList.toShortArray())
            writeObject(packedNodeIds?.toIntArray() ?: nodeIdList)
            writeObject(bitvector)
            flush()
            close()
        }
    }

    /**
     *
     * @param fileList dictionary00 ~ dictionary09
     *
     **/
    fun buildPOSTable(
        fileList: List<String>,
        objectOutputStream: ObjectOutputStream
    ) {
        val tempMap: MutableMap<Pair<Short, Short>, Int> = mutableMapOf()
        fileList.forEach {
            val line = this::class.java.getResourceAsStream(it)
                ?.bufferedReader()
                ?.readLines()
            line?.forEach { str ->
                str.apply {
                    val leftId = split("\\t".toRegex())[1]
                    val rightId = split("\\t".toRegex())[2]
                    if (tempMap[Pair(leftId.toShort(), rightId.toShort())] == null) {
                        tempMap[Pair(leftId.toShort(), rightId.toShort())] = 0
                    } else {
                        tempMap[Pair(leftId.toShort(), rightId.toShort())] =
                            (tempMap[Pair(leftId.toShort(), rightId.toShort())]!!) + 1
                    }
                }
            }
        }

        val result = tempMap.toList().sortedByDescending { (_, value) -> value }.toMap()
        val objectToWrite = result.keys.toList()
        try {
            objectOutputStream.apply {
                writeObject(objectToWrite)
                flush()
                close()
            }
        } catch (e: Exception) {
            println(e.stackTraceToString())
        }
    }

    /**
     *
     * @param fileList dictionary00 ~ dictionary09
     *
     **/
    fun buildPOSTableWithIndex(
        fileList: List<String>,
        objectOutputStream: ObjectOutputStream
    ) {
        val tempMap: MutableMap<Pair<Short, Short>, Int> = mutableMapOf()
        fileList.forEach {
            val line = this::class.java.getResourceAsStream(it)
                ?.bufferedReader()
                ?.readLines()
            line?.forEach { str ->
                str.apply {
                    val leftId = split("\\t".toRegex())[1]
                    val rightId = split("\\t".toRegex())[2]
                    if (tempMap[Pair(leftId.toShort(), rightId.toShort())] == null) {
                        tempMap[Pair(leftId.toShort(), rightId.toShort())] = 0
                    } else {
                        tempMap[Pair(leftId.toShort(), rightId.toShort())] =
                            (tempMap[Pair(leftId.toShort(), rightId.toShort())]!!) + 1
                    }
                }
            }
        }

        val result = tempMap.toList().sortedByDescending { (_, value) -> value }.toMap()
        val mapToSave = result.keys.toList().mapIndexed { index, pair -> pair to index }.toMap()
        try {
            objectOutputStream.apply {
                writeObject(mapToSave)
                flush()
                close()
            }
        } catch (e: Exception) {
            println(e.stackTraceToString())
        }
    }

    fun readPOSTable(
        objectInputStream: ObjectInputStream
    ) {
        objectInputStream.apply {
            setPOSTable(
                leftIds = readObject() as ShortArray,
                rightIds = readObject() as ShortArray,
            )
        }
    }

    fun setPOSTable(leftIds: ShortArray, rightIds: ShortArray) {
        this.leftIds = leftIds
        this.rightIds = rightIds
    }

    fun readPOSTableWithIndex(
        objectInputStream: ObjectInputStream
    ): Map<Pair<Short, Short>, Int> {
        var a: Map<Pair<Short, Short>, Int>
        objectInputStream.apply {
            a = (readObject() as Map<Pair<Short, Short>, Int>)
        }
        return a
    }

    private fun String.isHiraganaOnly(): Boolean {
        return isNotEmpty() && all { it in 'ぁ'..'ゖ' || it == 'ー' }
    }

    private fun String.isKatakanaOnly(): Boolean {
        return isNotEmpty() && all { it in 'ァ'..'ヶ' || it == 'ー' }
    }

    private fun ShortBuffer.toShortArray(): ShortArray =
        ShortArray(capacity()).also { duplicate().apply { clear() }.get(it) }

    companion object {
        private const val PACKED_NODE_ID_THRESHOLD = 500_000

        fun fromPacked(
            posTableIndices: ShortBuffer,
            wordCosts: ShortBuffer,
            nodeIds: PackedIntArray,
            bitvector: BitSet,
            leftIds: ShortArray,
            rightIds: ShortArray,
        ): TokenArray {
            require(posTableIndices.remaining() == wordCosts.remaining()) {
                "Token POS/cost size mismatch: ${posTableIndices.remaining()} != ${wordCosts.remaining()}"
            }
            require(posTableIndices.remaining() == nodeIds.size) {
                "Token/node size mismatch: ${posTableIndices.remaining()} != ${nodeIds.size}"
            }
            return TokenArray().apply {
                posTableIndexList = posTableIndices.slice()
                wordCostList = wordCosts.slice()
                nodeIdList = intArrayOf()
                packedNodeIds = nodeIds
                this.bitvector = bitvector
                this.leftIds = leftIds
                this.rightIds = rightIds
            }
        }
    }

}
