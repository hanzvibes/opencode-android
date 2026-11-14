package dev.bidzz.opencode.ui.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.GenerateImageInput
import dev.bidzz.opencode.core.api.dto.GenerateImageOutput
import dev.bidzz.opencode.core.api.dto.ModelInfo
import java.io.File
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

enum class GenerateMode { TEXT, IMAGE }

data class GenerateUiState(
    val models: List<ModelInfo> = emptyList(),
    val modelsLoading: Boolean = true,
    val prompt: String = "",
    val mode: GenerateMode = GenerateMode.TEXT,
    /** `ModelInfo.id` of the picked model; null means the engine default (text only). */
    val selectedModelId: String? = null,
    val aspect: String = "1:1",
    /** Null means the engine default size tier. */
    val resolution: String? = null,
    val generating: Boolean = false,
    val textResult: String? = null,
    val imageResults: List<GenerateImageOutput> = emptyList(),
    val resultCost: Double? = null,
    val resultInputTokens: Long? = null,
    val resultOutputTokens: Long? = null,
    /**
     * The last generation died with the process (audit-200 A9-3, DL-011):
     * the prompt and the previous result are restored, but nothing is
     * running anymore — the screen says so instead of showing a spinner
     * that never finishes.
     */
    val interrupted: Boolean = false,
    /**
     * What the last generation got wrong; the screen shows it once as a
     * snackbar, then clears it. A failed load of the model list goes
     * to [modelsFailure] instead.
     */
    val failure: ApiResult.Failure? = null,
    val modelsFailure: ApiResult.Failure? = null,
)

/** Image-capable models, by the engine's own `type` (`"image"`). */
internal fun List<ModelInfo>.imageModels(): List<ModelInfo> =
    filter { it.type == "image" }

/**
 * One-shot generation (mockup group 5).
 *
 * Text is stateless and uses the base configuration's default model
 * when none is picked; image needs a picked image model (the engine
 * rejects other types with `UnsupportedModelError`). Results are
 * transient — nothing is stored on the engine — and the image
 * payloads are base64, rendered by the screen.
 */
class GenerateViewModel(
    private val api: EngineApi,
    /**
     * Where the last generation lives on disk (audit-200 A9-3, DL-011).
     * Null keeps everything in memory, which is what unit tests get; the
     * app passes its files dir so a prompt — and a paid image result —
     * survive process death.
     */
    private val filesDir: File? = null,
) : ViewModel() {

    private val _state = MutableStateFlow(GenerateUiState())
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()

    init {
        // Restore the last generation, if one survived a process death.
        // The read runs off the main thread: image payloads can be
        // megabytes of base64. Nothing is restored when there is no
        // record, or when it no longer parses.
        val dir = filesDir?.let { lastGenerationDir(it) }
        if (dir != null) {
            viewModelScope.launch {
                val restored = withContext(Dispatchers.IO) { loadLastGeneration(dir) }
                if (restored != null) _state.value = restored
            }
        }
    }

    fun refreshModels() {
        _state.value = _state.value.copy(modelsLoading = true, modelsFailure = null)
        viewModelScope.launch {
            val handle = api.handle()
            if (handle == null) {
                _state.value = _state.value.copy(
                    modelsLoading = false,
                    modelsFailure = ApiResult.Failure(code = 0, message = "Engine not running"),
                )
                return@launch
            }
            when (val result = handle.models.list()) {
                is ApiResult.Success -> _state.value = _state.value.copy(
                    models = result.value.sortedBy { it.label().lowercase() },
                    modelsLoading = false,
                )
                is ApiResult.Failure -> _state.value = _state.value.copy(
                    modelsLoading = false,
                    modelsFailure = result,
                )
            }
        }
    }

    fun setPrompt(prompt: String) {
        _state.value = _state.value.copy(prompt = prompt)
    }

    fun setMode(mode: GenerateMode) {
        if (mode == _state.value.mode) return
        _state.value = _state.value.copy(
            mode = mode,
            selectedModelId = null,
            textResult = null,
            imageResults = emptyList(),
            resultCost = null,
            resultInputTokens = null,
            resultOutputTokens = null,
            failure = null,
            interrupted = false,
        )
    }

    fun setModel(modelId: String?) {
        _state.value = _state.value.copy(selectedModelId = modelId)
    }

    fun setAspect(aspect: String) {
        _state.value = _state.value.copy(aspect = aspect)
    }

    fun setResolution(resolution: String?) {
        _state.value = _state.value.copy(resolution = resolution)
    }

    fun generate() {
        val state = _state.value
        if (state.prompt.isBlank() || state.generating) return
        val handle = api.handle()
        if (handle == null) {
            _state.value = state.copy(failure = ApiResult.Failure(code = 0, message = "Engine not running"))
            return
        }
        _state.value = state.copy(generating = true, failure = null, interrupted = false)
        viewModelScope.launch {
            // Audit-200 A9-3 (DL-011): the prompt is on disk before the
            // call starts, with the previous result kept beside it — a
            // death mid-generation restores both, and the record says a
            // generation was in flight so the next launch can say it died.
            persistGeneration(_state.value)
            when (state.mode) {
                GenerateMode.TEXT -> {
                    val model = state.selectedModelId
                        ?.let { id -> state.models.firstOrNull { it.id == id } }
                        ?.ref()
                    when (val result = handle.generate.generateText(state.prompt, model)) {
                        is ApiResult.Success -> _state.value = _state.value.copy(
                            generating = false,
                            textResult = result.value,
                            imageResults = emptyList(),
                            resultCost = null,
                            resultInputTokens = null,
                            resultOutputTokens = null,
                        )
                        is ApiResult.Failure -> _state.value = _state.value.copy(
                            generating = false,
                            failure = result,
                        )
                    }
                    persistGeneration(_state.value)
                }
                GenerateMode.IMAGE -> {
                    val model = state.selectedModelId
                        ?.let { id -> state.models.firstOrNull { it.id == id } }
                        ?.ref()
                    if (model == null) {
                        _state.value = _state.value.copy(
                            generating = false,
                            failure = ApiResult.Failure(code = 0, message = "Pick an image model first"),
                        )
                        persistGeneration(_state.value)
                        return@launch
                    }
                    when (val result = handle.generate.generateImage(
                        GenerateImageInput(
                            model = model,
                            prompt = state.prompt,
                            aspect = state.aspect,
                            resolution = state.resolution,
                        ),
                    )) {
                        is ApiResult.Success -> _state.value = _state.value.copy(
                            generating = false,
                            textResult = null,
                            imageResults = result.value.images,
                            resultCost = result.value.cost,
                            resultInputTokens = result.value.tokens?.input,
                            resultOutputTokens = result.value.tokens?.output,
                        )
                        is ApiResult.Failure -> _state.value = _state.value.copy(
                            generating = false,
                            failure = result,
                        )
                    }
                    persistGeneration(_state.value)
                }
            }
        }
    }

    fun clearFailure() {
        _state.value = _state.value.copy(failure = null)
    }

    /** Writes [state] to disk when persistence is configured (DL-011). */
    private suspend fun persistGeneration(state: GenerateUiState) {
        val dir = filesDir?.let { lastGenerationDir(it) } ?: return
        withContext(Dispatchers.IO) { saveLastGeneration(state, dir) }
    }
}

