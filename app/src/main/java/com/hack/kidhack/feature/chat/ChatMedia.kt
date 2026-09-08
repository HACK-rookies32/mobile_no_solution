package com.hack.kidhack.feature.chat

import android.media.MediaPlayer
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.core.permission.KidhackPermissions
import kotlinx.coroutines.delay
import java.io.File

private const val TAG = "ChatMedia"

/** 파형에 남겨 두는 샘플 개수. 새 샘플이 오른쪽에 붙고 오래된 것이 왼쪽으로 밀려난다. */
private const val WAVE_SAMPLES = 24

/** 진폭 정규화 기준. MediaRecorder.getMaxAmplitude() 는 0..32767 을 준다. */
private const val AMPLITUDE_CEIL = 14000f

/**
 * 메신저형 입력줄. 텍스트 필드와 첨부/전송 버튼을 한 컴포넌트가 모두 들고 있어
 * 부모·자녀 두 채팅 화면이 똑같은 입력줄을 쓴다.
 *
 * 상태는 세 가지다.
 *  - 기본: `+`(첨부 토글) · 입력 필드 · 🎤(빠른 녹음) · 전송.
 *  - 첨부 트레이 열림: `+` 가 ✕ 로 바뀌고, 위로 카메라/갤러리/음성 타일이 펼쳐진다.
 *  - 녹음 중: 줄 전체가 🗑(취소) · 실시간 파형 · 타이머 · 전송 으로 바뀐다.
 *
 * 카메라/마이크 권한은 버튼을 누르는 순간에 없으면 다시 요청한다(온보딩 거절 안전망).
 * [onImage]/[onAudio] 는 업로드 전 로컬 URI(문자열)를 준다. 전송은 호출부(ViewModel)가 맡는다.
 */
@Composable
fun ChatComposer(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    onImage: (String) -> Unit,
    onAudio: (String) -> Unit,
    hint: String,
    sendContentDescription: String,
    modifier: Modifier = Modifier,
    imeSendAction: Boolean = false,
) {
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    var trayOpen by remember { mutableStateOf(false) }

    // ---- 카메라 ----
    var pendingPhotoUri by remember { mutableStateOf<Uri?>(null) }
    val takePicture = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val uri = pendingPhotoUri
        pendingPhotoUri = null
        if (success && uri != null) onImage(uri.toString())
    }
    fun launchCamera() {
        val file = File(context.cacheDir, "chat-photo-${System.currentTimeMillis()}.jpg")
        val uri = runCatching {
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull() ?: return
        pendingPhotoUri = uri
        runCatching { takePicture.launch(uri) }
            .onFailure { Log.w(TAG, "camera launch failed", it) }
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCamera() }
    fun onCameraTile() {
        trayOpen = false
        if (KidhackPermissions.hasCamera(context)) launchCamera()
        else cameraPermission.launch(KidhackPermissions.camera)
    }

    // ---- 갤러리(포토 피커, 권한 불필요) ----
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) onImage(uri.toString()) }
    fun onGalleryTile() {
        trayOpen = false
        pickMedia.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    // ---- 녹음 ----
    var recorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var audioFile by remember { mutableStateOf<File?>(null) }
    var recording by remember { mutableStateOf(false) }
    var elapsedSec by remember { mutableStateOf(0) }
    val amplitudes = remember { mutableStateListOf<Float>() }

    fun stopRecording(emit: Boolean) {
        val rec = recorder
        recorder = null
        recording = false
        val file = audioFile
        audioFile = null
        runCatching { rec?.stop() }.onFailure { Log.w(TAG, "recorder stop failed", it) }
        runCatching { rec?.release() }
        if (emit && file != null && file.exists() && file.length() > 0) {
            onAudio(Uri.fromFile(file).toString())
        } else {
            runCatching { file?.delete() }
        }
    }

    fun startRecording() {
        trayOpen = false
        val file = File(context.cacheDir, "chat-audio-${System.currentTimeMillis()}.m4a")
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        val ok = runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.MIC)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
        }.isSuccess
        if (ok) {
            recorder = rec
            audioFile = file
            elapsedSec = 0
            amplitudes.clear()
            recording = true
        } else {
            runCatching { rec.release() }
            runCatching { file.delete() }
        }
    }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) startRecording() }
    fun requestRecord() {
        if (KidhackPermissions.hasMicrophone(context)) startRecording()
        else micPermission.launch(KidhackPermissions.microphone)
    }

    // 녹음 중에는 ~90ms 마다 진폭을 읽어 파형에 붙이고 경과 초를 갱신한다.
    LaunchedEffect(recording) {
        if (!recording) return@LaunchedEffect
        val startAt = System.currentTimeMillis()
        while (recording) {
            val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
            val norm = (amp / AMPLITUDE_CEIL).coerceIn(0f, 1f)
            amplitudes.add(norm)
            while (amplitudes.size > WAVE_SAMPLES) amplitudes.removeAt(0)
            elapsedSec = ((System.currentTimeMillis() - startAt) / 1000L).toInt()
            delay(90)
        }
    }

    // 화면을 벗어나면 녹음을 버린다(파일도 지운다).
    DisposableEffect(Unit) {
        onDispose { if (recorder != null) stopRecording(emit = false) }
    }

    Column(modifier = modifier.fillMaxWidth().background(colors.card)) {
        HorizontalDivider(color = colors.line)

        // 첨부 트레이 — 녹음 중에는 감춘다.
        AnimatedVisibility(
            visible = trayOpen && !recording,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            AttachmentTray(
                onCamera = ::onCameraTile,
                onGallery = ::onGalleryTile,
                onVoice = { trayOpen = false; requestRecord() },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 14.dp, top = 8.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (recording) {
                RecordingBar(
                    amplitudes = amplitudes,
                    elapsedSec = elapsedSec,
                    onCancel = { stopRecording(emit = false) },
                    onSend = { stopRecording(emit = true) },
                )
            } else {
                DefaultComposer(
                    draft = draft,
                    onDraft = onDraft,
                    onSend = onSend,
                    canSend = canSend,
                    hint = hint,
                    sendContentDescription = sendContentDescription,
                    trayOpen = trayOpen,
                    onTogglePlus = { trayOpen = !trayOpen },
                    onMic = { trayOpen = false; requestRecord() },
                    imeSendAction = imeSendAction,
                )
            }
        }
    }
}

