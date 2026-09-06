package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.paging.LoadState
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Screenshot
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.ScreenshotRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ScreenshotUiState(
    val selectedShot: Screenshot? = null,
    val requesting: Boolean = false,
    val error: String? = null,
)

class ScreenshotViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val screenshotRepository: ScreenshotRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenshotUiState())
    val state: StateFlow<ScreenshotUiState> = _state.asStateFlow()

    // 캡처 요청으로 새 항목이 감지되면 UI 가 paging 목록을 refresh 하도록 알리는 1회성 신호.
    private val _refreshSignal = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val refreshSignal: SharedFlow<Unit> = _refreshSignal.asSharedFlow()

    // 선택된 자녀가 바뀔 때마다 그 자녀의 캡처 목록으로 Pager 를 다시 만든다.
    @OptIn(ExperimentalCoroutinesApi::class)
    val screenshots: Flow<PagingData<Screenshot>> =
        selectedChildRepository.selectedChildId
            .flatMapLatest { selected ->
                val id = selected
                    ?: runCatching { selectedChildRepository.resolveId(childRepository.children()) }.getOrNull()
                if (id == null) flowOf(PagingData.empty<Screenshot>()) else screenshotRepository.paged(id)
            }
            .cachedIn(viewModelScope)

    fun selectShot(shot: Screenshot?) = _state.update { it.copy(selectedShot = shot) }

    fun request() {
        viewModelScope.launch {
            _state.update { it.copy(requesting = true, error = null) }
            runCatching {
                val id = selectedChildRepository.current()
                    ?: selectedChildRepository.resolveId(childRepository.children())
                    ?: error("자녀를 선택해 주세요")

                // 폴링 비교 기준(현재 첫 항목). paged() 는 첫 페이지만 다시 엿보는 API 가 마땅치 않아
                // 기존 non-paged list() 로 새 캡처 도착만 감지하고, 목록 자체는 items.refresh() 로 갱신한다.
                val lastId = runCatching { screenshotRepository.list(id).firstOrNull()?.id }.getOrNull()

                // 서버가 자녀 기기로 FCM(SCREENSHOT_REQUESTED)을 보내고, 자녀가 화면 공유에 동의하면 업로드된다.
                // 도착 통지는 없어 여기서 목록을 폴링한다. 자녀가 알림을 보고 동의하는 시간이 있으므로 최대 5분.
                screenshotRepository.requestCapture(id)

                var found = false
                for (i in 1..POLL_MAX_ROUNDS) {
                    delay(POLL_INTERVAL_MS)
                    val firstId = runCatching { screenshotRepository.list(id).firstOrNull()?.id }.getOrNull()
                    if (firstId != lastId) {
                        _state.update { it.copy(requesting = false) }
                        _refreshSignal.tryEmit(Unit)
                        found = true
                        break
                    }
                }
                if (!found) {
                    // 시간 초과여도 목록은 한 번 다시 읽는다(늦게 올라온 캡처가 옛 목록에 가려지지 않도록).
                    _refreshSignal.tryEmit(Unit)
                    error("캡처 응답 시간이 초과되었습니다. 자녀가 화면 공유에 동의하면 목록에 나타나요.")
                }
            }.onFailure { t ->
                _state.update { it.copy(requesting = false, error = t.message) }
            }
        }
    }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        /** 5초 × 60 = 5분. */
        const val POLL_MAX_ROUNDS = 60
    }
}