/** The directory holding the last generation's record and image files. */
internal fun lastGenerationDir(filesDir: File): File = File(filesDir, "generate-last")

/** File name for the [index]-th result image, from its MIME type. */
internal fun generationImageFileName(index: Int, mime: String): String {
    val extension = when (mime.lowercase()) {
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        else -> "bin"
    }
    return "image-$index.$extension"
}

/**
 * Saves one generation's prompt, settings and result (audit-200 A9-3,
 * DL-011): the prompt and the `generating` flag go down before the call
 * starts, the result overwrites them when it arrives. Image payloads
 * are files, not JSON — the record only names them. A failure to write
 * is swallowed: persistence must never break generating.
 */
internal fun saveLastGeneration(state: GenerateUiState, dir: File) {
    try {
        dir.mkdirs()
        dir.listFiles()?.forEach { it.delete() }
        val images = JSONArray()
        state.imageResults.forEachIndexed { index, image ->
            val fileName = generationImageFileName(index, image.mime)
            val bytes = runCatching { Base64.getDecoder().decode(image.data) }.getOrNull()
                ?: return@forEachIndexed
            File(dir, fileName).writeBytes(bytes)
            images.put(
                JSONObject()
                    .put("type", image.type)
                    .put("mime", image.mime)
                    .put("name", image.name)
                    .put("file", fileName),
            )
        }
        val record = JSONObject()
            .put("prompt", state.prompt)
            .put("mode", state.mode.name)
            .put("modelId", state.selectedModelId)
            .put("aspect", state.aspect)
            .put("resolution", state.resolution)
            .put("generating", state.generating)
            .put("text", state.textResult)
            .put("cost", state.resultCost)
            .put("inputTokens", state.resultInputTokens)
            .put("outputTokens", state.resultOutputTokens)
            .put("images", images)
        File(dir, "last.json").writeText(record.toString())
    } catch (_: Exception) {
        // Persistence is best-effort: a full disk or a bad payload must
        // not fail the generation that just ran.
    }
}

/**
 * Reads the last generation back, or null when there is none or it no
 * longer parses (audit-200 A9-3, DL-011). A record that still says a
 * generation was in flight died with the process: the prompt and the
 * previous result come back, `generating` stays false, and [GenerateUiState.interrupted]
 * says so. An image whose file is gone is skipped, not fatal.
 */
internal fun loadLastGeneration(dir: File): GenerateUiState? {
    val record = try {
        JSONObject(File(dir, "last.json").readText())
    } catch (_: Exception) {
        return null
    }
    val images = mutableListOf<GenerateImageOutput>()
    val listed = runCatching { record.getJSONArray("images") }.getOrNull()
    if (listed != null) {
        for (i in 0 until listed.length()) {
            val entry = runCatching { listed.getJSONObject(i) }.getOrNull() ?: continue
            val bytes = runCatching { File(dir, entry.getString("file")).readBytes() }.getOrNull()
                ?: continue
            images += GenerateImageOutput(
                type = entry.optString("type"),
                data = Base64.getEncoder().encodeToString(bytes),
                mime = entry.optString("mime"),
                name = entry.optString("name").takeIf { it.isNotEmpty() },
            )
        }
    }
    return GenerateUiState(
        prompt = record.optString("prompt"),
        mode = runCatching { GenerateMode.valueOf(record.optString("mode")) }.getOrDefault(GenerateMode.TEXT),
        selectedModelId = record.optString("modelId").takeIf { it.isNotEmpty() },
        aspect = record.optString("aspect").takeIf { it.isNotEmpty() } ?: "1:1",
        resolution = record.optString("resolution").takeIf { it.isNotEmpty() },
        textResult = record.optString("text").takeIf { it.isNotEmpty() },
        imageResults = images,
        resultCost = if (record.isNull("cost")) null else record.optDouble("cost"),
        resultInputTokens = if (record.isNull("inputTokens")) null else record.optLong("inputTokens"),
        resultOutputTokens = if (record.isNull("outputTokens")) null else record.optLong("outputTokens"),
        interrupted = record.optBoolean("generating"),
    )
}
