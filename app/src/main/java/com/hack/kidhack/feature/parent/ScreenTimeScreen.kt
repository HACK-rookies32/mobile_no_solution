package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.component.WheelGuides
import com.hack.kidhack.core.designsystem.component.WheelPicker
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidLineBreak
import com.hack.kidhack.core.designsystem.theme.keepWordsWhole
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 기본으로 보여 줄 시간. 서버에 값이 없을 때 휠이 어딘가에는 서 있어야 한다. */
private const val DEFAULT_HOURS = 3

data class ScreenTimeUiState(
    /** 누구의 약속인지. 자녀가 여럿이면 화면 제목만으로는 구분되지 않는다. */
    val childName: String = "",
    /** 약속을 쓰는지. 끄면 서버에 null(제한 없음)을 보낸다. */
    val enabled: Boolean = false,
    val hours: Int = DEFAULT_HOURS,
    val minutes: Int = 0,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null,
) {
    val totalMinutes: Int get() = hours * 60 + minutes

    /** 0분은 "하루 종일 잠금"이라 실수로 저장되기 쉽다. 켜 두었다면 최소 1분은 받는다. */
    val canSave: Boolean get() = !saving && (!enabled || totalMinutes > 0)
}

/**
 * 자녀의 하루 사용시간 약속 (`GET`·`PUT /appcontrol/screentime/{childId}`).
 *
 * <p>이 값은 자녀 앱이 받아 가서, 다 쓰면 다른 앱을 열지 못하게 잠근다. 앱 통제 목록의
 * 앱별 한도와는 별개다 — 그쪽은 앱 하나하나, 이쪽은 하루 전체다.
 *
 * <p>서버는 `null` 을 "제한 없음", `0` 을 "한도 0분"으로 구분한다. 화면의 토글이 그
 * 구분을 그대로 나른다 — 끄면 null, 켜면 시간·분 합.
 */
class ScreenTimeViewModel(
    private val appControlRepository: AppControlRepository,
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ScreenTimeUiState())
    val state: StateFlow<ScreenTimeUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children) ?: return@runCatching null to ""
                val name = children.firstOrNull { it.id == id }?.name.orEmpty()
                appControlRepository.screenTimeLimit(id) to name
            }.onSuccess { (limit, name) ->
                _state.update {
                    it.copy(
                        loading = false,
                        childName = name,
                        enabled = limit != null,
                        hours = limit?.let { m -> m / 60 } ?: DEFAULT_HOURS,
                        minutes = limit?.let { m -> m % 60 } ?: 0,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.userMessage("한도를 불러오지 못했어요")) }
            }
        }
    }

    fun onEnabled(value: Boolean) = _state.update { it.copy(enabled = value, saved = false, error = null) }
    fun onHours(value: Int) = _state.update { it.copy(hours = value, saved = false) }
    fun onMinutes(value: Int) = _state.update { it.copy(minutes = value, saved = false) }

    fun save() {
        val current = _state.value
        if (!current.canSave) return
        viewModelScope.launch {
            _state.update { it.copy(saving = true, error = null, saved = false) }
            runCatching {
                val id = selectedChildRepository.current() ?: return@runCatching null
                appControlRepository.setScreenTimeLimit(
                    id,
                    if (current.enabled) current.totalMinutes else null,
                )
            }.onSuccess {
                _state.update { it.copy(saving = false, saved = true) }
            }.onFailure { t ->
                _state.update { it.copy(saving = false, error = t.userMessage("한도를 저장하지 못했어요")) }
            }
        }
    }
}

/**
 * 하루 사용시간 약속 설정 화면.
 *
 * <p>다른 부모 화면과 달리 주황 헤더(`ParentScreenScaffold`)도, 카드도 쓰지 않는다. 값
 * 하나를 고르는 설정 화면이라 색과 테두리를 걷어내고 화면 전체를 흰 바탕으로 두었다.
 * 구역은 얇은 선으로만 나눈다 — 카드를 쌓으면 고를 것이 여럿인 것처럼 보인다.
 */