/** 기본 입력줄: + · 필드 · 🎤 · 전송. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.DefaultComposer(
    draft: String,
    onDraft: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    hint: String,
    sendContentDescription: String,
    trayOpen: Boolean,
    onTogglePlus: () -> Unit,
    onMic: () -> Unit,
    imeSendAction: Boolean,
) {
    val colors = LocalKidhackColors.current
    // 열리면 + 가 ✕ 로 돌아간다(45° 회전).
    val plusRotation by animateFloatAsState(if (trayOpen) 45f else 0f, label = "plusRotation")

    RoundIconButton(
        iconRes = R.drawable.ic_plus,
        contentDescription = if (trayOpen) "첨부 닫기" else "첨부 열기",
        onClick = onTogglePlus,
        tint = if (trayOpen) colors.brand else colors.muted,
        iconModifier = Modifier.rotate(plusRotation),
    )
    Spacer(Modifier.width(4.dp))
    BasicTextField(
        value = draft,
        onValueChange = onDraft,
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        textStyle = TextStyle(
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
            fontSize = 14.sp,
            color = colors.text,
        ),
        cursorBrush = SolidColor(colors.brand),
        maxLines = 4,
        keyboardOptions = if (imeSendAction) KeyboardOptions(imeAction = ImeAction.Send) else KeyboardOptions.Default,
        keyboardActions = if (imeSendAction) KeyboardActions(onSend = { onSend() }) else KeyboardActions.Default,
        decorationBox = { inner ->
            if (draft.isEmpty()) {
                Text(hint, color = colors.subtle, fontSize = 14.sp)
            }
            inner()
        },
    )
    Spacer(Modifier.width(2.dp))
    RoundIconButton(
        iconRes = R.drawable.ic_mic,
        contentDescription = "음성 녹음",
        onClick = onMic,
        tint = colors.muted,
    )
    Spacer(Modifier.width(4.dp))
    SendButton(
        enabled = canSend,
        contentDescription = sendContentDescription,
        onClick = onSend,
    )
}

/** 녹음 줄: 🗑 · 파형 · 타이머 · 전송. */
@Composable
private fun androidx.compose.foundation.layout.RowScope.RecordingBar(
    amplitudes: List<Float>,
    elapsedSec: Int,
    onCancel: () -> Unit,
    onSend: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    RoundIconButton(
        iconRes = R.drawable.ic_trash,
        contentDescription = "녹음 취소",
        onClick = onCancel,
        tint = colors.danger,
    )
    Spacer(Modifier.width(4.dp))
    Row(
        modifier = Modifier
            .weight(1f)
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Waveform(
            amplitudes = amplitudes,
            color = colors.danger,
            modifier = Modifier.weight(1f).height(24.dp),
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = formatElapsed(elapsedSec),
            color = colors.danger,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
    }
    Spacer(Modifier.width(4.dp))
    SendButton(
        enabled = true,
        contentDescription = "음성 보내기",
        onClick = onSend,
    )
}

/** 첨부 타일 세 개(카메라/갤러리/음성). */
@Composable
private fun AttachmentTray(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    onVoice: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AttachmentTile(R.drawable.ic_camera, "카메라", onCamera, Modifier.weight(1f))
        AttachmentTile(R.drawable.ic_image, "갤러리", onGallery, Modifier.weight(1f))
        AttachmentTile(R.drawable.ic_mic, "음성", onVoice, Modifier.weight(1f))
    }
}

