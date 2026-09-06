package com.hack.kidhack.feature.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidSwitch
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.AppControlItem
import com.hack.kidhack.domain.model.InstalledApp
import com.hack.kidhack.domain.repository.AppControlRepository
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 목록 거르개.
 *
 * <p>서버 규칙은 두 칸뿐이다 — `enabled`(적용 여부)와 `dailyLimitMinutes`(0 이면 차단).
 * 「무제한」을 담을 값이 없어, 통제 목록에 있는 앱은 <b>차단 · 제한 · 미적용</b> 셋 중 하나다.
 *
 * <p>전에는 화면에 적히는 글자("무제한")를 비교해 판정했다. 표기를 바꾸면 거르개가 조용히
 * 깨지고, 실제로 「미적용」 규칙은 어느 칸에도 잡히지 않았다.
 */
enum class AppControlFilter {
    ALL,

    /** 아예 못 쓰게 해 둔 앱. `enabled && limit == 0` */
    BLOCKED,

    /** 하루 몇 분만. `enabled && limit > 0` */
    LIMITED,

    /** 규칙은 남아 있고 걸리지 않는 앱. `!enabled` */
    OFF,
    ;

    fun matches(item: AppControlItem): Boolean = when (this) {
        ALL -> true
        BLOCKED -> item.enabled && item.dailyLimitMinutes == 0
        LIMITED -> item.enabled && (item.dailyLimitMinutes ?: 0) > 0
        OFF -> !item.enabled
    }
}

data class AppControlUiState(
    /** 자녀 목록 (id to 이름). 이 화면의 모든 설정은 고른 자녀 한 명 기준이다. */
    val children: List<Pair<String, String>> = emptyList(),
    val selectedChildId: String? = null,
    val items: List<AppControlItem> = emptyList(),
    val installedApps: List<InstalledApp> = emptyList(),
    val selectedApp: InstalledApp? = null,
    /** 고치는 중인 규칙. 새 앱을 담는 중이면 null 이다. */
    val editingRule: AppControlItem? = null,
    val filter: AppControlFilter = AppControlFilter.ALL,
    val showAppSelection: Boolean = false,
    val showControlSettings: Boolean = false,
    /** 자녀 하루 총 사용 한도(분). null 이면 제한 없음 — 0("한도 0분")과 의미가 다르다. */
    val dailyLimitMinutes: Int? = null,
    val savingLimit: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
)

class AppControlViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
    private val appControlRepository: AppControlRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(AppControlUiState())
    val state: StateFlow<AppControlUiState> = _state.asStateFlow()

    init { load() }

    fun onFilter(f: AppControlFilter) = _state.update { it.copy(filter = f) }
    
    fun toggleAppSelection(show: Boolean) {
        _state.update { it.copy(showAppSelection = show) }
        if (show) loadInstalledApps()
    }

    fun selectAppForControl(app: InstalledApp) {
        _state.update { it.copy(selectedApp = app, showAppSelection = false, showControlSettings = true) }
    }

    fun closeControlSettings() {
        _state.update { it.copy(showControlSettings = false, selectedApp = null, editingRule = null) }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children)
                    ?: return@runCatching Triple(children, null, emptyList<AppControlItem>() to null)
                // 총 한도는 부가 정보라 실패해도 규칙 목록은 그대로 보여 준다.
                val limit = runCatching { appControlRepository.screenTimeLimit(id) }.getOrNull()
                Triple(children, id, appControlRepository.items(id) to limit)
            }.onSuccess { (children, id, loaded) ->
                val (items, limit) = loaded
                _state.update {
                    it.copy(
                        loading = false,
                        children = children.map { c -> c.id to c.name },
                        selectedChildId = id,
                        items = items,
                        dailyLimitMinutes = limit,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    /**
     * 볼 자녀를 바꾼다.
     *
     * <p>고른 자녀는 앱 전체가 공유한다(대시보드·캡처·사용량도 같은 값을 본다). 화면마다
     * 다른 자녀를 보고 있으면 부모가 다른 아이 설정을 건드리게 된다.
     */
    fun selectChild(childId: String) {
        if (childId == _state.value.selectedChildId) return
        viewModelScope.launch {
            selectedChildRepository.select(childId)
            load()
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            runCatching {
                // load() 가 resolveId 로 이미 정한 자녀를 그대로 쓴다. current() 를 직접 부르면,
                // 자녀가 하나뿐이라 칩을 누른 적이 없는 부모에게는 null 이 나와 목록이 늘 비었다.
                val id = _state.value.selectedChildId ?: return@runCatching emptyList()
                appControlRepository.getInstalledApps(id)
            }.onSuccess { apps ->
                _state.update { it.copy(installedApps = apps) }
            }.onFailure { t ->
                _state.update { it.copy(error = t.userMessage("앱 목록을 불러오지 못했어요")) }
            }
        }
    }

    /**
     * 차단 토글. 서버에 `blocked` 필드가 없어 **하루 한도 0분**으로 표현한다.
     * 해제하면 이전 한도를 알 수 없으므로 무제한(null)으로 되돌린다.
     */
    /**
     * 규칙을 켜고 끈다.
     *
     * <p>전에는 스위치가 「차단 안 함」을 켜는 것이었고, 켤 때 `dailyLimitMinutes = null` 을
     * 보냈다. 서버에서 <b>null 은 「이 칸은 그대로 두라」</b>는 뜻이라(RuleUpdateRequest),
     * 한도 0(=차단)이 그대로 남았다. <b>차단은 되는데 풀리지 않았다.</b>
     *
     * <p>이제 스위치는 `enabled` 하나만 오간다. 차단인지 몇 분인지는 줄을 눌러 따로 고른다.
     */
    fun toggleEnabled(item: AppControlItem, enabled: Boolean) {
        viewModelScope.launch {
            runCatching {
                appControlRepository.updateRule(
                    ruleId = item.ruleId,
                    // 한도는 건드리지 않는다. 껐다 켜도 걸어 둔 분이 그대로 살아 있어야 한다.
                    dailyLimitMinutes = null,
                    enabled = enabled,
                )
            }.onSuccess { updated ->
                _state.update { s ->
                    s.copy(items = s.items.map { if (it.ruleId == updated.ruleId) updated else it })
                }
            }.onFailure { t ->
                _state.update { it.copy(error = t.userMessage("설정을 바꾸지 못했어요")) }
            }
        }
    }

    /** 이미 통제 중인 앱의 한도를 고치려고 시트를 연다. */
    fun editRule(item: AppControlItem) {
        _state.update {
            it.copy(
                selectedApp = InstalledApp(item.packageName, item.name),
                editingRule = item,
                showControlSettings = true,
            )
        }
    }

    fun saveControl(blocked: Boolean, limitMinutes: Int?) {
        val app = _state.value.selectedApp ?: return
        viewModelScope.launch {
            runCatching {
                val id = _state.value.selectedChildId ?: return@launch
                val existing = _state.value.items.firstOrNull { it.packageName == app.packageName }
                val limit = if (blocked) 0 else limitMinutes
                if (existing != null) {
                    appControlRepository.updateRule(existing.ruleId, limit, enabled = true)
                } else {
                    appControlRepository.addRule(id, app.packageName, app.appName, limit)
                }
            }.onSuccess { newItem ->
                _state.update { s ->
                    s.copy(
                        items = (s.items.filterNot { it.packageName == newItem.packageName } + newItem),
                        showControlSettings = false,
                        selectedApp = null,
                        editingRule = null,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(error = t.userMessage("설정을 저장하지 못했어요")) }
            }
        }
    }

    /**
     * 하루 총 사용 한도 저장. 빈 값이면 제한을 푼다(서버에 null 을 보낸다).
     *
     * 앱 통제 규칙의 한도는 **앱별**이고 이 값은 **하루 전체**다. 서버에서도 별개 개념이다.
     */
    fun saveDailyLimit(minutes: Int?) {
        viewModelScope.launch {
            _state.update { it.copy(savingLimit = true, error = null) }
            runCatching {
                val id = _state.value.selectedChildId ?: return@runCatching null
                appControlRepository.setScreenTimeLimit(id, minutes)
            }.onSuccess { applied ->
                _state.update { it.copy(savingLimit = false, dailyLimitMinutes = applied) }
            }.onFailure { t ->
                _state.update { it.copy(savingLimit = false, error = t.userMessage("한도를 저장하지 못했어요")) }
            }
        }
    }

    fun removeControl(ruleId: String) {
        viewModelScope.launch {
            runCatching { appControlRepository.deleteRule(ruleId) }
                .onSuccess {
                    _state.update { s -> s.copy(items = s.items.filterNot { it.ruleId == ruleId }) }
                }
        }
    }
}

/**
 * 앱 통제.
 *
 * <p>스위치가 「차단 안 함」을 켜는 것이라 켜면 초록·끄면 빨강이었다. 스위치 자체가
 * 신호등이 되어 무엇이 켜진 상태인지 헷갈렸고, 켜는 쪽이 서버에서 동작하지도 않았다.
 *
 * <p>이제 스위치는 <b>규칙을 걸까 말까</b> 하나만 묻고, 차단인지 몇 분인지는 줄에 글자로
 * 적는다. 값을 고치려면 줄을 누른다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentAppControlScreen(
    onBack: () -> Unit = {},
    onOpenScreenTime: () -> Unit = {},
    viewModel: AppControlViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val filtered = state.items.filter { state.filter.matches(it) }

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
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u2039", color = colors.text, fontSize = 26.sp)
            }
            Text(
                stringResource(R.string.parent_appcontrol_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
        }

        Box(Modifier.weight(1f)) {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                // 이 화면의 모든 설정이 자녀 한 명 기준이라, 무엇보다 먼저 누구인지 고르게 한다.
                if (state.children.size >= 2) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        state.children.forEach { (id, name) ->
                            ControlChip(name, id == state.selectedChildId) { viewModel.selectChild(id) }
                        }
                    }
                }

                DailyLimitTile(current = state.dailyLimitMinutes, onClick = onOpenScreenTime)

                // 눌러 보기 전에 무엇이 몇 개인지 안다.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 20.dp, end = 20.dp, top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                ) {
                    AppControlFilter.entries.forEach { f ->
                        val count = state.items.count { f.matches(it) }
                        ControlChip(
                            label = stringResource(f.labelRes()) + " " + count,
                            selected = state.filter == f,
                        ) { viewModel.onFilter(f) }
                    }
                }
                Spacer(Modifier.height(6.dp))

                if (state.loading && state.items.isEmpty() || state.error != null) {
                    Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                        ParentLoadingOrError(state.loading && state.items.isEmpty(), state.error, viewModel::load)
                    }
                }

                if (!state.loading && state.items.isEmpty()) {
                    Text(
                        text = stringResource(R.string.parent_appcontrol_empty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 48.dp),
                        color = colors.muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                } else if (filtered.isEmpty() && state.items.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.parent_appcontrol_filter_empty),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 44.dp),
                        color = colors.muted,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                filtered.forEachIndexed { index, app ->
                    AppRuleRow(
                        item = app,
                        showDivider = index > 0,
                        onClick = { viewModel.editRule(app) },
                        onToggle = { on -> viewModel.toggleEnabled(app, on) },
                    )
                }
                Spacer(Modifier.height(90.dp))
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(23.dp))
                    .background(colors.brand)
                    .clickable { viewModel.toggleAppSelection(true) }
                    .padding(start = 15.dp, end = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_plus),
                    contentDescription = null,
                    modifier = Modifier.size(17.dp),
                    tint = Color.White,
                )
                Spacer(Modifier.width(7.dp))
                Text(
                    stringResource(R.string.parent_appcontrol_add),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.sp,
                )
            }
        }
    }

    if (state.showAppSelection) {
        AppPickerSheet(
            apps = state.installedApps,
            already = state.items.map { it.packageName }.toSet(),
            onDismiss = { viewModel.toggleAppSelection(false) },
            onPick = viewModel::selectAppForControl,
        )
    }

    val editing = state.selectedApp
    if (state.showControlSettings && editing != null) {
        RuleSheet(
            appName = editing.appName,
            existing = state.editingRule,
            onDismiss = viewModel::closeControlSettings,
            onSave = { blocked, minutes -> viewModel.saveControl(blocked, minutes) },
            onRemove = state.editingRule?.let { rule ->
                { viewModel.removeControl(rule.ruleId); viewModel.closeControlSettings() }
            },
        )
    }
}

/* ---- 조각 ---- */

@Composable
private fun ControlChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) colors.text else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 7.dp),
        color = if (selected) Color.White else colors.muted,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

/** 하루 총 한도. 앱별 규칙과 다른 값이라 목록 위에 따로 둔다. */
@Composable
private fun DailyLimitTile(current: Int?, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.parent_appcontrol_daily_limit),
                modifier = Modifier.weight(1f),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.sp,
            )
            Text(
                stringResource(R.string.parent_appcontrol_change),
                color = colors.subtle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 11.5.sp,
            )
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = current?.let { formatDuration(it * 60) }
                ?: stringResource(R.string.parent_appcontrol_no_limit),
            color = if (current == null) colors.subtle else colors.text,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBlack,
            fontSize = 22.sp,
            letterSpacing = (-0.8).sp,
        )
    }
}