@Composable
fun ParentScreenshotScreen(
    onBack: () -> Unit = {},
    viewModel: ScreenshotViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val items = viewModel.screenshots.collectAsLazyPagingItems()

    // 캡처 요청으로 새 항목이 감지되면 paging 목록만 새로고침한다.
    LaunchedEffect(Unit) {
        viewModel.refreshSignal.collect { items.refresh() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u2039", color = colors.text, fontSize = 26.sp)
            }
            Text(
                stringResource(R.string.parent_screenshot_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 캡처 요청 히어로
            item { RequestHero(requesting = state.requesting, onRequest = viewModel::request) }

            // 요청 실패 메시지(폴링/캡처 요청 오류)
            if (state.error != null) {
                item { ParentLoadingOrError(false, state.error) }
            }

            item {
                Text(
                    stringResource(R.string.parent_screenshot_recent),
                    modifier = Modifier.padding(start = 6.dp, top = 8.dp),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.sp,
                    color = colors.subtle,
                )
            }

            // 목록 첫 로딩 / 에러 / 빈 상태
            val refresh = items.loadState.refresh
            when {
                refresh is LoadState.Loading && items.itemCount == 0 ->
                    item { ParentLoadingOrError(true, null) }
                refresh is LoadState.Error ->
                    item { ParentLoadingOrError(false, refresh.error.message ?: "", onRetry = { items.retry() }) }
                refresh is LoadState.NotLoading && items.itemCount == 0 ->
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.parent_screenshot_empty),
                                color = colors.subtle,
                                fontFamily = KidhackFontFamily,
                                fontSize = 14.sp,
                            )
                        }
                    }
            }

            // 2열 썸네일 갤러리 — paging 항목을 두 개씩 한 행에 놓는다.
            val rowCount = (items.itemCount + 1) / 2
            items(count = rowCount) { rowIndex ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    for (col in 0..1) {
                        val index = rowIndex * 2 + col
                        val shot = if (index < items.itemCount) items[index] else null
                        if (shot != null) {
                            ScreenshotTile(shot, Modifier.weight(1f), onView = { viewModel.selectShot(shot) })
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            // 다음 페이지 로딩/에러(맨 아래)
            when (items.loadState.append) {
                is LoadState.Loading ->
                    item {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(20.dp), color = colors.brand, strokeWidth = 2.dp)
                        }
                    }
                is LoadState.Error ->
                    item {
                        Column(Modifier.fillMaxWidth().padding(8.dp)) {
                            Text("\ub354 \ubd88\ub7ec\uc624\uc9c0 \ubabb\ud588\uc5b4\uc694", color = colors.danger, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 13.sp)
                            KidButton(stringResource(R.string.parent_retry), { items.retry() }, Modifier.fillMaxWidth())
                        }
                    }
                else -> Unit
            }
        }
    }

    // 상세 보기 다이얼로그
    state.selectedShot?.let { shot ->
        Dialog(
            onDismissRequest = { viewModel.selectShot(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { viewModel.selectShot(null) },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(RemoteUrl.absolute(shot.imageUrl.orEmpty()).takeIf { it.isNotBlank() } ?: R.drawable.ic_launcher_foreground)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth(0.9f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(stringResource(shot.triggerLabelRes()), color = Color.White, fontWeight = FontBold, fontSize = 16.sp)
                    Text(shot.capturedAt, color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
                    Spacer(Modifier.height(24.dp))
                    KidButton("\ub2eb\uae30", { viewModel.selectShot(null) }, variant = KidButtonVariant.Secondary)
                }
            }
        }
    }
}

/** 지금 화면을 캡처 요청하는 히어로. 요청 중이면 스피너로 바뀐다. */
@Composable
private fun RequestHero(requesting: Boolean, onRequest: () -> Unit) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.card)
            .border(width = 1.dp, color = colors.border, shape = RoundedCornerShape(18.dp))
            .padding(vertical = 22.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(colors.brandLight),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_camera),
                contentDescription = null,
                modifier = Modifier.size(27.dp),
                tint = colors.brand,
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            stringResource(R.string.parent_screenshot_live),
            fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 15.5.sp, color = colors.text,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(R.string.parent_screenshot_desc),
            color = colors.muted, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        if (requesting) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), color = colors.brand, strokeWidth = 2.dp)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.parent_screenshot_requesting), color = colors.brand, fontWeight = FontBold)
            }
        } else {
            KidButton(stringResource(R.string.parent_screenshot_request), onRequest, Modifier.fillMaxWidth())
        }
    }
}

/** 갤러리 한 칸. 썸네일 위에 자동/수동 배지와 캡처 시각을 얹는다. */
@Composable
private fun ScreenshotTile(shot: Screenshot, modifier: Modifier = Modifier, onView: () -> Unit) {
    val colors = LocalKidhackColors.current
    val manual = !shot.trigger.equals("SCHEDULED", ignoreCase = true)
    Box(
        modifier = modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .clickable(onClick = onView),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(RemoteUrl.absolute(shot.imageUrl.orEmpty()).takeIf { it.isNotBlank() } ?: R.drawable.ic_launcher_foreground)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // 트리거 배지(좌상단)
        Text(
            text = stringResource(shot.triggerLabelRes()),
            modifier = Modifier
                .padding(7.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (manual) colors.brandDark.copy(alpha = 0.92f) else Color(0xB81F2937))
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = Color.White,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 10.sp,
        )
        // 캡처 시각(하단 그라데이션)
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0x8C000000))))
                .padding(start = 9.dp, end = 9.dp, top = 18.dp, bottom = 7.dp),
        ) {
            Text(shot.capturedAt, color = Color.White, fontSize = 10.5.sp, fontWeight = FontBold)
        }
    }
}

/** 서버는 캡처 설명을 주지 않는다(ScreenshotResponse 에 없음). 트리거를 제목처럼 쓴다. */
private fun Screenshot.triggerLabelRes(): Int =
    if (trigger.equals("SCHEDULED", ignoreCase = true)) {
        R.string.parent_screenshot_trigger_scheduled
    } else {
        R.string.parent_screenshot_trigger_manual
    }
