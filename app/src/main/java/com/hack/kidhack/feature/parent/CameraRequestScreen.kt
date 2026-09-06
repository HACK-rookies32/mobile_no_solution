package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidCard
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.ChildPhoto
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.PhotoRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class CameraRequestUiState(
    val items: List<ChildPhoto> = emptyList(),
    val selectedPhoto: ChildPhoto? = null,
    val requesting: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * 부모 「카메라 촬영 요청」. [ScreenshotViewModel] 과 대칭이다.
 *
 * 요청을 보내면 서버가 자녀 기기로 CAMERA_REQUEST 푸시를 밀고, 자녀가 찍어 올린 사진이
 * 갤러리 목록에 나타난다. 캡처와 달리 자녀가 직접 셔터를 눌러야 하므로 응답이 더 걸릴 수
 * 있다 — 폴링 시간도 그만큼 길게 잡는다.
 */
class CameraRequestViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val photoRepository: PhotoRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(CameraRequestUiState())
    val state: StateFlow<CameraRequestUiState> = _state.asStateFlow()

    init { load() }

    fun selectPhoto(photo: ChildPhoto?) = _state.update { it.copy(selectedPhoto = photo) }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children()) ?: return@runCatching emptyList()
                photoRepository.list(id)
            }.onSuccess { items ->
                _state.update { it.copy(loading = false, items = items) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.userMessage("촬영 내역을 불러오지 못했어요")) }
            }
        }
    }

    fun request() {
        viewModelScope.launch {
            val lastId = _state.value.items.firstOrNull()?.id
            _state.update { it.copy(requesting = true, error = null) }
            runCatching {
                val id = selectedChildRepository.current()
                    ?: selectedChildRepository.resolveId(childRepository.children())
                    ?: error("자녀를 선택해 주세요")

                photoRepository.requestShot(id)

                // 자녀가 알림을 보고 직접 찍어야 하므로 캡처(2분)보다 길게 5분간 기다린다.
                var found = false
                for (i in 1..60) {
                    delay(5_000)
                    val items = runCatching { photoRepository.list(id) }.getOrNull() ?: continue
                    if (items.firstOrNull()?.id != lastId) {
                        _state.update { it.copy(requesting = false, items = items) }
                        found = true
                        break
                    }
                }
                if (!found) error("자녀가 아직 촬영하지 않았어요. 잠시 후 목록을 확인해 주세요.")
            }.onFailure { t ->
                _state.update { it.copy(requesting = false, error = t.userMessage("촬영 요청에 실패했어요")) }
            }
        }
    }
}

@Composable
fun ParentCameraRequestScreen(
    onBack: () -> Unit = {},
    viewModel: CameraRequestViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    ParentScreenScaffold(
        header = { ParentHeaderBar(stringResource(R.string.parent_camera_title), onBack) },
    ) {
        KidCard {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📷", fontSize = 48.sp)
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.parent_camera_live), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 16.sp, color = colors.text)
                Text(stringResource(R.string.parent_camera_desc), color = colors.muted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))
                if (state.requesting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), color = colors.brand, strokeWidth = 2.dp)
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.parent_camera_requesting), color = colors.brand, fontWeight = FontBold)
                    }
                } else {
                    KidButton(stringResource(R.string.parent_camera_request), viewModel::request)
                }
            }
        }
        Text(stringResource(R.string.parent_camera_recent), fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 14.sp, color = colors.text)

        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::load)

        if (!state.loading && state.items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.parent_camera_empty),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontSize = 14.sp,
                )
            }
        }

        state.items.forEach { photo ->
            KidCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(width = 64.dp, height = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(photo.imageUrl.takeIf { !it.isNullOrBlank() } ?: R.drawable.ic_launcher_foreground)
                                .crossfade(true)
                                .build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(
                                if (photo.requestId.isNullOrBlank()) R.string.parent_camera_item_self else R.string.parent_camera_item_requested,
                            ),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = colors.text,
                        )
                        Text(photo.capturedAt, color = colors.muted, fontSize = 12.sp)
                    }
                    KidButton(
                        text = stringResource(R.string.parent_camera_view),
                        onClick = { viewModel.selectPhoto(photo) },
                        variant = KidButtonVariant.Ghost,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }

    // 상세 보기 다이얼로그
    state.selectedPhoto?.let { photo ->
        Dialog(
            onDismissRequest = { viewModel.selectPhoto(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { viewModel.selectPhoto(null) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(photo.imageUrl.takeIf { !it.isNullOrBlank() } ?: R.drawable.ic_launcher_foreground)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(photo.capturedAt, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(24.dp))
                    KidButton(stringResource(R.string.parent_camera_close), { viewModel.selectPhoto(null) }, variant = KidButtonVariant.Secondary)
                }
            }
        }
    }
}
