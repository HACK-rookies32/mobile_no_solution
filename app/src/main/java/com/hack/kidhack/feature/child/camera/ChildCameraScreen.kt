package com.hack.kidhack.feature.child.camera

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import com.hack.kidhack.R
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.core.common.KoreanParticles
import com.hack.kidhack.core.designsystem.component.BrandGradientHeader
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.core.permission.KidhackPermissions
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import java.io.File



private const val TAB_CAMERA = "camera"
private const val TAB_GALLERY = "gallery"

/**
 * 자녀 카메라 화면.
 *
 * [requestId] 가 있으면 부모의 촬영 요청으로 열린 것이다. 이때는 갤러리 탭을 잠그고
 * 요청 배너를 띄우며, 셔터를 누르는 즉시 촬영본이 부모 계정으로 전송된다.
 *
 * [openGallery] 는 마이페이지 「카메라 갤러리」처럼 처음부터 갤러리를 보여 줄 때 쓴다.
 * 부모 요청이 살아 있으면 요청이 우선이라 카메라로 되돌아간다.
 */
@Composable
fun ChildCameraScreen(
    requestId: String? = null,
    openGallery: Boolean = false,
    viewModel: ChildCameraViewModel = koinViewModel(),
) {
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(if (openGallery) TAB_GALLERY else TAB_CAMERA) }
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasPermission = it
    }
    val imageCapture = remember { ImageCapture.Builder().build() }
    val requestActive = state.requestId != null

    LaunchedEffect(requestId) {
        viewModel.onRequest(requestId)
    }

    // 부모 요청으로 열렸으면 갤러리를 보고 있어도 카메라로 되돌린다.
    LaunchedEffect(requestActive) {
        if (requestActive) {
            tab = TAB_CAMERA
            if (!hasPermission) launcher.launch(KidhackPermissions.camera)
        }
    }

    LaunchedEffect(tab) {
        if (tab == TAB_GALLERY) viewModel.loadGallery()
    }

    LaunchedEffect(state.phase) {
        if (state.phase == UploadPhase.Sent || state.phase == UploadPhase.Failed) {
            delay(2500)
            viewModel.clearPhase()
        }
    }

    val takePhoto = {
        if (!hasPermission) {
            launcher.launch(KidhackPermissions.camera)
        } else if (state.phase != UploadPhase.Uploading) {
            val file = File(context.cacheDir, "photo-" + System.currentTimeMillis() + ".jpg")
            imageCapture.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        viewModel.upload(file)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        runCatching { file.delete() }
                        viewModel.onCaptureFailed()
                    }
                },
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface),
    ) {
        BrandGradientHeader {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.child_camera_title),
                    modifier = Modifier.weight(1f),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 20.sp,
                )
                CameraTabChip(
                    selected = tab == TAB_CAMERA,
                    label = stringResource(R.string.child_camera_tab_camera),
                    onClick = { tab = TAB_CAMERA },
                )
                Spacer(Modifier.size(8.dp))
                CameraTabChip(
                    selected = tab == TAB_GALLERY,
                    label = stringResource(R.string.child_camera_tab_gallery),
                    onClick = { if (!requestActive) tab = TAB_GALLERY },
                )
            }
        }

        if (requestActive) {
            RequestBanner(requestedBy = state.requestedBy)
        }

        if (tab == TAB_CAMERA) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF111827)),
                contentAlignment = Alignment.Center,
            ) {
                if (hasPermission) {
                    CameraPreview(
                        lensFacing = lensFacing,
                        imageCapture = imageCapture,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📷", fontSize = 56.sp)
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.child_camera_need_permission),
                            color = Color(0xFF9CA3AF),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(12.dp))
                        KidButton(
                            text = stringResource(R.string.child_camera_grant),
                            onClick = { launcher.launch(KidhackPermissions.camera) },
                        )
                    }
                }
                GridOverlay()
                UploadPill(
                    phase = state.phase,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF111827))
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleControl("🖼️") { if (!requestActive) tab = TAB_GALLERY }
                Spacer(Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable(onClick = takePhoto),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White)
                            .padding(3.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape)
                                .background(colors.brand),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (state.phase == UploadPhase.Uploading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Text("📸", fontSize = 22.sp)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                CircleControl("🔄") {
                    lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    }
                }
            }
        } else {
            GalleryGrid(
                photos = state.gallery,
                loading = state.galleryLoading,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * 실제로 찍어서 기기에 남아 있는 사진들.
 *
 * 예전에는 이모지 타일 6개가 하드코딩돼 있어서, 부모 요청으로 찍어 보낸 사진을
 * 자녀가 다시 볼 방법이 없었다.
 */
@Composable
private fun GalleryGrid(
    photos: List<ChildPhoto>,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = stringResource(R.string.child_camera_recent),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.sp,
            color = colors.text,
        )
        Spacer(Modifier.height(12.dp))

        if (photos.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(40.dp))
                Text(if (loading) "⏳" else "📷", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(
                        if (loading) R.string.child_camera_gallery_loading
                        else R.string.child_camera_gallery_empty,
                    ),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                    color = colors.text,
                )
                if (!loading) {
                    Text(
                        text = stringResource(R.string.child_camera_gallery_empty_hint),
                        color = colors.muted,
                        fontSize = 12.sp,
                    )
                }
            }
            return@Column
        }

        photos.chunked(GALLERY_COLUMNS).forEach { row ->
            Row {
                row.forEach { photo ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.border),
                        ) {
                            AsyncImage(
                                model = photo.imageUrl?.let(::File),
                                contentDescription = photo.capturedAt,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                            if (photo.requestId != null) {
                                Text(
                                    text = stringResource(R.string.child_camera_gallery_requested),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(colors.brand)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    color = Color.White,
                                    fontFamily = KidhackFontFamily,
                                    fontWeight = FontBold,
                                    fontSize = 10.sp,
                                )
                            }
                        }
                        Text(
                            text = photo.capturedAt,
                            color = colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 12.sp,
                        )
                    }
                }
                repeat(GALLERY_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

private const val GALLERY_COLUMNS = 3

@Composable
private fun RequestBanner(requestedBy: String?) {
    val colors = LocalKidhackColors.current
    val who = KoreanParticles.subject(
        requestedBy?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.child_request_default_sender),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(colors.brandLight)
            .border(1.dp, colors.brand.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("📷", fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.child_camera_request_title),
                color = colors.brandDark,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
            )
            Text(
                text = stringResource(R.string.child_camera_request_body, who),
                color = colors.muted,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun UploadPill(phase: UploadPhase, modifier: Modifier = Modifier) {
    val colors = LocalKidhackColors.current
    val (label, tint) = when (phase) {
        UploadPhase.Uploading -> stringResource(R.string.child_camera_sending) to colors.info
        UploadPhase.Sent -> stringResource(R.string.child_camera_sent) to colors.safe
        UploadPhase.Failed -> stringResource(R.string.child_camera_send_failed) to colors.danger
        UploadPhase.Idle -> return
    }
    Text(
        text = label,
        modifier = modifier
            .clip(RoundedCornerShape(99.dp))
            .background(tint)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = Color.White,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

@Composable
private fun CameraTabChip(selected: Boolean, label: String, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.2f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = if (selected) colors.brand else Color.White,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

@Composable
private fun CircleControl(emoji: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF374151))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(emoji, fontSize = 20.sp)
    }
}

@Composable
private fun GridOverlay() {
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .offset(y = (-80).dp)
                .background(Color.White.copy(alpha = 0.1f)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .align(Alignment.Center)
                .offset(y = 80.dp)
                .background(Color.White.copy(alpha = 0.1f)),
        )
    }
}

@Composable
private fun CameraPreview(
    lensFacing: Int,
    imageCapture: ImageCapture,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context) }

    DisposableEffect(lensFacing, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        val executor = ContextCompat.getMainExecutor(context)
        val listener = Runnable {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
            runCatching {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(lifecycleOwner, selector, preview, imageCapture)
            }
        }
        cameraProviderFuture.addListener(listener, executor)
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
        }
    }

    AndroidView(factory = { previewView }, modifier = modifier)
}
