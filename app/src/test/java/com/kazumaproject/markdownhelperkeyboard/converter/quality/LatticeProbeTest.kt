package com.kazumaproject.markdownhelperkeyboard.converter.quality

import com.kazumaproject.markdownhelperkeyboard.converter.path_algorithm.LatticeLmCost
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 診断用（ローカル JVM のみ）: NK_PROBE=読み,読み,... の格子ノード（読み・表記・左右 ID・語コスト・出所）と実機上段の候補を
 * NK_PROBE_OUT に書く。NK_PROBE_YOMI=読み,... があればその読みのノードだけ（無ければ全ノード）。
 */
class LatticeProbeTest {
    @Test
    fun probe() {
        val readings = System.getenv("NK_PROBE")?.split(',')?.filter { it.isNotBlank() }.orEmpty()
        val outPath = System.getenv("NK_PROBE_OUT")
        assumeTrue(readings.isNotEmpty() && !outPath.isNullOrBlank())
        val yomiFilter = System.getenv("NK_PROBE_YOMI")?.split(',')?.filter { it.isNotBlank() }?.toSet()
        LatticeLmCost.table = DeviceConfigConversion.latticeTableFile()?.inputStream()?.use { LatticeLmCost.load(it) }
        val dc = DeviceConfigConversion()
        val engine = dc.newEngine()
        File(outPath!!).bufferedWriter().use { w ->
            for (r in readings) {
                w.write("## $r\n")
                dc.deviceTopRow(engine, r).forEachIndexed { i, c -> w.write("top$i\t${c.string}\t${c.score}\n") }
                val tr = engine.convertWithTrace(r)
                tr.graphNodes.filter { yomiFilter == null || it.yomiUsed in yomiFilter }
                    .sortedWith(compareBy({ it.startPos }, { it.endPos }, { it.wordCost }))
                    .forEach { n ->
                        w.write("node\t${n.startPos}-${n.endPos}\t${n.yomiUsed}\t${n.tango}\t${n.leftId}/${n.rightId}\t${n.wordCost}\t${n.source}\t${n.event}\n")
                    }
            }
        }
        LatticeLmCost.table = null
    }
}