/**
 * 통제 중인 앱 한 줄.
 *
 * <p>지금 무엇이 걸려 있는지를 <b>글자로</b> 적는다. 스위치 색만으로 알리면 「초록이 허용인가
 * 차단인가」를 매번 되짚어야 한다.
 */
@Composable
private fun AppRuleRow(
    item: AppControlItem,
    showDivider: Boolean,
    onClick: () -> Unit,
    onToggle: (Boolean) -> Unit,
) {
    val colors = LocalKidhackColors.current
    if (showDivider) {
        HorizontalDivider(modifier = Modifier.padding(start = 22.dp), color = colors.line)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = item.name,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.5.sp,
                letterSpacing = (-0.25).sp,
                color = colors.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            val blocked = item.enabled && item.dailyLimitMinutes == 0
            val limited = item.enabled && (item.dailyLimitMinutes ?: 0) > 0
            Text(
                text = when {
                    !item.enabled -> stringResource(R.string.parent_appcontrol_state_off)
                    blocked -> stringResource(R.string.parent_appcontrol_state_blocked)
                    limited -> stringResource(
                        R.string.parent_appcontrol_state_limited,
                        formatDuration((item.dailyLimitMinutes ?: 0) * 60),
                    )
                    else -> stringResource(R.string.parent_appcontrol_state_free)
                },
                color = when {
                    !item.enabled -> colors.subtle
                    blocked -> colors.danger
                    limited -> colors.brandDark
                    else -> colors.subtle
                },
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 11.5.sp,
            )
        }
        Spacer(Modifier.width(10.dp))
        KidSwitch(checked = item.enabled, onCheckedChange = onToggle)
    }
}

