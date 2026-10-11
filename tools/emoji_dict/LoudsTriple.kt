// Standalone (JDK-only) reader/writer for the Sumire LOUDS dictionary triple format
// (tango LOUDS / yomi LOUDSWithTermId / TokenArray, Java-serialized, not compressed).
// Mirrors PrefixTree + Converter + PrefixTreeWithTermId + ConverterWithTermId + TokenArray.buildTokenArray.
//
//   dump  <tango> <yomi> <token> [zip]        -> TSV on stdout: reading \t surface \t posIndex \t cost
//   build <tsv> <tango> <yomi> <token>        <- TSV (same columns, no comments)
//
// Build: kotlinc LoudsTriple.kt -include-runtime -d louds-triple.jar ; java -jar louds-triple.jar ...
import java.io.*
import java.text.Normalizer
import java.util.BitSet
import java.util.zip.ZipInputStream

private fun open(path: String, zipped: Boolean): ObjectInputStream {
    var input: InputStream = BufferedInputStream(FileInputStream(path))
    if (zipped) {
        val zip = ZipInputStream(input)
        var e = zip.nextEntry
        while (e != null && e.isDirectory) e = zip.nextEntry
        input = zip
    }
    return ObjectInputStream(input)
}

/** Decodes every word of a LOUDS trie: returns map LBS-position -> word, and leaf positions in BFS order. */
private class Trie(val lbs: BitSet, val isLeaf: BitSet, val labels: CharArray) {
    val wordAtPos = HashMap<Int, String>()
    val leafPositions = ArrayList<Int>()

    init {
        // unit j (0-based) belongs to the node with one-index j (super-root unit is 0).
        // one-index k <-> labels[k]; root is one-index 1.
        val strOfOne = HashMap<Int, String>()
        strOfOne[1] = ""
        var pos = 0
        var unit = 0
        var oneIndex = 0
        val n = lbs.length() + 1
        while (pos < n) {
            while (lbs[pos]) {
                oneIndex++
                if (oneIndex > 1) {
                    val parent = strOfOne[unit] ?: ""
                    val s = parent + labels[oneIndex]
                    strOfOne[oneIndex] = s
                    if (isLeaf[pos]) {
                        wordAtPos[pos] = s
                        leafPositions.add(pos)
                    }
                }
                pos++
            }
            pos++ // the 0
            unit++
        }
    }
}

fun dump(args: List<String>) {
    val zipped = args.getOrNull(3) == "zip"
    val (tLbs, tLeaf, tLabels) = open(args[0], zipped).use {
        Triple(it.readObject() as BitSet, it.readObject() as BitSet, it.readObject() as CharArray)
    }
    val yomiIn = open(args[1], zipped)
    val yLbs = yomiIn.readObject() as BitSet
    val yLeaf = yomiIn.readObject() as BitSet
    val yLabels = yomiIn.readObject() as CharArray
    val termIds = yomiIn.readObject() as IntArray
    yomiIn.close()
    val tokIn = open(args[2], zipped)
    val pos = tokIn.readObject() as ShortArray
    val cost = tokIn.readObject() as ShortArray
    val nodeIds = tokIn.readObject() as IntArray
    val bits = tokIn.readObject() as BitSet
    tokIn.close()
    val tango = Trie(tLbs, tLeaf, tLabels)
    val yomi = Trie(yLbs, yLeaf, yLabels)
    // termId -> reading
    val readingOfTerm = HashMap<Int, String>()
    yomi.leafPositions.forEachIndexed { i, p -> readingOfTerm[termIds[i]] = yomi.wordAtPos[p]!! }
    // walk token bitvector: 0 starts a new term (term ids start at 1)
    val out = PrintWriter(BufferedWriter(OutputStreamWriter(System.out, Charsets.UTF_8)))
    var term = 0
    var tok = 0
    var i = 0
    val len = bits.length().coerceAtLeast(0)
    val totalBits = pos.size + readingOfTerm.size + 1
    while (i < maxOf(len, totalBits) && tok < pos.size) {
        if (!bits[i]) term++ else {
            val r = readingOfTerm[term] ?: "?$term"
            val surface = when (val nid = nodeIds[tok]) {
                -2 -> r
                -1 -> r.map { if (it in 'ぁ'..'ゖ') (it.code + 0x60).toChar() else it }.joinToString("")
                else -> tango.wordAtPos[nid] ?: "?node$nid"
            }
            out.println("$r\t$surface\t${pos[tok]}\t${cost[tok]}")
            tok++
        }
        i++
    }
    out.flush()
}

private class Node(val c: Char) {
    val children = LinkedHashMap<Char, Node>()
    var isWord = false
    var termId = -1
}

