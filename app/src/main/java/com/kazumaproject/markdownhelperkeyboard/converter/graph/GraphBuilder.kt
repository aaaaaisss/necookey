package com.kazumaproject.markdownhelperkeyboard.converter.graph

import com.kazumaproject.markdownhelperkeyboard.learning.database.LearningScorePolicy
import com.kazumaproject.Louds.LOUDS
import com.kazumaproject.Louds.with_term_id.LOUDSWithTermId
import com.kazumaproject.dictionary.TokenArray
import com.kazumaproject.graph.CandidateSource
import com.kazumaproject.graph.MozcNodeAttributes
import com.kazumaproject.graph.MozcNodeType
import com.kazumaproject.graph.Node
import com.kazumaproject.hiraToKata
import com.kazumaproject.markdownhelperkeyboard.converter.Other.BOS
import com.kazumaproject.markdownhelperkeyboard.converter.bitset.SuccinctBitVector
import com.kazumaproject.markdownhelperkeyboard.converter.mozc.MozcNodeAttributeTable
import com.kazumaproject.markdownhelperkeyboard.converter.trace.GraphNodeTrace
import com.kazumaproject.markdownhelperkeyboard.ime_service.extensions.isAllHalfWidthAscii
import com.kazumaproject.markdownhelperkeyboard.repository.LearnRepository
import com.kazumaproject.markdownhelperkeyboard.repository.UserDictionaryRepository
import com.kazumaproject.markdownhelperkeyboard.user_dictionary.PosMapper
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

class GraphBuilder {

    internal data class CachedGraph(
        val input: String,
        val signature: Int,
        val graph: MutableMap<Int, MutableList<Node>>,
    )

    internal class IncrementalGraph(
        override var reusedThroughEndIndex: Int,
        override val conversionSignature: Int,
        override var forwardDpReusableThroughEndIndex: Int = reusedThroughEndIndex,
    ) : LinkedHashMap<Int, MutableList<Node>>(), IncrementalGraphMetadata

    @Volatile
    private var cachedGraph: CachedGraph? = null

    private var systemUserYomiTrie: LOUDSWithTermId? = null
    private var systemUserTangoTrie: LOUDS? = null
    private var systemUserTokenArray: TokenArray? = null
    private var systemUserSuccinctBitVectorLBSYomi: SuccinctBitVector? = null
    private var systemUserSuccinctBitVectorIsLeafYomi: SuccinctBitVector? = null
    private var systemUserSuccinctBitVectorTokenArray: SuccinctBitVector? = null
    private var systemUserSuccinctBitVectorTangoLBS: SuccinctBitVector? = null

    private var englishReadingYomiTrie: LOUDSWithTermId? = null
    private var englishReadingTangoTrie: LOUDS? = null
    private var englishReadingTokenArray: TokenArray? = null
    private var englishReadingSuccinctBitVectorLBSYomi: SuccinctBitVector? = null
    private var englishReadingSuccinctBitVectorIsLeafYomi: SuccinctBitVector? = null
    private var englishReadingSuccinctBitVectorTokenArray: SuccinctBitVector? = null
    private var englishReadingSuccinctBitVectorTangoLBS: SuccinctBitVector? = null

    fun updateSystemUserDictionary(
        yomiTrie: LOUDSWithTermId?,
        tangoTrie: LOUDS?,
        tokenArray: TokenArray?,
        succinctBitVectorLBSYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafYomi: SuccinctBitVector?,
        succinctBitVectorTokenArray: SuccinctBitVector?,
        succinctBitVectorTangoLBS: SuccinctBitVector?,
    ) {
        cachedGraph = null
        systemUserYomiTrie = yomiTrie
        systemUserTangoTrie = tangoTrie
        systemUserTokenArray = tokenArray
        systemUserSuccinctBitVectorLBSYomi = succinctBitVectorLBSYomi
        systemUserSuccinctBitVectorIsLeafYomi = succinctBitVectorIsLeafYomi
        systemUserSuccinctBitVectorTokenArray = succinctBitVectorTokenArray
        systemUserSuccinctBitVectorTangoLBS = succinctBitVectorTangoLBS
    }

    fun updateEnglishReadingDictionary(
        yomiTrie: LOUDSWithTermId?,
        tangoTrie: LOUDS?,
        tokenArray: TokenArray?,
        succinctBitVectorLBSYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafYomi: SuccinctBitVector?,
        succinctBitVectorTokenArray: SuccinctBitVector?,
        succinctBitVectorTangoLBS: SuccinctBitVector?,
    ) {
        cachedGraph = null
        englishReadingYomiTrie = yomiTrie
        englishReadingTangoTrie = tangoTrie
        englishReadingTokenArray = tokenArray
        englishReadingSuccinctBitVectorLBSYomi = succinctBitVectorLBSYomi
        englishReadingSuccinctBitVectorIsLeafYomi = succinctBitVectorIsLeafYomi
        englishReadingSuccinctBitVectorTokenArray = succinctBitVectorTokenArray
        englishReadingSuccinctBitVectorTangoLBS = succinctBitVectorTangoLBS
    }

