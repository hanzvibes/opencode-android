package dev.bidzz.opencode.ui.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
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
import androidx.compose.ui.unit.dp
import dev.bidzz.opencode.R
import dev.bidzz.opencode.core.design.OpenCodeTheme
import dev.bidzz.opencode.core.design.OpenCodeTypography
import dev.bidzz.opencode.ui.EmptyState
import dev.bidzz.opencode.ui.Glyph
import dev.bidzz.opencode.ui.IconAction
import dev.bidzz.opencode.ui.LoadingState
import java.io.File
import java.util.Locale
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
}

/**
 * Pure: which preview a path gets, by extension only. Case-insensitive;
 * a missing extension, a double extension (`report.pdf.bak`), or an
 * unknown extension is [FilePreviewKind.NONE]. Unit-tested in
 * FilePreviewKindTest.
 */
fun previewKindForPath(path: String): FilePreviewKind {
    val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (extension) {
        "jpg", "jpeg", "png", "webp", "gif", "bmp" -> FilePreviewKind.IMAGE
        "pdf" -> FilePreviewKind.PDF
        else -> FilePreviewKind.NONE
    }
}

/** Dispatches on [previewKindForPath]; NONE is a programming error here. */
@Composable
fun FilePreview(
    path: String,
    kind: FilePreviewKind,
    modifier: Modifier = Modifier,
) {
    when (kind) {
        FilePreviewKind.IMAGE -> ImagePreview(path = path, modifier = modifier)
        FilePreviewKind.PDF -> PdfPreview(path = path, modifier = modifier)
        FilePreviewKind.NONE -> Unit
    }
}

/** A decoded image with its pixel size, or null when the file is not a decodable image. */
private data class DecodedImage(
    val bitmap: Bitmap,
    val width: Int,
    val height: Int,
)

/**
 * Decodes [file] sampled down so its longest side is at most [maxDimension]
 * pixels: a 12 MP phone photo is ~48 MB as ARGB_8888, and the preview only
 * ever shows it fit-to-width. Runs on the caller's thread — call from IO.
 */
private fun decodeSampledBitmap(file: File, maxDimension: Int = 2048): DecodedImage? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val width = bounds.outWidth
    val height = bounds.outHeight
    if (width <= 0 || height <= 0) return null
    var sampleSize = 1
    while (maxOf(width, height) / sampleSize > maxDimension) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    val bitmap = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
    return DecodedImage(bitmap, bitmap.width, bitmap.height)
}

/** Image preview: sampled decode on IO, fit-to-width, scrollable both ways. */
@Composable
private fun ImagePreview(path: String, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var decoded by remember(path) { mutableStateOf<DecodedImage?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val result = withContext(Dispatchers.IO) {
            try {
                decodeSampledBitmap(File(path))
            } catch (_: Exception) {
                null
            }
        }
        if (result == null) failed = true else decoded = result
    }
    DisposableEffect(decoded) {
        onDispose { decoded?.bitmap?.recycle() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            failed -> EmptyState(
                stringResource(R.string.preview_load_failed),
                isError = true,
            )
            decoded == null -> LoadingState(stringResource(R.string.fileview_reading))
            else -> {
                val image = decoded!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState()),
                ) {
                    Image(
                        bitmap = image.bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(spacing.sm))
                    Text(
                        text = stringResource(
                            R.string.preview_image_size,
                            image.width,
                            image.height,
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
private fun PdfPreview(path: String, modifier: Modifier = Modifier) {
    val colors = OpenCodeTheme.colors
    val spacing = OpenCodeTheme.tokens.spacing
    var renderer by remember(path) { mutableStateOf<PdfRenderer?>(null) }
    var pageCount by remember(path) { mutableStateOf(0) }
    var pageIndex by remember(path) { mutableStateOf(0) }
    var bitmap by remember(path, pageIndex) { mutableStateOf<Bitmap?>(null) }
    var failed by remember(path) { mutableStateOf(false) }

    LaunchedEffect(path) {
        val opened = withContext(Dispatchers.IO) {
            try {
                val fd = ParcelFileDescriptor.open(
                    File(path),
                    ParcelFileDescriptor.MODE_READ_ONLY,
                )
                PdfRenderer(fd)
            } catch (_: Exception) {
                null
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
        val rendered = withContext(Dispatchers.IO) {
            try {
                current.openPage(pageIndex).use { page ->
                    val scale = 1600f / maxOf(page.width, page.height)
                    val width = (page.width * scale).toInt().coerceAtLeast(1)
                    val height = (page.height * scale).toInt().coerceAtLeast(1)
                    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                        .also { page.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY) }
                }
            } catch (_: Exception) {
                null
            }
        }
        if (rendered == null) failed = true else bitmap = rendered
    }

    Column(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                failed -> EmptyState(
                    stringResource(R.string.preview_load_failed),
                    isError = true,
                )
                bitmap == null -> LoadingState(stringResource(R.string.fileview_reading))
                else -> {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
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