private fun bfs(root: Node, withTermIds: Boolean): List<Any> {
    val lbs = ArrayList<Boolean>(listOf(true, false))
    val labels = StringBuilder("  ")
    val leaf = ArrayList<Boolean>(listOf(false, false))
    val termIds = ArrayList<Int>()
    val posOfNode = HashMap<Node, Int>()
    val queue = ArrayDeque<Node>()
    queue.add(root)
    while (queue.isNotEmpty()) {
        val node = queue.removeFirst()
        for ((k, child) in node.children) {
            queue.add(child)
            posOfNode[child] = lbs.size
            lbs.add(true); labels.append(k); leaf.add(child.isWord)
            if (withTermIds && child.isWord) termIds.add(child.termId)
        }
        lbs.add(false); leaf.add(false)
    }
    fun bs(l: List<Boolean>) = BitSet(l.size).also { b -> l.forEachIndexed { i, v -> if (v) b.set(i) } }
    return listOf(bs(lbs), bs(leaf), labels.toString().toCharArray(), termIds.toIntArray(), posOfNode)
}

private fun insert(root: Node, word: String, termId: Int): Node {
    var cur = root
    for (ch in word) {
        cur = cur.children.getOrPut(ch) { Node(ch).also { it.termId = termId } }
    }
    cur.isWord = true
    return cur
}

private fun String.isHira() = isNotEmpty() && all { it in 'ぁ'..'ゖ' || it == 'ー' }
private fun String.isKata() = isNotEmpty() && all { it in 'ァ'..'ヶ' || it == 'ー' }

fun build(args: List<String>) {
    data class E(val reading: String, val surface: String, val pos: Short, val cost: Short)
    val byReading = LinkedHashMap<String, MutableList<E>>()
    File(args[0]).forEachLine(Charsets.UTF_8) { line ->
        if (line.isBlank()) return@forEachLine
        val f = line.split('\t')
        val e = E(f[0], Normalizer.normalize(f[1], Normalizer.Form.NFC), f[2].toShort(), f[3].toShort())
        byReading.getOrPut(e.reading) { ArrayList() }.add(e)
    }
    val readings = byReading.keys.sorted()
    // tango trie: surfaces in reading order
    val tRoot = Node(' ')
    val tNodes = HashMap<String, Node>()
    for (r in readings) for (e in byReading[r]!!) {
        if (e.surface == r || e.surface.isHira() || e.surface.isKata()) continue
        if (e.surface !in tNodes) tNodes[e.surface] = insert(tRoot, e.surface, -1)
    }
    val t = bfs(tRoot, false)
    @Suppress("UNCHECKED_CAST") val tPos = t[4] as Map<Node, Int>
    ObjectOutputStream(BufferedOutputStream(FileOutputStream(args[1]))).use {
        it.writeObject(t[0]); it.writeObject(t[1]); it.writeObject(t[2])
    }
    // yomi trie: sorted readings, term ids 1..n in sorted order
    val yRoot = Node(' ')
    readings.forEachIndexed { i, r -> insert(yRoot, r, i + 1).termId = i + 1 }
    val y = bfs(yRoot, true)
    ObjectOutputStream(BufferedOutputStream(FileOutputStream(args[2]))).use {
        it.writeObject(y[0]); it.writeObject(y[1]); it.writeObject(y[2]); it.writeObject(y[3])
    }
    val pos = ArrayList<Short>(); val cost = ArrayList<Short>(); val nid = ArrayList<Int>(); val bits = ArrayList<Boolean>()
    for (r in readings) {
        bits.add(false)
        for (e in byReading[r]!!) {
            bits.add(true); pos.add(e.pos); cost.add(e.cost)
            nid.add(when {
                e.surface == r || e.surface.isHira() -> -2
                e.surface.isKata() -> -1
                else -> tPos.getValue(tNodes.getValue(e.surface))
            })
        }
    }
    val bitSet = BitSet(bits.size).also { b -> bits.forEachIndexed { i, v -> if (v) b.set(i) } }
    ObjectOutputStream(BufferedOutputStream(FileOutputStream(args[3]))).use {
        it.writeObject(pos.toShortArray()); it.writeObject(cost.toShortArray())
        it.writeObject(nid.toIntArray()); it.writeObject(bitSet)
    }
    System.err.println("readings=${readings.size} tokens=${pos.size} surfaces=${tNodes.size}")
}

fun main(args: Array<String>) {
    when (args[0]) {
        "dump" -> dump(args.drop(1))
        "build" -> build(args.drop(1))
        else -> error("dump|build")
    }
}