    /**
     * グラフにノードを追加または更新する。
     * 同じ終了位置に【同じ単語】かつ【同じ品詞ID(l/r)】のノードが既に存在する場合、
     * 新しいノードのスコアが既存のノードよりも良ければ（低ければ）置き換える。
     * 品詞IDが異なる場合は、別のノードとしてリストに追加する。
     *
     * @param graph グラフ全体
     * @param endIndex ノードの終了インデックス
     * @param newNode 追加する新しいノード
     */
    private fun addOrUpdateNode(
        graph: MutableMap<Int, MutableList<Node>>,
        endIndex: Int,
        newNode: Node,
        mode: GraphNodeDedupMode,
        trace: MutableList<GraphNodeTrace>?,
        input: String,
        source: String,
    ) {
        val reusedThroughEndIndex = (graph as? IncrementalGraph)?.reusedThroughEndIndex ?: -1
        if (endIndex <= reusedThroughEndIndex) return
        val nodes = graph.computeIfAbsent(endIndex) { mutableListOf() }

        when (mode) {
            GraphNodeDedupMode.MOZC_KEEP_ALL -> {
                nodes.add(newNode)
                trace?.add(newNode.toTrace(input, endIndex, source, "ADDED"))
            }
            GraphNodeDedupMode.EXISTING_BY_TANGO_L_R -> {
                // tango, l, r の3つがすべて一致するノードを探す
                val existingNodeIndex = nodes.indexOfFirst {
                    it.tango == newNode.tango && it.l == newNode.l && it.r == newNode.r
                }

                if (existingNodeIndex != -1) {
                    // 完全に一致する既存ノードが見つかった場合
                    val existingNode = nodes[existingNodeIndex]
                    if (newNode.score < existingNode.score) {
                        // 新しいノードの方がスコアが良い（低い）ので置き換える
                        nodes[existingNodeIndex] = newNode
                        trace?.add(newNode.toTrace(input, endIndex, source, "REPLACED"))
                    } else {
                        trace?.add(newNode.toTrace(input, endIndex, source, "SKIPPED"))
                    }
                } else {
                    // 新しい単語、または、同じ単語だが品詞が異なる場合は、単純に追加する
                    nodes.add(newNode)
                    trace?.add(newNode.toTrace(input, endIndex, source, "ADDED"))
                }
            }
        }
    }

    private fun Node.toTrace(
        input: String,
        endIndex: Int,
        source: String,
        event: String,
    ): GraphNodeTrace =
        GraphNodeTrace(
            input = input,
            yomiUsed = yomiUsed,
            tango = tango,
            startPos = sPos,
            endPos = endIndex,
            leftId = l.toInt(),
            rightId = r.toInt(),
            wordCost = score,
            source = source,
            event = event,
        )

