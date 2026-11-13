package dev.bidzz.opencode.ui.generate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.bidzz.opencode.api.EngineApi
import dev.bidzz.opencode.core.api.ApiResult
import dev.bidzz.opencode.core.api.dto.GenerateImageInput
import dev.bidzz.opencode.core.api.dto.GenerateImageOutput
import dev.bidzz.opencode.core.api.dto.ModelInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

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
class GenerateViewModel(private val api: EngineApi) : ViewModel() {

    private val _state = MutableStateFlow(GenerateUiState())
    val state: StateFlow<GenerateUiState> = _state.asStateFlow()

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
        _state.value = state.copy(generating = true, failure = null)
        viewModelScope.launch {
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
                }
            }
        }
    }

    fun clearFailure() {
        _state.value = _state.value.copy(failure = null)
    }
}