@Composable
private fun AttachmentTile(
    iconRes: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(colors.brandLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = colors.brand,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            color = colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
            fontSize = 11.5.sp,
        )
    }
}

/** 44dp 유효 터치 영역의 원형 아이콘 버튼. */
@Composable
private fun RoundIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            modifier = iconModifier.size(23.dp),
            tint = tint,
        )
    }
}

@Composable
private fun SendButton(
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(if (enabled) colors.brand else colors.line)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_send),
            contentDescription = contentDescription,
            modifier = Modifier.size(17.dp),
            tint = if (enabled) Color.White else colors.muted,
        )
    }
}

/**
 * 실시간 파형. 진폭 리스트를 둥근 세로 막대로 그린다. 새 샘플이 오른쪽에 붙고
 * 오래된 막대가 왼쪽으로 밀려난다. 값이 없으면 최소 높이의 점선처럼 낮게 깐다.
 */
@Composable
private fun Waveform(
    amplitudes: List<Float>,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val barCount = WAVE_SAMPLES
        val gap = 3.dp.toPx()
        val barWidth = ((size.width - gap * (barCount - 1)) / barCount).coerceAtLeast(1f)
        val minH = 3.dp.toPx()
        val maxH = size.height
        val radius = barWidth / 2f
        // 오른쪽 정렬: 최신 샘플이 오른쪽 끝.
        for (i in 0 until barCount) {
            val sampleIndex = amplitudes.size - barCount + i
            val amp = amplitudes.getOrNull(sampleIndex) ?: 0f
            val h = (minH + amp * (maxH - minH)).coerceIn(minH, maxH)
            val x = i * (barWidth + gap)
            val top = (size.height - h) / 2f
            drawRoundRect(
                color = color,
                topLeft = Offset(x, top),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(radius, radius),
                alpha = if (amp <= 0f) 0.25f else 1f,
            )
        }
    }
}

private fun formatElapsed(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}

/** 이미지 메시지 본문. 서버 상대 경로를 절대 URL 로 바꿔 그린다. */
@Composable
fun ChatImageBubble(mediaUrl: String?, modifier: Modifier = Modifier) {
    if (mediaUrl.isNullOrBlank()) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(RemoteUrl.absolute(mediaUrl))
            .crossfade(true)
            .build(),
        contentDescription = "사진 메시지",
        contentScale = ContentScale.Crop,
        modifier = modifier
            .widthIn(max = 200.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp)),
    )
}

/**
 * 음성 메시지 본문. 재생 버튼 하나로 [MediaPlayer] 를 켜고 끈다.
 * [bubbleColor] 배경 위에 [contentColor] 로 아이콘·글자를 그린다.
 */
@Composable
fun ChatAudioBubble(
    mediaUrl: String?,
    bubbleColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember { mutableStateOf(false) }

    fun release() {
        runCatching { player?.release() }
        player = null
        playing = false
    }

    DisposableEffect(mediaUrl) { onDispose { release() } }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(bubbleColor)
            .clickable(enabled = !mediaUrl.isNullOrBlank()) {
                if (playing) {
                    release()
                } else {
                    val url = mediaUrl?.let { RemoteUrl.absolute(it) } ?: return@clickable
                    val mp = MediaPlayer()
                    val ok = runCatching {
                        mp.setDataSource(url)
                        mp.setOnCompletionListener { release() }
                        mp.setOnErrorListener { _, _, _ -> release(); true }
                        mp.setOnPreparedListener { it.start() }
                        mp.prepareAsync()
                    }.isSuccess
                    if (ok) {
                        player = mp
                        playing = true
                    } else {
                        runCatching { mp.release() }
                    }
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (playing) "⏸" else "▶",
            color = contentColor,
            fontSize = 15.sp,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "음성 메시지",
            color = contentColor,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
    }
}