/* ---- 시트 ---- */

/** 통제할 앱 고르기. 목록은 자녀 기기가 올린 것이라 부모가 만들지 않는다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppPickerSheet(
    apps: List<InstalledApp>,
    already: Set<String>,
    onDismiss: () -> Unit,
    onPick: (InstalledApp) -> Unit,
) {
    val colors = LocalKidhackColors.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.card,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Text(
                stringResource(R.string.parent_appcontrol_pick_title),
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 4.dp),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 17.sp,
                color = colors.text,
            )
            Text(
                stringResource(R.string.parent_appcontrol_pick_lead),
                modifier = Modifier.padding(start = 22.dp, end = 22.dp, bottom = 10.dp),
                color = colors.subtle,
                fontSize = 12.5.sp,
            )
            if (apps.isEmpty()) {
                Text(
                    stringResource(R.string.parent_appcontrol_pick_empty),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    color = colors.muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            }
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(apps, key = { it.packageName }) { app ->
                    val added = app.packageName in already
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !added) { onPick(app) }
                            .padding(horizontal = 22.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = app.appName,
                            modifier = Modifier.weight(1f),
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = if (added) colors.subtle else colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (added) {
                            Text(
                                stringResource(R.string.parent_appcontrol_pick_added),
                                color = colors.subtle,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 11.5.sp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 한 앱에 무엇을 걸지 고르는 시트.
 *
 * <p>「차단 / 시간 제한」을 <b>둘 중 하나로</b> 고르게 한다. 전에는 차단 스위치 아래에 분
 * 입력칸이 따로 있어, 차단을 켠 채 분을 적어 두면 무엇이 적용되는지 알 수 없었다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSheet(
    appName: String,
    existing: AppControlItem?,
    onDismiss: () -> Unit,
    onSave: (blocked: Boolean, minutes: Int?) -> Unit,
    onRemove: (() -> Unit)?,
) {
    val colors = LocalKidhackColors.current
    var blocked by rememberSaveable(appName) {
        mutableStateOf(existing != null && existing.dailyLimitMinutes == 0)
    }
    var minutes by rememberSaveable(appName) {
        mutableStateOf(existing?.dailyLimitMinutes?.takeIf { it > 0 }?.toString().orEmpty())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.card,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
        ) {
            Text(
                appName,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 17.sp,
                color = colors.text,
            )
            Spacer(Modifier.height(18.dp))

            ModeRow(
                label = stringResource(R.string.parent_appcontrol_mode_block),
                desc = stringResource(R.string.parent_appcontrol_mode_block_desc),
                selected = blocked,
            ) { blocked = true }
            ModeRow(
                label = stringResource(R.string.parent_appcontrol_mode_limit),
                desc = stringResource(R.string.parent_appcontrol_mode_limit_desc),
                selected = !blocked,
            ) { blocked = false }

            if (!blocked) {
                Spacer(Modifier.height(14.dp))
                KidTextField(
                    value = minutes,
                    onValueChange = { v -> if (v.all { it.isDigit() } && v.length <= 4) minutes = v },
                    label = stringResource(R.string.parent_appcontrol_limit_label),
                    placeholder = stringResource(R.string.parent_appcontrol_limit_hint),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }

            Spacer(Modifier.height(22.dp))
            KidButton(
                text = stringResource(R.string.parent_appcontrol_save),
                onClick = { onSave(blocked, minutes.toIntOrNull()) },
                modifier = Modifier.fillMaxWidth(),
                // 시간 제한을 골랐으면 분을 적어야 저장할 수 있다. 빈 값으로 보내면
                // 서버에서 null(=미변경)이 되어 아무 일도 일어나지 않는다.
                enabled = blocked || (minutes.toIntOrNull() ?: 0) > 0,
            )
            if (onRemove != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.parent_appcontrol_remove),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onRemove)
                        .padding(vertical = 13.dp),
                    color = colors.danger,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** 둘 중 하나를 고르는 줄. 라디오 대신 테두리로 고른 쪽을 말한다. */
@Composable
private fun ModeRow(label: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.brandLight else colors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                color = if (selected) colors.brandDark else colors.text,
            )
            Spacer(Modifier.height(3.dp))
            Text(desc, color = colors.subtle, fontSize = 11.5.sp)
        }
        if (selected) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
                tint = colors.brandDark,
            )
        }
    }
}

private fun AppControlFilter.labelRes(): Int = when (this) {
    AppControlFilter.ALL -> R.string.parent_filter_all
    AppControlFilter.BLOCKED -> R.string.parent_filter_blocked
    AppControlFilter.LIMITED -> R.string.parent_filter_limited
    AppControlFilter.OFF -> R.string.parent_filter_off
}