    suspend fun constructGraph(
        str: String,
        yomiTrie: LOUDSWithTermId,
        tangoTrie: LOUDS,
        tokenArray: TokenArray,
        succinctBitVectorLBSYomi: SuccinctBitVector,
        succinctBitVectorIsLeafYomi: SuccinctBitVector,
        succinctBitVectorTokenArray: SuccinctBitVector,
        succinctBitVectorTangoLBS: SuccinctBitVector,
        userDictionaryRepository: UserDictionaryRepository?,
        learnRepository: LearnRepository?,
        wikiYomiTrie: LOUDSWithTermId?,
        wikiTangoTrie: LOUDS?,
        wikiTokenArray: TokenArray?,
        succinctBitVectorLBSWikiYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafWikiYomi: SuccinctBitVector?,
        succinctBitVectorWikiTokenArray: SuccinctBitVector?,
        succinctBitVectorWikiTangoLBS: SuccinctBitVector?,
        webYomiTrie: LOUDSWithTermId?,
        webTangoTrie: LOUDS?,
        webTokenArray: TokenArray?,
        succinctBitVectorLBSwebYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafwebYomi: SuccinctBitVector?,
        succinctBitVectorwebTokenArray: SuccinctBitVector?,
        succinctBitVectorwebTangoLBS: SuccinctBitVector?,
        personYomiTrie: LOUDSWithTermId?,
        personTangoTrie: LOUDS?,
        personTokenArray: TokenArray?,
        succinctBitVectorLBSpersonYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafpersonYomi: SuccinctBitVector?,
        succinctBitVectorpersonTokenArray: SuccinctBitVector?,
        succinctBitVectorpersonTangoLBS: SuccinctBitVector?,
        neologdYomiTrie: LOUDSWithTermId?,
        neologdTangoTrie: LOUDS?,
        neologdTokenArray: TokenArray?,
        succinctBitVectorLBSneologdYomi: SuccinctBitVector?,
        succinctBitVectorIsLeafneologdYomi: SuccinctBitVector?,
        succinctBitVectorneologdTokenArray: SuccinctBitVector?,
        succinctBitVectorneologdTangoLBS: SuccinctBitVector?,
        beamWidth: Int = 20,
        graphNodeDedupMode: GraphNodeDedupMode = GraphNodeDedupMode.EXISTING_BY_TANGO_L_R,
        mozcNodeAttributeTable: MozcNodeAttributeTable? = null,
        graphNodeTrace: MutableList<GraphNodeTrace>? = null,
    ): MutableMap<Int, MutableList<Node>> {
        if (str.isAllHalfWidthAscii()) {
            return mutableMapOf()
        }
        fun mozcAttributesFor(leftId: Short): Int =
            mozcNodeAttributeTable?.attributesFor(leftId.toInt()) ?: MozcNodeAttributes.NONE

        val signature = conversionSignature(
            yomiTrie = yomiTrie,
            englishReadingYomiTrie = englishReadingYomiTrie,
            wikiYomiTrie = wikiYomiTrie,
            webYomiTrie = webYomiTrie,
            personYomiTrie = personYomiTrie,
            neologdYomiTrie = neologdYomiTrie,
            userDictionaryRepository = userDictionaryRepository,
            learnRepository = learnRepository,
            beamWidth = beamWidth,
            graphNodeDedupMode = graphNodeDedupMode,
            mozcNodeAttributeTable = mozcNodeAttributeTable,
        )
        val reusable = cachedGraph?.takeIf {
            graphNodeTrace == null &&
                it.signature == signature &&
                str.length == it.input.length + 1 &&
                str.startsWith(it.input) &&
                // Appending within an alphabet token changes every maximal-run node that starts
                // in that token.  The current append cache can only preserve a fully stable
                // prefix, so rebuild this transition instead of publishing a stale short token.
                str.substring((it.input.length - 1).coerceAtLeast(0))
                    .windowed(2)
                    .none { pair -> pair[0].isAsciiAlphabet() && pair[1].isAsciiAlphabet() }
        }
        val reusablePrefixLength = reusable?.input?.length ?: -1

        fun prefixSearch(
            trie: LOUDSWithTermId,
            bitVector: SuccinctBitVector,
            start: Int,
        ): List<LOUDSWithTermId.CommonPrefixSearchResult> =
            trie.commonPrefixSearchWithProgress(
                str = str,
                start = start,
                succinctBitVector = bitVector,
            ).results

        val graph: MutableMap<Int, MutableList<Node>> = if (reusable != null) {
            IncrementalGraph(reusablePrefixLength, signature).apply {
                reusable.graph.forEach { (endIndex, nodes) ->
                    if (endIndex != reusablePrefixLength + 1) {
                        put(endIndex, nodes.mapTo(mutableListOf()) { it.copyForGraphBuild() })
                    }
                }
            }
        } else {
            IncrementalGraph(-1, signature).apply { put(0, mutableListOf(BOS)) }
        }

        fun invalidateForwardDpReuseAndRestoreCompletePrefix() {
            val incrementalGraph = graph as? IncrementalGraph ?: return
            if (incrementalGraph.forwardDpReusableThroughEndIndex < 0) return
            incrementalGraph.forwardDpReusableThroughEndIndex = -1
        }

        graph[str.length + 1] = mutableListOf(
            Node(
                l = 0,
                r = 0,
                score = 0,
                f = 0,
                g = 0,
                tango = "EOS",
                yomiUsed = "EOS",
                len = 0,
                sPos = str.length + 1,
                mozcNodeType = MozcNodeType.EOS,
            )
        )
        val learnedCostNow = System.currentTimeMillis()
        val dialectActive = KanaDialectNodes.mayApply(str)
        // 方言の活用語尾が始まる位置（語幹はその手前の辞書の動詞活用形、DialectInflectionNodes.kt）
        val dialectSuffixStarts = if (DialectInflectionNodes.mayApply(str)) DialectInflectionNodes.suffixStarts(str) else IntArray(0)
        val dialectSuffixCost = DialectInflectionNodes.suffixCost
        for (i in str.indices) {
            currentCoroutineContext().ensureActive()
            var subStrCache: String? = null
            fun subStr(): String {
                val cached = subStrCache
                if (cached != null) return cached
                return str.substring(i).also { subStrCache = it }
            }
            var foundInAnyDictionary = false

            // 1. ユーザー辞書
            val userWords = userDictionaryRepository?.let { repository ->
                if (reusable != null && str.length == reusable.input.length + 1) {
                    repository.exactMatchesForConversion(subStr())
                } else {
                    repository.commonPrefixSearchInUserDict(subStr())
                }
            } ?: emptyList()
            if (userWords.isNotEmpty()) foundInAnyDictionary = true
            userWords.forEach { userWord ->
                val endIndex = i + userWord.reading.length
                val contextId = PosMapper.getContextIdForPos(userWord.posIndex)
                val node = Node(
                    l = contextId,
                    r = contextId,
                    score = userWord.posScore,
                    f = userWord.posScore,
                    g = userWord.posScore,
                    tango = userWord.word,
                    yomiUsed = userWord.reading,
                    len = userWord.reading.length.toShort(),
                    sPos = i,
                    mozcAttributes = mozcAttributesFor(contextId),
                    candidateSource = CandidateSource.USER_DICTIONARY,
                )
                addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "USER")
            }

            // 2. 学習辞書
            val learnedWords = learnRepository?.let { repository ->
                if (reusable != null && str.length == reusable.input.length + 1) {
                    repository.findExactMatchesForConversion(subStr())
                } else {
                    repository.findCommonPrefixes(subStr())
                }
            } ?: emptyList()
            if (learnedWords.isNotEmpty()) foundInAnyDictionary = true
            learnedWords.forEach { learnedWord ->
                val endIndex = i + learnedWord.input.length
                // 学習直後は強く、半減期で弱まる単語コスト（読み出し時に計算、LearningScorePolicy）。
                val learnedCost = LearningScorePolicy.conversionCost(
                    storedScore = learnedWord.score,
                    usageCount = learnedWord.usageCount,
                    lastUsedAt = learnedWord.lastUsedAt,
                    readingLength = learnedWord.input.length,
                    now = learnedCostNow,
                ) ?: return@forEach // 忘れた語（強さが下限未満）はグラフに入れない
                val node = Node(
                    l = learnedWord.leftId ?: 1851.toShort(),
                    r = learnedWord.rightId ?: 1851.toShort(),
                    score = learnedCost,
                    f = learnedCost,
                    g = learnedCost,
                    tango = learnedWord.out,
                    yomiUsed = learnedWord.input,
                    len = learnedWord.input.length.toShort(),
                    sPos = i,
                    mozcAttributes = mozcAttributesFor(learnedWord.leftId ?: 1851.toShort()),
                    candidateSource = CandidateSource.LEARNED_DICTIONARY,
                )
                addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "LEARN")
            }

