package com.kazumaproject.markdownhelperkeyboard.converter.candidatebar

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Source-level contract for the parts of zenz_bridge.cpp the bunsetsu gate relies on. The native
 * library cannot run in a JVM unit test, so this pins the scoring shape instead.
 */
class ZenzBridgeScoringContractTest {

    private val source: String by lazy {
        listOf(
            File("../zenz/src/main/cpp/zenz_bridge.cpp"),
            File("zenz/src/main/cpp/zenz_bridge.cpp"),
        ).firstOrNull { it.isFile }?.readText() ?: run {
            fail("zenz_bridge.cpp was not found")
            ""
        }
    }

    private fun function(signature: String): String {
        val start = source.indexOf(signature)
        assertTrue("$signature must exist", start >= 0)
        val bodyStart = source.indexOf('{', start)
        var depth = 0
        for (i in bodyStart until source.length) {
            when (source[i]) {
                '{' -> depth++
                '}' -> if (--depth == 0) return source.substring(start, i + 1)
            }
        }
        fail("unbalanced braces in $signature")
        return ""
    }

    @Test
    fun jniStringsAreDecodedFromUtf16NotModifiedUtf8() {
        val body = function("static std::string jstring_to_string(")
        assertTrue(body.contains("GetStringRegion"))
        assertFalse("modified UTF-8 breaks supplementary characters", body.contains("GetStringUTFChars"))
        assertTrue("surrogate pairs must be combined", body.contains("0xDC00"))
    }

    @Test
    fun scoringUsesLogSoftmaxShiftedByOneAndLengthNormalization() {
        val body = function("static float score_candidate_avg_logprob_reuse_prompt_locked(")
        // The last prompt token is re-decoded so its logits predict the first candidate token.
        assertTrue(body.contains("batch.token[batch.n_tokens] = prompt_tokens.back();"))
        assertTrue(body.contains("logits[expected_token] - max_logit - (float) log(sum_exp)"))
        assertTrue(body.contains("all_logits + ((size_t) i * (size_t) n_vocab)"))
        assertTrue(body.contains("total_score / (float) candidate_tokens.size()"))
        // Each candidate is its own sequence: the KV suffix after the prompt is dropped first.
        assertTrue(body.contains("llama_kv_cache_seq_rm(ctx, 0, suffix_start, -1)"))
    }

    @Test
    fun scoredSequenceEndsWithEos() {
        val body = function("static jfloatArray score_candidates_with_context(")
        assertTrue(body.contains("llama_vocab_eos(g_vocab)"))
        assertTrue(body.contains("push_back(eos)"))
    }
}
