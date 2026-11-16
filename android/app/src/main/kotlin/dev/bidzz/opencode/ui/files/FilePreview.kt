package dev.bidzz.opencode.ui.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.ParcelFileDescriptor
import android.widget.ImageView
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.HatchFilePreviewUnavailableCard
import dev.bidzz.opencode.ui.IconAction
import dev.bidzz.opencode.ui.LoadingState
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * In-file preview (Batch 7, Native C batch 2): images and PDFs open
 * inside the file viewer instead of failing the strict UTF-8 text load.
 *
 * The viewer ([FileViewScreen]) branches here when [previewKindForPath]
 * is not [FilePreviewKind.NONE]; the text-load failure the view model
 * still produces for a binary file is ignored by that branch. Skipping
 * the text load entirely is a follow-up once the Fase 1b save-file work
 * (which owns the view model) lands — the wasted read is capped by the
 * large-file guard, so it cannot OOM the app in the meantime.
 */
enum class FilePreviewKind {
    NONE,
    IMAGE,
    PDF,
    /**
     * A known image format this device cannot decode — AVIF below API 34,
     * TIFF, ICO (audit-200 D11, m-722). The viewer says so plainly
     * instead of opening the binary as text.
     */
    UNSUPPORTED,
}

/**
 * Pure: which preview a path gets, by extension only. Case-insensitive;
 * a missing extension, a double extension (`report.pdf.bak`), or an
 * unknown extension is [FilePreviewKind.NONE]. Unit-tested in
 * FilePreviewKindTest.
 *
 * Audit-200 D11 (m-722): HEIC/HEIF decode since API 26 (minSdk 29), so
 * they are IMAGE unconditionally. AVIF decodes only on API 34+ — below
 * that it is UNSUPPORTED, not NONE, so the viewer explains instead of
 * showing binary as text. TIFF and ICO are UNSUPPORTED for the same
 * reason (neither BitmapFactory nor ImageDecoder handles them). SVG
 * stays NONE on purpose: it is XML text, and the text viewer already
 * shows it.
 *
 * [sdkInt] is a parameter (defaulting to the device's API level) so the
 * AVIF gate is unit-testable without a device.
 */
fun previewKindForPath(path: String, sdkInt: Int = Build.VERSION.SDK_INT): FilePreviewKind {
    val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (extension) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif" -> FilePreviewKind.IMAGE
        "avif" -> if (sdkInt >= 34) FilePreviewKind.IMAGE else FilePreviewKind.UNSUPPORTED
        "pdf" -> FilePreviewKind.PDF
        "tif", "tiff", "ico" -> FilePreviewKind.UNSUPPORTED
        else -> FilePreviewKind.NONE
    }
}

/**
 * Pure: resolves a workspace-relative path against the workspace root.
 * An absolute path is already complete and passes through untouched.
 *
 * Audit-200 B7 (M-022): the preview used to read `File(path)` directly,
 * but the viewer hands it workspace-relative paths (the same strings the
 * engine serves text for) — resolved against the app process's working
 * directory, they never pointed at the file, so no image/PDF preview
 * ever opened. Unit-tested in FilePreviewPathTest.
 */
fun resolveWorkspaceFile(workspaceRoot: String, path: String): File {
    val file = File(path)
    return if (file.isAbsolute) file else File(workspaceRoot, path)
}

/** Dispatches on [previewKindForPath]; NONE is a programming error here. */
@Composable
fun FilePreview(
    path: String,
    kind: FilePreviewKind,
    modifier: Modifier = Modifier,
    /** Workspace root the [path] is relative to (M-022); "" keeps the raw path. */
    workspaceRoot: String = "",
) {
    // Audit-200 B7 (M-022): resolve against the workspace root first —
    // a bare File(path) points at the process working directory.
    val file = remember(path, workspaceRoot) { resolveWorkspaceFile(workspaceRoot, path) }
    when (kind) {
        FilePreviewKind.IMAGE -> ImagePreview(file = file, modifier = modifier)
        FilePreviewKind.PDF -> PdfPreview(file = file, modifier = modifier)
        // Audit-200 D11 (m-722): a known-but-undecodable format gets an
        // honest message, not the text view's binary garbage — and no
        // retry, since retrying cannot make the decoder appear.
        FilePreviewKind.UNSUPPORTED -> HatchFilePreviewUnavailableCard(
            message = stringResource(R.string.preview_unsupported_format),
            modifier = modifier,
        )
        FilePreviewKind.NONE -> Unit
    }
}

