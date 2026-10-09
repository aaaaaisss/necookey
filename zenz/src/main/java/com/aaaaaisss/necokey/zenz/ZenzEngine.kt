package com.aaaaaisss.necokey.zenz

object ZenzEngine {
    init {
        System.loadLibrary("necookey_zenz")
    }

    external fun initModel(modelPath: String): Boolean
    external fun cancelCurrent()
    external fun closeModel()
    external fun setRuntimeConfig(nCtx: Int, nThreads: Int)
    external fun scoreCandidatesV32(
        profile: String?,
        topic: String?,
        style: String?,
        preference: String?,
        leftContext: String?,
        rightContext: String?,
        input: String?,
        candidates: Array<String>
    ): FloatArray
}
