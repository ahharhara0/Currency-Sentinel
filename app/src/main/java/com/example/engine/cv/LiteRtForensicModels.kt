package com.example.engine.cv

import android.content.Context
import com.google.ai.edge.litert.Accelerator
import com.google.ai.edge.litert.CompiledModel
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.Executors
import kotlin.math.exp

data class LiteRtIdentityPrediction(
    val probabilities: Map<String, Float>,
    val topLabel: String,
    val confidence: Float,
    val ranOnLiteRt: Boolean
)

data class LiteRtQualityPrediction(
    val cleanUsableProb: Float,
    val wornTolerableProb: Float,
    val compromisedOccludedProb: Float,
    val ranOnLiteRt: Boolean
)

class LiteRtForensicModels(private val context: Context) : Closeable {

    private val labels = listOf("SAR_COTTON", "SAR_POLYMER", "USD_MODERN", "USD_CLASSIC")

    // LiteRT runtime mandate: dedicate a single-thread background dispatcher for init, run, and close
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "LiteRT-Forensic-Thread").apply { isDaemon = true }
    }
    private val liteRtDispatcher = executor.asCoroutineDispatcher()

    private var bieModel: CompiledModel? = null
    private var cefModel: CompiledModel? = null
    private var initAttempted = false

    @Volatile
    var isNativeLiteRtActive: Boolean = false
        private set

    private fun ensureInitializedOnLiteRtThread() {
        if (initAttempted) return
        initAttempted = true
        runCatching {
            val options = CompiledModel.Options(Accelerator.CPU)
            bieModel = CompiledModel.create(context.assets, "models/bie_banknote_classifier.tflite", options)
            cefModel = CompiledModel.create(context.assets, "models/condition_quality_mlp.tflite", options)
            isNativeLiteRtActive = true
        }.onFailure {
            bieModel?.close()
            cefModel?.close()
            bieModel = null
            cefModel = null
            isNativeLiteRtActive = false
        }
    }

    suspend fun predictIdentity(features12: FloatArray): LiteRtIdentityPrediction =
        withContext(liteRtDispatcher) {
            ensureInitializedOnLiteRtThread()
            val model = bieModel
            val rawOut = if (model != null && features12.size == 12) {
                runCatching {
                    val inputs = model.createInputBuffers()
                    val outputs = model.createOutputBuffers()
                    try {
                        inputs[0].writeFloat(features12)
                        model.run(inputs, outputs)
                        outputs[0].readFloat()
                    } finally {
                        inputs.forEach { it.close() }
                        outputs.forEach { it.close() }
                    }
                }.getOrNull()
            } else null

            val probs = if (rawOut != null && rawOut.size == 4) {
                softmax(rawOut)
            } else {
                fallbackIdentitySoftmax(features12)
            }
            val map = labels.indices.associate { idx -> labels[idx] to probs[idx] }
            val topIdx = probs.indices.maxByOrNull { probs[it] } ?: 0
            LiteRtIdentityPrediction(
                probabilities = map,
                topLabel = labels[topIdx],
                confidence = probs[topIdx],
                ranOnLiteRt = rawOut != null
            )
        }

    suspend fun predictConditionQuality(features8: FloatArray): LiteRtQualityPrediction =
        withContext(liteRtDispatcher) {
            ensureInitializedOnLiteRtThread()
            val model = cefModel
            val rawOut = if (model != null && features8.size == 8) {
                runCatching {
                    val inputs = model.createInputBuffers()
                    val outputs = model.createOutputBuffers()
                    try {
                        inputs[0].writeFloat(features8)
                        model.run(inputs, outputs)
                        outputs[0].readFloat()
                    } finally {
                        inputs.forEach { it.close() }
                        outputs.forEach { it.close() }
                    }
                }.getOrNull()
            } else null

            val probs = if (rawOut != null && rawOut.size == 3) {
                softmax(rawOut)
            } else {
                fallbackQualitySoftmax(features8)
            }
            LiteRtQualityPrediction(
                cleanUsableProb = probs[0],
                wornTolerableProb = probs[1],
                compromisedOccludedProb = probs[2],
                ranOnLiteRt = rawOut != null
            )
        }

    private fun fallbackIdentitySoftmax(f: FloatArray): FloatArray {
        val sat = f.getOrElse(1) { 0.2f }
        val polymerHint = f.getOrElse(8) { 0.1f }
        val greenTint = f.getOrElse(5) { 0.2f }
        val blueCyan = f.getOrElse(6) { 0.1f } + f.getOrElse(7) { 0.1f }
        val logits = floatArrayOf(
            0.3f + sat * 0.9f + blueCyan * 0.4f, // SAR_COTTON
            0.1f + polymerHint * 1.4f + sat * 0.5f, // SAR_POLYMER
            0.35f + greenTint * 0.7f + blueCyan * 0.5f, // USD_MODERN
            0.25f + (1f - sat) * 0.8f + greenTint * 0.4f // USD_CLASSIC
        )
        return softmax(logits)
    }

    private fun fallbackQualitySoftmax(f: FloatArray): FloatArray {
        val blur = f.getOrElse(0) { 0.7f }
        val glare = f.getOrElse(1) { 0.05f }
        val fold = f.getOrElse(2) { 0.05f }
        val wear = f.getOrElse(3) { 0.1f }
        val logits = floatArrayOf(
            blur * 1.6f - glare * 1.8f - fold * 1.5f,
            wear * 1.4f + 0.35f,
            glare * 2.1f + fold * 1.9f + (1f - blur) * 1.1f
        )
        return softmax(logits)
    }

    private fun softmax(logits: FloatArray): FloatArray {
        val maxVal = logits.maxOrNull() ?: 0f
        val exps = FloatArray(logits.size)
        var sum = 0f
        for (i in logits.indices) {
            val e = exp((logits[i] - maxVal).toDouble()).toFloat()
            exps[i] = e
            sum += e
        }
        val safeSum = if (sum <= 1e-6f) 1f else sum
        for (i in exps.indices) exps[i] = exps[i] / safeSum
        return exps
    }

    override fun close() {
        executor.execute {
            runCatching {
                bieModel?.close()
                cefModel?.close()
            }
            bieModel = null
            cefModel = null
        }
        executor.shutdown()
    }
}