@Composable
fun ParentScreenTimeScreen(
    onBack: () -> Unit = {},
    viewModel: ScreenTimeViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        TitleBar(title = stringResource(R.string.parent_screentime_title), onBack = onBack)

        if (state.error != null) {
            Text(
                text = state.error.orEmpty(),
                color = colors.danger,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }

        Section {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.parent_screentime_toggle),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 16.sp,
                        color = colors.text,
                    )
                    if (state.childName.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.parent_screentime_for_child, state.childName),
                            color = colors.muted,
                            fontSize = 12.sp,
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                KidSwitch(checked = state.enabled, onCheckedChange = viewModel::onEnabled)
            }
        }

        if (state.enabled) {
            Hairline()
            Section {
                Text(
                    stringResource(R.string.parent_screentime_how_long),
                    color = colors.muted,
                    fontSize = 13.sp,
                )
            }
            // 강조선은 두 휠을 가로질러 한 줄로 긋는다. 휠마다 그리면 가운데가 끊겨 보인다.
            Box(contentAlignment = Alignment.Center) {
                WheelGuides()
                Row(Modifier.fillMaxWidth()) {
                    WheelPicker(
                        values = HOUR_VALUES,
                        value = state.hours,
                        onValueChange = viewModel::onHours,
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.parent_screentime_unit_hour),
                        showGuides = false,
                    )
                    WheelPicker(
                        values = MINUTE_VALUES,
                        value = state.minutes,
                        onValueChange = viewModel::onMinutes,
                        modifier = Modifier.weight(1f),
                        label = stringResource(R.string.parent_screentime_unit_minute),
                        showGuides = false,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
            Hairline()

            Section {
                Text(
                    stringResource(R.string.parent_screentime_when_used_up),
                    color = colors.muted,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.Top) {
                    // 잠금이라는 결과를 작은 표식 하나로만 나타낸다. 카드나 색 블록을
                    // 더하면 안내가 설정처럼 보여 고를 수 있는 것으로 오해된다.
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(16.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(colors.brand),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            stringResource(R.string.parent_screentime_lock_title),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = colors.text,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.parent_screentime_lock_desc).keepWordsWhole(),
                            color = colors.muted,
                            fontSize = 13.sp,
                            lineHeight = 19.sp,
                            style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
                        )
                    }
                }
            }
        }

        Hairline()
        Section {
            if (state.saved) {
                Text(
                    stringResource(R.string.parent_screentime_saved),
                    color = colors.safe,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
            }
            KidButton(
                text = stringResource(
                    if (state.saving) R.string.parent_screentime_saving else R.string.parent_screentime_save
                ),
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSave,
            )
        }
    }
}

/** 화면 맨 위 제목 줄. 뒤로가기는 왼쪽 끝, 제목은 가운데. */
/** 카드관리 등 다른 서브 페이지와 같은 헤더 규격 — 좌측 제목 18 Bold, 40dp ‹, 아래 구분선. */
@Composable
private fun TitleBar(title: String, onBack: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Text("‹", color = colors.text, fontSize = 26.sp)
        }
        Text(
            text = title,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 18.sp,
            color = colors.text,
        )
    }
    KidHeaderDivider()
}

/** 구역 사이를 가르는 실선. 카드로 감싸는 대신 선 하나로만 나눈다. */
@Composable
private fun Hairline() {
    HorizontalDivider(color = LocalKidhackColors.current.border.copy(alpha = 0.7f))
}

/** 구역의 공통 여백. */
@Composable
private fun Section(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 18.dp),
        content = content,
    )
}

/** 하루를 넘기는 약속은 의미가 없다. */
private val HOUR_VALUES = (0..23).toList()

/** 5분 단위. 1분 단위로 두면 60 칸을 굴려야 해서 고르기 어렵다. */
private val MINUTE_VALUES = (0..55 step 5).toList()
