package com.kazumaproject.markdownhelperkeyboard.ime_service.candidatebar

/**
 * 文節チップ方式の「浮いた候補帯」（docs/CHIP_BAR.md「2026-10-30 浮いた候補帯」）の純粋な部分。
 *
 * チップ方式のとき、候補欄（`suggestionView_parent` = 上段チップ列 / 下段の予測・チップ候補、未入力時は従来のツールバー・候補行）を
 * キーボードのルート（キーボードの幅・位置・左右の余白の設定で狭く・寄せられる）から外し、IME ウィンドウのコンテナ
 * （常に画面の幅）の直下に置く。帯はキーボードの上に少し離して浮かべ、角を丸めて影を付ける。
 * キーボードのルートの高さからは候補欄の分を引く（その分は帯が持つ）。
 */
object FloatingCandidateBand {

    /** 帯の左右の余白（画面の端から）。 */
    const val SIDE_MARGIN_DP = 6

    /** 帯とキーボードの間。 */
    const val GAP_DP = 4

    /** 帯の上の余白（影が切れないように）。 */
    const val TOP_MARGIN_DP = 4

    const val CORNER_RADIUS_DP = 14

    const val ELEVATION_DP = 3

    /**
     * 帯にするか。チップ方式（2段バーが前提）の設定がオンで、記号キーボード（候補欄の上まで覆う）・
     * 物理キーボード用の候補だけの表示・全画面（抽出）表示のどれでもないとき。それ以外は従来の配置。
     */
    fun shouldFloat(
        chipBarPreference: Boolean,
        twoRowBarPreference: Boolean,
        symbolKeyboardShown: Boolean,
        candidateOnlyMode: Boolean,
        fullscreen: Boolean,
    ): Boolean = chipBarPreference && twoRowBarPreference && !symbolKeyboardShown &&
        !candidateOnlyMode && !fullscreen

    data class Geometry(
        /** キーボードのルートの高さ（候補欄の分を引いた残り、ナビゲーションバーの余白込み）。 */
        val rootHeightPx: Int,
        /** 帯の高さ（候補欄の予算そのまま）。 */
        val bandHeightPx: Int,
        /** コンテナの下端から帯の下端まで（キーボードの下余白＋ルート＋間）。 */
        val bandBottomMarginPx: Int,
        val bandSideMarginPx: Int,
        val bandTopMarginPx: Int,
        /** 帯にしたことで IME の高さが増える分（間＋上の余白）。固定高さのコンテナに足す。 */
        val extraHeightPx: Int,
    ) {
        /** コンテナの下端から帯の上端まで（コンテナ・ウィンドウの中で帯が占める高さの合計）。 */
        val totalHeightPx: Int get() = bandBottomMarginPx + bandHeightPx + bandTopMarginPx
    }

    /**
     * @param inputViewHeightPx 従来の配置でのルートの高さ（キーボード本体＋候補欄＋タブ・ツールバー＋ナビゲーションバーの余白）
     * @param candidateStripHeightPx 従来の配置でルートに取っていた候補欄の高さ（帯の高さになる）
     * @param keyboardBottomMarginPx キーボードの下余白の設定（ルートの bottomMargin）
     */
    fun geometry(
        inputViewHeightPx: Int,
        candidateStripHeightPx: Int,
        keyboardBottomMarginPx: Int,
        sideMarginPx: Int,
        gapPx: Int,
        topMarginPx: Int,
    ): Geometry {
        val band = candidateStripHeightPx.coerceAtLeast(0)
        val root = (inputViewHeightPx - band).coerceAtLeast(0)
        val gap = gapPx.coerceAtLeast(0)
        val top = topMarginPx.coerceAtLeast(0)
        return Geometry(
            rootHeightPx = root,
            bandHeightPx = band,
            bandBottomMarginPx = keyboardBottomMarginPx.coerceAtLeast(0) + root + gap,
            bandSideMarginPx = sideMarginPx.coerceAtLeast(0),
            bandTopMarginPx = top,
            extraHeightPx = gap + top,
        )
    }
}