/**
 * A decoded image. [width]/[height] are the file's ORIGINAL dimensions
 * (audit-200 D11, m-715): the bitmap itself is sampled down, but the
 * footer and the TalkBack description must name the real size, not the
 * sampling size. Null when the file is not a decodable image.
 */
private data class DecodedImage(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
)

/**
 * Audit-200 A4 (C-005): runs one preview step (image decode, PDF open,
 * page render) and turns a failure into null instead of crashing.
 * An out-of-memory allocation is a failure like any other here — the
 * preview shows "Could not open this preview" instead of killing the
 * app. Cancellation is NOT swallowed: a cancelled render must still
 * cancel, so its caller can recycle the bitmap it already made.
 */
internal fun <T> previewOrNull(block: () -> T): T? {
    return try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (_: OutOfMemoryError) {
        null
    } catch (_: Exception) {
        null
    }
}

/**
 * Decodes [file] sampled down so its longest side is at most [maxDimension]
 * pixels: a 12 MP phone photo is ~48 MB as ARGB_8888, and the preview only
 * ever shows it fit-to-width. Decoded as RGB_565 — half that again, and a
 * preview never needs an alpha channel. Runs on the caller's thread —
 * call from IO.
 */
private fun decodeSampledBitmap(file: File, maxDimension: Int = 2048): DecodedImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return null
    var sampleSize = 1
    while (maxOf(width, height) / sampleSize > maxDimension) sampleSize *= 2
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.RGB_565
    }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
    // Audit-200 D11 (m-715): the footer shows the ORIGINAL size — the
    // sampled bitmap's own width/height are the downscaled ones.
    return DecodedImage(bitmap, width, height)
}

/**
 * An animated image that [ImageDecoder] decoded. [width]/[height] are
 * the file's ORIGINAL dimensions (audit-200 D11, m-715) — the drawable
 * itself is target-sized by [ImageDecoder.setTargetSize], so its
 * intrinsic size is the sampled one and must not be shown as the size.
 */
internal data class AnimatedPreview(
    val drawable: AnimatedImageDrawable,
    val width: Int,
    val height: Int,
)

/**
 * Pure: only GIF and WebP can animate in this preview set — every other
 * image format goes straight to the sampled bitmap path. Unit-tested.
 */
internal fun isAnimatedCandidate(path: String): Boolean {
    val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return extension == "gif" || extension == "webp"
}

/**
 * Audit-200 D9 (m-718): GIF/WebP animation used to show only the first
 * frame — [BitmapFactory] decodes one frame, and nothing in the app
 * played animations. [ImageDecoder] (API 28+, minSdk 29) decodes the
 * animation; the result decides, not the extension — a static WebP
 * decodes to a plain drawable and falls back to the sampled bitmap
 * path. Frames are capped at [maxDimension] on the long side via
 * [ImageDecoder.setTargetSize], like the bitmap sampler. Runs on the
 * caller's thread — call from IO.
 */
internal fun decodeAnimatedDrawable(file: File, maxDimension: Int = 2048): AnimatedPreview? {
    // Audit-200 D11 (m-715): the original size is read before
    // setTargetSize — after it, the drawable's intrinsic size is the
    // sampled (target) size, not the file's real one.
    var originalWidth = 0
    var originalHeight = 0
    val drawable = previewOrNull {
        ImageDecoder.decodeDrawable(ImageDecoder.createSource(file)) { decoder, info, _ ->
            val size = info.size
            originalWidth = size.width
            originalHeight = size.height
            val scale = (maxDimension.toFloat() / maxOf(size.width, size.height)).coerceAtMost(1f)
            decoder.setTargetSize(
                (size.width * scale).toInt().coerceAtLeast(1),
                (size.height * scale).toInt().coerceAtLeast(1),
            )
        }
    } ?: return null
    val animated = drawable as? AnimatedImageDrawable ?: return null
    if (originalWidth <= 0 || originalHeight <= 0) return null
    return AnimatedPreview(animated, originalWidth, originalHeight)
}