            val localSystemUserYomiTrie = systemUserYomiTrie
            val localSystemUserTangoTrie = systemUserTangoTrie
            val localSystemUserTokenArray = systemUserTokenArray
            val localSystemUserLBSYomi = systemUserSuccinctBitVectorLBSYomi
            val localSystemUserIsLeaf = systemUserSuccinctBitVectorIsLeafYomi
            val localSystemUserTokenBitVector = systemUserSuccinctBitVectorTokenArray
            val localSystemUserTangoLBS = systemUserSuccinctBitVectorTangoLBS

            if (
                localSystemUserYomiTrie != null &&
                localSystemUserTangoTrie != null &&
                localSystemUserTokenArray != null &&
                localSystemUserLBSYomi != null &&
                localSystemUserIsLeaf != null &&
                localSystemUserTokenBitVector != null &&
                localSystemUserTangoLBS != null
            ) {
                val systemUserPrefixSearch = prefixSearch(
                    trie = localSystemUserYomiTrie,
                    bitVector = localSystemUserLBSYomi,
                    start = i,
                )
                if (systemUserPrefixSearch.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in systemUserPrefixSearch) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex <= 0) continue
                    val termId = localSystemUserYomiTrie.getTermId(nodeIndex, localSystemUserIsLeaf)
                    val endIndex = i + yomiStr.length
                    if (endIndex <= reusablePrefixLength) continue
                    localSystemUserTokenArray.forEachDictionaryByYomiTermId(
                        termId,
                        localSystemUserTokenBitVector,
                    ) { posTableIndex, wordCost, tokenNodeId ->
                        val tango = when (tokenNodeId) {
                            -2 -> yomiStr
                            -1 -> yomiStr.hiraToKata()
                            else -> localSystemUserTangoTrie.getLetter(
                                tokenNodeId,
                                succinctBitVector = localSystemUserTangoLBS,
                            )
                        }
                        val leftId = localSystemUserTokenArray.leftIds[posTableIndex.toInt()]
                        addOrUpdateNode(
                            graph,
                            endIndex,
                            Node(
                                l = leftId,
                                r = localSystemUserTokenArray.rightIds[posTableIndex.toInt()],
                                score = wordCost.toInt(),
                                f = wordCost.toInt(),
                                g = wordCost.toInt(),
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(
                                    leftId,
                                ),
                            ),
                            graphNodeDedupMode,
                            graphNodeTrace,
                            str,
                            "SYSTEM_USER",
                        )
                    }
                }

            }

            // 3. システム辞書 (通常検索)
            val commonPrefixSearchSystem = prefixSearch(
                trie = yomiTrie,
                bitVector = succinctBitVectorLBSYomi,
                start = i,
            )
            if (commonPrefixSearchSystem.isNotEmpty()) foundInAnyDictionary = true
            for (prefixResult in commonPrefixSearchSystem) {
                val yomiStr = prefixResult.yomi
                val nodeIndex = prefixResult.nodeIndex
                if (nodeIndex > 0) {
                    val termId = yomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafYomi)
                    val endIndex = i + yomiStr.length
                    if (endIndex <= reusablePrefixLength) continue
                    tokenArray.forEachDictionaryByYomiTermId(
                        termId,
                        succinctBitVectorTokenArray
                    ) { posTableIndex, wordCost, tokenNodeId ->
                        val tango = when (tokenNodeId) {
                            -2 -> yomiStr
                            -1 -> yomiStr.hiraToKata()
                            else -> SystemDictionaryErrata.fix(
                                tangoTrie.getLetter(
                                    tokenNodeId,
                                    succinctBitVector = succinctBitVectorTangoLBS
                                )
                            )
                        }
                        val leftId = tokenArray.leftIds[posTableIndex.toInt()]
                        val cost = wordCost.toInt() + VerbInflectionCost.delta(leftId, yomiStr, tango)
                        val node = Node(
                            l = leftId,
                            r = tokenArray.rightIds[posTableIndex.toInt()],
                            score = cost,
                            f = cost,
                            g = cost,
                            tango = tango,
                            yomiUsed = yomiStr,
                            len = yomiStr.length.toShort(),
                            sPos = i,
                            mozcAttributes = mozcAttributesFor(leftId),
                        )
                        addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "SYSTEM")
                    }
                }
            }

            // 3.0 文中の複合数詞（NumberReadingNodes.kt）
            for ((endIndex, value) in NumberReadingNodes.spans(str, i)) {
                if (endIndex <= reusablePrefixLength) continue
                val yomiStr = str.substring(i, endIndex)
                foundInAnyDictionary = true
                for ((tango, id, cost) in listOf(
                    Triple(value.toString(), NumberReadingNodes.ARABIC_ID, NumberReadingNodes.ARABIC_COST),
                    Triple(NumberReadingNodes.toKanji(value), NumberReadingNodes.KANJI_ID, NumberReadingNodes.ARABIC_COST + NumberReadingNodes.KANJI_EXTRA_COST),
                )) {
                    addOrUpdateNode(
                        graph, endIndex,
                        Node(l = id, r = id, score = cost, f = cost, g = cost, tango = tango, yomiUsed = yomiStr, len = yomiStr.length.toShort(), sPos = i),
                        graphNodeDedupMode, graphNodeTrace, str, "NUMBER",
                    )
                }
            }

            // 3.05 方言の活用語尾を動詞語幹に付けた 1 ノード（DialectInflectionNodes.kt）。語幹 = str[i, e) の辞書の動詞活用形。
            // 差分入力で語幹が再利用範囲にあっても、語尾の終わりが新しい範囲なら作る（共通接頭辞検索の結果には頼らない）。
            for (e in dialectSuffixStarts) {
                if (e <= i) continue
                if (e - i > DialectInflectionNodes.MAX_STEM_LEN) break
                val stemYomi = str.substring(i, e)
                val idx = yomiTrie.getNodeIndex(stemYomi, succinctBitVectorLBSYomi)
                if (idx <= 0) continue
                val termId = yomiTrie.getTermId(idx, succinctBitVectorIsLeafYomi)
                if (termId < 0) continue
                tokenArray.forEachDictionaryByYomiTermId(termId, succinctBitVectorTokenArray) { posTableIndex, wordCost, tokenNodeId ->
                    val leftId = tokenArray.leftIds[posTableIndex.toInt()]
                    val stemClass = DialectInflectionNodes.stemClass(leftId)
                    if (stemClass != 0) {
                        val stemTango = when (tokenNodeId) {
                            -2 -> stemYomi
                            -1 -> stemYomi.hiraToKata()
                            else -> tangoTrie.getLetter(tokenNodeId, succinctBitVector = succinctBitVectorTangoLBS)
                        }
                        val stemCost = wordCost.toInt() + VerbInflectionCost.delta(leftId, stemYomi, stemTango)
                        for (sfx in DialectInflectionNodes.suffixesAt(str, e, stemClass)) {
                            val sfxEnd = e + sfx.reading.length
                            if (sfxEnd <= reusablePrefixLength) continue
                            val yomi = stemYomi + sfx.reading
                            val c = stemCost + dialectSuffixCost
                            addOrUpdateNode(
                                graph, sfxEnd,
                                Node(
                                    l = leftId, r = sfx.r, score = c, f = c, g = c, tango = stemTango + sfx.reading,
                                    yomiUsed = yomi, len = yomi.length.toShort(), sPos = i,
                                    mozcAttributes = mozcAttributesFor(leftId) or MozcNodeAttributes.DIALECT_INFLECTION,
                                ),
                                graphNodeDedupMode, graphNodeTrace, str, "DIALECT_INFLECTION",
                            )
                        }
                    }
                }
            }

            // 3.06 方言・口語のかな語（KanaDialectNodes.kt）: 標準形の構成語の品詞 ID・語コストの和＋共通ペナルティ
            if (dialectActive && i < str.length) {
                for (entry in KanaDialectNodes.entriesAt(str, i)) {
                    val endIndex = i + entry.reading.length
                    if (endIndex <= reusablePrefixLength) continue
                    var sum = 0
                    var ok = true
                    for (part in entry.parts) {
                        val idx = yomiTrie.getNodeIndex(part.yomi, succinctBitVectorLBSYomi)
                        val termId = if (idx > 0) yomiTrie.getTermId(idx, succinctBitVectorIsLeafYomi) else -1
                        var best = Int.MAX_VALUE
                        if (termId >= 0) {
                            tokenArray.forEachDictionaryByYomiTermId(termId, succinctBitVectorTokenArray) { posTableIndex, wordCost, _ ->
                                if (tokenArray.leftIds[posTableIndex.toInt()] == part.posId && wordCost < best) best = wordCost.toInt()
                            }
                        }
                        if (best == Int.MAX_VALUE) { ok = false; break }
                        sum += best
                    }
                    if (!ok) continue
                    val l = entry.parts.first().posId
                    val r = entry.parts.last().posId
                    val cost = sum + KanaDialectNodes.penalty
                    addOrUpdateNode(
                        graph, endIndex,
                        Node(
                            l = l, r = r, score = cost, f = cost, g = cost, tango = entry.reading, yomiUsed = entry.reading,
                            len = entry.reading.length.toShort(), sPos = i, mozcAttributes = mozcAttributesFor(l),
                        ),
                        graphNodeDedupMode, graphNodeTrace, str, "DIALECT",
                    )
                }
            }

            // 3.1 英語読み辞書
            val localEnglishReadingYomiTrie = englishReadingYomiTrie
            val localEnglishReadingTangoTrie = englishReadingTangoTrie
            val localEnglishReadingTokenArray = englishReadingTokenArray
            val localEnglishReadingLBSYomi = englishReadingSuccinctBitVectorLBSYomi
            val localEnglishReadingIsLeafYomi = englishReadingSuccinctBitVectorIsLeafYomi
            val localEnglishReadingTokenBitVector = englishReadingSuccinctBitVectorTokenArray
            val localEnglishReadingTangoLBS = englishReadingSuccinctBitVectorTangoLBS
            if (
                localEnglishReadingYomiTrie != null &&
                localEnglishReadingTangoTrie != null &&
                localEnglishReadingTokenArray != null &&
                localEnglishReadingLBSYomi != null &&
                localEnglishReadingIsLeafYomi != null &&
                localEnglishReadingTokenBitVector != null &&
                localEnglishReadingTangoLBS != null
            ) {
                val commonPrefixSearchEnglishReading = prefixSearch(
                    trie = localEnglishReadingYomiTrie,
                    bitVector = localEnglishReadingLBSYomi,
                    start = i,
                )
                if (commonPrefixSearchEnglishReading.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchEnglishReading) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex <= 0) continue
                    val termId = localEnglishReadingYomiTrie.getTermId(
                        nodeIndex,
                        localEnglishReadingIsLeafYomi,
                    )
                    val endIndex = i + yomiStr.length
                    if (endIndex <= reusablePrefixLength) continue
                    localEnglishReadingTokenArray.forEachDictionaryByYomiTermId(
                        termId,
                        localEnglishReadingTokenBitVector,
                    ) { posTableIndex, wordCost, tokenNodeId ->
                        val tango = when (tokenNodeId) {
                            -2 -> yomiStr
                            -1 -> yomiStr.hiraToKata()
                            else -> localEnglishReadingTangoTrie.getLetter(
                                tokenNodeId,
                                succinctBitVector = localEnglishReadingTangoLBS,
                            )
                        }
                        val leftId = localEnglishReadingTokenArray.leftIds[posTableIndex.toInt()]
                        val cost = wordCost.toInt()
                        addOrUpdateNode(
                            graph,
                            endIndex,
                            Node(
                                l = leftId,
                                r = localEnglishReadingTokenArray.rightIds[posTableIndex.toInt()],
                                score = cost,
                                f = cost,
                                g = cost,
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(leftId),
                            ),
                            graphNodeDedupMode,
                            graphNodeTrace,
                            str,
                            "ENGLISH_READING",
                        )
                    }
                }
            }

            // 5. Wiki辞書
            if (wikiYomiTrie != null && wikiTangoTrie != null && wikiTokenArray != null &&
                succinctBitVectorLBSWikiYomi != null && succinctBitVectorWikiTangoLBS != null &&
                succinctBitVectorWikiTokenArray != null && succinctBitVectorIsLeafWikiYomi != null
            ) {
                val commonPrefixSearchWiki = prefixSearch(
                    trie = wikiYomiTrie,
                    bitVector = succinctBitVectorLBSWikiYomi,
                    start = i,
                )
                if (commonPrefixSearchWiki.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchWiki) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            wikiYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafWikiYomi)
                        val endIndex = i + yomiStr.length
                        if (endIndex <= reusablePrefixLength) continue
                        wikiTokenArray.forEachDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorWikiTokenArray
                        ) { posTableIndex, wordCost, tokenNodeId ->
                            val tango = when (tokenNodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> wikiTangoTrie.getLetter(
                                    tokenNodeId,
                                    succinctBitVector = succinctBitVectorWikiTangoLBS
                                )
                            }
                            val leftId = wikiTokenArray.leftIds[posTableIndex.toInt()]
                            val node = Node(
                                l = leftId,
                                r = wikiTokenArray.rightIds[posTableIndex.toInt()],
                                score = wordCost.toInt(),
                                f = wordCost.toInt(),
                                g = wordCost.toInt(),
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(leftId),
                            )
                            addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "WIKI")
                        }
                    }
                }
            }

            // 6. Web辞書
            if (webYomiTrie != null && webTangoTrie != null && webTokenArray != null &&
                succinctBitVectorLBSwebYomi != null && succinctBitVectorwebTangoLBS != null &&
                succinctBitVectorwebTokenArray != null && succinctBitVectorIsLeafwebYomi != null
            ) {
                val commonPrefixSearchWeb = prefixSearch(
                    trie = webYomiTrie,
                    bitVector = succinctBitVectorLBSwebYomi,
                    start = i,
                )
                if (commonPrefixSearchWeb.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchWeb) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            webYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafwebYomi)
                        val endIndex = i + yomiStr.length
                        if (endIndex <= reusablePrefixLength) continue
                        webTokenArray.forEachDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorwebTokenArray
                        ) { posTableIndex, wordCost, tokenNodeId ->
                            val tango = when (tokenNodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> webTangoTrie.getLetter(
                                    tokenNodeId,
                                    succinctBitVector = succinctBitVectorwebTangoLBS
                                )
                            }
                            val leftId = webTokenArray.leftIds[posTableIndex.toInt()]
                            val node = Node(
                                l = leftId,
                                r = webTokenArray.rightIds[posTableIndex.toInt()],
                                score = wordCost.toInt(),
                                f = wordCost.toInt(),
                                g = wordCost.toInt(),
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(leftId),
                            )
                            addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "WEB")
                        }
                    }
                }
            }

            // 7. 人名辞書
            if (personYomiTrie != null && personTangoTrie != null && personTokenArray != null &&
                succinctBitVectorLBSpersonYomi != null && succinctBitVectorpersonTangoLBS != null &&
                succinctBitVectorpersonTokenArray != null && succinctBitVectorIsLeafpersonYomi != null
            ) {
                val commonPrefixSearchPerson = prefixSearch(
                    trie = personYomiTrie,
                    bitVector = succinctBitVectorLBSpersonYomi,
                    start = i,
                )
                if (commonPrefixSearchPerson.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchPerson) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            personYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafpersonYomi)
                        val endIndex = i + yomiStr.length
                        if (endIndex <= reusablePrefixLength) continue
                        personTokenArray.forEachDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorpersonTokenArray
                        ) { posTableIndex, wordCost, tokenNodeId ->
                            val tango = when (tokenNodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> personTangoTrie.getLetter(
                                    tokenNodeId,
                                    succinctBitVector = succinctBitVectorpersonTangoLBS
                                )
                            }
                            val leftId = personTokenArray.leftIds[posTableIndex.toInt()]
                            val node = Node(
                                l = leftId,
                                r = personTokenArray.rightIds[posTableIndex.toInt()],
                                score = wordCost.toInt(),
                                f = wordCost.toInt(),
                                g = wordCost.toInt(),
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(leftId),
                            )
                            addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "PERSON")
                        }
                    }
                }
            }

            // 8. Neologd辞書
            if (neologdYomiTrie != null && neologdTangoTrie != null && neologdTokenArray != null &&
                succinctBitVectorLBSneologdYomi != null && succinctBitVectorneologdTangoLBS != null &&
                succinctBitVectorneologdTokenArray != null && succinctBitVectorIsLeafneologdYomi != null
            ) {
                val commonPrefixSearchNeologd = prefixSearch(
                    trie = neologdYomiTrie,
                    bitVector = succinctBitVectorLBSneologdYomi,
                    start = i,
                )
                if (commonPrefixSearchNeologd.isNotEmpty()) foundInAnyDictionary = true
                for (prefixResult in commonPrefixSearchNeologd) {
                    val yomiStr = prefixResult.yomi
                    val nodeIndex = prefixResult.nodeIndex
                    if (nodeIndex > 0) {
                        val termId =
                            neologdYomiTrie.getTermId(nodeIndex, succinctBitVectorIsLeafneologdYomi)
                        val endIndex = i + yomiStr.length
                        if (endIndex <= reusablePrefixLength) continue
                        neologdTokenArray.forEachDictionaryByYomiTermId(
                            termId,
                            succinctBitVectorneologdTokenArray
                        ) { posTableIndex, wordCost, tokenNodeId ->
                            val tango = when (tokenNodeId) {
                                -2 -> yomiStr
                                -1 -> yomiStr.hiraToKata()
                                else -> neologdTangoTrie.getLetter(
                                    tokenNodeId,
                                    succinctBitVector = succinctBitVectorneologdTangoLBS
                                )
                            }
                            val leftId = neologdTokenArray.leftIds[posTableIndex.toInt()]
                            val node = Node(
                                l = leftId,
                                r = neologdTokenArray.rightIds[posTableIndex.toInt()],
                                score = wordCost.toInt(),
                                f = wordCost.toInt(),
                                g = wordCost.toInt(),
                                tango = tango,
                                yomiUsed = yomiStr,
                                len = yomiStr.length.toShort(),
                                sPos = i,
                                mozcAttributes = mozcAttributesFor(leftId),
                            )
                            addOrUpdateNode(graph, endIndex, node, graphNodeDedupMode, graphNodeTrace, str, "NEOLOGD")
                        }
                    }
                }
            }

            // An append can complete a dictionary word that did not exist in the previous
            // prefix. Remove the now-invalid unknown fallback retained by the incremental graph.
            if (foundInAnyDictionary && reusablePrefixLength >= 0) {
                val fallbackEndIndex = i + 1
                val removesStablePrefixNode =
                    fallbackEndIndex < reusablePrefixLength &&
                        graph[fallbackEndIndex].orEmpty().any {
                            it.candidateSource == CandidateSource.UNKNOWN && it.sPos == i
                        }
                if (removesStablePrefixNode) {
                    // A dictionary match completed by the appended character can invalidate an
                    // older unknown fallback. Restore the complete prefix and deliberately fall
                    // back to full DP for this uncommon transition.
                    invalidateForwardDpReuseAndRestoreCompletePrefix()
                }
                graph[fallbackEndIndex]?.removeAll {
                    it.candidateSource == CandidateSource.UNKNOWN && it.sPos == i
                }
                if (graph[fallbackEndIndex]?.isEmpty() == true) {
                    graph.remove(fallbackEndIndex)
                }
            }

            // 9. Mozc-style alphabet group node / unknown fallback
            if (i < str.length) {
                val isAsciiAlphabet = str[i].isAsciiAlphabet()
                // Mozc's ImmutableConverter::AddCharacterTypeBasedNodes runs after every
                // dictionary lookup and adds a maximal same-script/form alphabet node in
                // parallel with dictionary nodes.  Our old fallback ran only when no dictionary
                // entry existed, so one-letter entries suppressed the `abc` group and the strict
                // boundary checker severed the path between letters.
                if (!isAsciiAlphabet && foundInAnyDictionary) continue
                val endExclusive = if (isAsciiAlphabet) {
                    var end = i + 1
                    while (end < str.length && str[end].isAsciiAlphabet()) end++
                    end
                } else {
                    i + 1
                }
                val yomiStr = str.substring(i, endExclusive)
                val endIndex = i + yomiStr.length
                if (endIndex <= reusablePrefixLength) continue
                val wordCost = if (isAsciiAlphabet) {
                    if (yomiStr.length > 1) MOZC_MAX_WORD_COST / 2 else MOZC_MAX_WORD_COST
                } else {
                    10000
                }
                val posId = if (isAsciiAlphabet) MOZC_UNKNOWN_POS_ID else 0
                val unknownNode = Node(
                    l = posId,
                    r = posId,
                    score = wordCost,
                    f = wordCost,
                    g = wordCost,
                    tango = yomiStr, // 読みをそのまま単語とする
                    yomiUsed = yomiStr,
                    len = yomiStr.length.toShort(),
                    sPos = i,
                    mozcAttributes = mozcAttributesFor(posId),
                    candidateSource = CandidateSource.UNKNOWN,
                )
                // 未知語は重複を考慮せずそのまま追加する
                graph.computeIfAbsent(endIndex) { mutableListOf() }.add(unknownNode)
                graphNodeTrace?.add(unknownNode.toTrace(str, endIndex, "UNKNOWN", "ADDED"))
            }
        }
        val updatedCache = CachedGraph(
            input = str,
            signature = signature,
            graph = graph.deepCopyForGraphBuild(),
        )
        cachedGraph = updatedCache
        // チップで直した区切りの一般化（LearnedBoundaries）。キャッシュは調整前のまま残し、返すグラフだけに反映する。
        if (LearnedBoundaries.enabled) {
            val leftmost = LearnedBoundaries.shared.applyTo(graph, str, learnedCostNow)
            val incremental = graph as? IncrementalGraph
            if (leftmost >= 0 && incremental != null && incremental.forwardDpReusableThroughEndIndex > leftmost) {
                incremental.forwardDpReusableThroughEndIndex = leftmost
            }
        }
        return graph
    }

    private fun Node.copyForGraphBuild(): Node = copy(
        f = score,
        g = score,
        prev = null,
        next = null,
        adjustedScore = score,
    )

    private fun MutableMap<Int, MutableList<Node>>.deepCopyForGraphBuild(): MutableMap<Int, MutableList<Node>> =
        LinkedHashMap<Int, MutableList<Node>>(size).also { copy ->
            forEach { (endIndex, nodes) ->
                copy[endIndex] = nodes.mapTo(mutableListOf()) { it.copyForGraphBuild() }
            }
        }

    private fun Char.isAsciiAlphabet(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    private fun conversionSignature(
        yomiTrie: LOUDSWithTermId,
        englishReadingYomiTrie: LOUDSWithTermId?,
        wikiYomiTrie: LOUDSWithTermId?,
        webYomiTrie: LOUDSWithTermId?,
        personYomiTrie: LOUDSWithTermId?,
        neologdYomiTrie: LOUDSWithTermId?,
        userDictionaryRepository: UserDictionaryRepository?,
        learnRepository: LearnRepository?,
        beamWidth: Int,
        graphNodeDedupMode: GraphNodeDedupMode,
        mozcNodeAttributeTable: MozcNodeAttributeTable?,
    ): Int {
        var result = System.identityHashCode(yomiTrie)
        result = 31 * result + System.identityHashCode(englishReadingYomiTrie)
        result = 31 * result + System.identityHashCode(wikiYomiTrie)
        result = 31 * result + System.identityHashCode(webYomiTrie)
        result = 31 * result + System.identityHashCode(personYomiTrie)
        result = 31 * result + System.identityHashCode(neologdYomiTrie)
        result = 31 * result + System.identityHashCode(systemUserYomiTrie)
        result = 31 * result + System.identityHashCode(userDictionaryRepository)
        result = 31 * result + (userDictionaryRepository?.conversionRevision?.hashCode() ?: 0)
        result = 31 * result + System.identityHashCode(learnRepository)
        result = 31 * result + (learnRepository?.conversionRevision?.hashCode() ?: 0)
        result = 31 * result + beamWidth
        result = 31 * result + graphNodeDedupMode.hashCode()
        result = 31 * result + System.identityHashCode(mozcNodeAttributeTable)
        return result
    }

    private companion object {
        // app/src/main/assets/id.def: 名詞,サ変接続,*,*,*,*,*
        const val MOZC_UNKNOWN_POS_ID: Short = 1841
        const val MOZC_MAX_WORD_COST = 32767
    }
}