/** Image preview: sampled decode on IO, fit-to-width, scrollable both ways. */
@Composable
private fun ImagePreview(file: File, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var decoded by remember(file) { mutableStateOf<DecodedImage?>(null) }
    var animated by remember(file) { mutableStateOf<AnimatedPreview?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    // Audit-200 C13 (M-008): a decode failure is retryable in place — a
    // file that was still being written, or briefly unreadable, needs no
    // exit-and-reopen to try again. Bumping the attempt re-runs the decode
    // effect below.
    var retryAttempt by remember(file) { mutableStateOf(0) }

    LaunchedEffect(file, retryAttempt) {
        val (animation, bitmap) = withContext(Dispatchers.IO) {
            // Audit-200 D9 (m-718): an animated GIF/WebP used to show
            // only its first frame. Try the animation first; a static
            // file decodes to a plain drawable and falls back to the
            // sampled bitmap below.
            val animation = if (isAnimatedCandidate(file.name)) {
                decodeAnimatedDrawable(file)
            } else {
                null
            }
            val bitmap = if (animation == null) {
                previewOrNull { decodeSampledBitmap(file) }
            } else {
                null
            }
            animation to bitmap
        }
        if (animation != null) {
            animated = animation
        } else if (bitmap != null) {
            decoded = bitmap
        } else {
            failed = true
        }
    }
    DisposableEffect(decoded) {
        onDispose { decoded?.bitmap?.recycle() }
    }
    // The animation runs only while the preview owns it.
    DisposableEffect(animated) {
        val current = animated?.drawable
        current?.start()
        onDispose { current?.stop() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            failed -> HatchFilePreviewUnavailableCard(
                message = stringResource(R.string.preview_load_failed),
                onRetry = {
                    failed = false
                    decoded = null
                    animated = null
                    retryAttempt++
                },
            )
            decoded == null && animated == null -> LoadingState(stringResource(R.string.fileview_reading))
            else -> {
                val image = decoded
                val animation = animated
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                ) {
                    if (animation != null) {
                        // Audit-200 D9 (m-718): there is no drawable-painter
                        // helper in Compose 1.7.6 — an AnimatedImageDrawable
                        // is shown through an ImageView, which plays it like
                        // any view.
                        // The description resolves in composition: a
                        // semantics block is not a @Composable scope.
                        val animationDescription = stringResource(
                            R.string.preview_image_desc,
                            file.name,
                            animation.width,
                            animation.height,
                        )
                        AndroidView(
                            factory = { context ->
                                ImageView(context).apply {
                                    adjustViewBounds = true
                                    setImageDrawable(animation.drawable)
                                }
                            },
                            update = { it.setImageDrawable(animation.drawable) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = animationDescription },
                        )
                    } else if (image != null) {
                        Image(
                            bitmap = image.bitmap.asImageBitmap(),
                            // Audit-200 D3 (m-031): TalkBack skipped the
                            // preview entirely — name the file and its size.
                            contentDescription = stringResource(
                                R.string.preview_image_desc,
                                file.name,
                                image.width,
                                image.height,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(spacing.sm))
                    val (shownWidth, shownHeight) = if (animation != null) {
                        animation.width to animation.height
                    } else {
                        image!!.width to image.height
                    }
                    Text(
                        text = stringResource(
                            R.string.preview_image_size,
                            shownWidth,
                            shownHeight,
                        ),
                        style = OpenCodeTypography.caption,
                        color = colors.onSurfaceMuted,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(bottom = spacing.md),
                    )
                }
            }
        }
    }
}

/**
 * PDF preview on the framework [PdfRenderer] (API 21+, minSdk 29): one
 * page at a time, rendered at up to 1600px on the long side for display.
 * The renderer is opened once per path and closed when the path changes
 * or the preview leaves the tree; the page bitmap is recycled when the
 * page changes. A corrupt or empty file lands in the failed state.
 */
@Composable
private fun PdfPreview(file: File, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var renderer by remember(file) { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember(file) { mutableStateOf(0) }
    var pageIndex by remember(file) { mutableStateOf(0) }
    var bitmap by remember(file, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(file) { mutableStateOf(false) }
    // Audit-200 C13 (M-008): same in-place retry as ImagePreview — a
    // corrupt-looking open (or a page that failed to render) can be
    // retried without leaving the file.
    var retryAttempt by remember(file) { mutableStateOf(0) }

    LaunchedEffect(file, retryAttempt) {
        val opened = withContext(Dispatchers.IO) {
            previewOrNull {
                val fd = ParcelFileDescriptor.open(
                    file,
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
                PdfRenderer(fd)
            }
        }
        if (opened == null) {
            failed = true
        } else {
            renderer = opened
            pageCount = opened.pageCount
            if (opened.pageCount == 0) failed = true
        }
    }
    // Keyed on the renderer itself, not the path: the effect body only
    // re-runs (and its onDispose only captures the current renderer) when
    // the renderer changes, so the old renderer is closed exactly when it
    // is replaced or the preview leaves the tree. Keying on `path` would
    // capture the null renderer from mount time and leak the file
    // descriptor on every file switch.
    DisposableEffect(renderer) {
        val current = renderer
        onDispose { current?.close() }
    }
    DisposableEffect(bitmap) {
        onDispose { bitmap?.recycle() }
    }

    LaunchedEffect(renderer, pageIndex) {
        val current = renderer ?: return@LaunchedEffect
        if (pageIndex >= current.pageCount) return@LaunchedEffect
        // Audit-200 A4 (C-005): a render cancelled mid-flight (fast page
        // flips) leaves behind the bitmap it already made — it was never
        // adopted by state, so the DisposableEffect below never sees it.
        // Hold it here and recycle it unless the state adopts it. The
        // assignment happens INSIDE withContext: if the effect is
        // cancelled after the render finishes, withContext throws before
        // it returns, and an assignment outside would never run.
        var produced: Bitmap? = null
        try {
            withContext(Dispatchers.IO) {
                produced = previewOrNull {
                    current.openPage(pageIndex).use { page ->
                        val scale = 1600f / maxOf(page.width, page.height)
                        val width = (page.width * scale).toInt().coerceAtLeast(1)
                        val height = (page.height * scale).toInt().coerceAtLeast(1)
                        // RGB_565: half the memory of ARGB_8888; a preview
                        // never needs an alpha channel.
                        Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                            .also { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                    }
                }
            }
            if (produced == null) {
                failed = true
            } else {
                bitmap = produced
                produced = null // adopted by state; its DisposableEffect owns it now
            }
        } catch (e: CancellationException) {
            produced?.recycle()
            throw e
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                failed -> HatchFilePreviewUnavailableCard(
                    message = stringResource(R.string.preview_load_failed),
                    onRetry = {
                        failed = false
                        renderer = null
                        bitmap = null
                        pageIndex = 0
                        pageCount = 0
                        retryAttempt++
                    },
                )
                bitmap == null -> LoadingState(stringResource(R.string.fileview_reading))
                else -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        // Audit-200 D3 (m-031): TalkBack skipped the page
                        // entirely — name the file and the page number.
                        // (The page is an image; its text stays unread —
                        // that is 146-F2, a separate cluster.)
                        contentDescription = stringResource(
                            R.string.preview_pdf_page_desc,
                            file.name,
                            pageIndex + 1,
                            pageCount,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .horizontalScroll(rememberScrollState()),
                    )
                }
            }
        }
        if (!failed && pageCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = spacing.sm),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(
                    glyph = Glyph.ChevronLeft,
                    description = stringResource(R.string.preview_page_prev),
                    onClick = { if (pageIndex > 0) pageIndex-- },
                    enabled = pageIndex > 0,
                )
                Text(
                    text = stringResource(R.string.preview_page_of, pageIndex + 1, pageCount),
                    style = OpenCodeTypography.bodySmall,
                    color = colors.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = spacing.md),
                )
                IconAction(
                    glyph = Glyph.ChevronRight,
                    description = stringResource(R.string.preview_page_next),
                    onClick = { if (pageIndex < pageCount - 1) pageIndex++ },
                    enabled = pageIndex < pageCount - 1,
                )
            }
        }
    }
}
