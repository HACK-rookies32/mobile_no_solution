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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/** 확인 시트가 필요한 되돌릴 수 없는 동작. */
enum class ChildAccountAction { FORCE_LOGOUT, UNPAIR }

data class ChildAccountUiState(
    /** 이 화면이 다루는 자녀 하나. 자녀 상세에서 고른 그 아이다. */
    val child: ChildProfile? = null,
    val loading: Boolean = true,
    val working: Boolean = false,
    val error: String? = null,
    /** 강제 로그아웃 성공 안내. 해제와 달리 화면이 그대로 남는다. */
    val info: String? = null,
    /** 자녀 계정이 지워져 화면을 닫아야 하는 상태. */
    val unpaired: Boolean = false,
    val pending: ChildAccountAction? = null,
)

/**
 * 자녀 계정 관리 — 강제 로그아웃 / 연결 해제.
 *
 * <p>전에는 자녀를 **전부 나열**했다. 그런데 이 화면은 자녀 상세에서 고른 <b>한 아이</b>를
 * 들고 들어온다. 다른 아이까지 늘어놓으면, 지금 보던 아이가 아닌 아이의 계정을 실수로
 * 지울 수 있다.
 *
 * <p>둘 다 부모만 할 수 있다(서버 `POST /children/{id}/logout`, `DELETE /children/{id}`).
 * 연결 해제는 연결만 끊는 게 아니라 자녀 계정을 소프트 삭제까지 하므로 되돌릴 수 없다 —
 * 문구와 확인 절차를 그 수준에 맞춘다.
 */
class ChildAccountViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildAccountUiState())
    val state: StateFlow<ChildAccountUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val children = childRepository.children()
                val id = selectedChildRepository.resolveId(children) ?: return@runCatching null
                // 상세 조회라야 로그인 아이디·연락처·주소까지 온다. 목록은 이름 정도만 준다.
                childRepository.child(id)
            }.onSuccess { child ->
                _state.update { it.copy(loading = false, child = child) }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.userMessage("자녀 정보를 불러오지 못했어요")) }
            }
        }
    }

    fun ask(action: ChildAccountAction) = _state.update { it.copy(pending = action, info = null, error = null) }

    fun dismiss() = _state.update { it.copy(pending = null) }

    fun confirm() {
        val action = _state.value.pending ?: return
        val child = _state.value.child ?: return
        viewModelScope.launch {
            _state.update { it.copy(working = true, pending = null, error = null, info = null) }
            runCatching {
                when (action) {
                    ChildAccountAction.FORCE_LOGOUT -> childRepository.forceLogout(child.id)
                    ChildAccountAction.UNPAIR -> childRepository.unpair(child.id)
                }
            }.onSuccess {
                when (action) {
                    ChildAccountAction.FORCE_LOGOUT ->
                        _state.update { it.copy(working = false, info = child.name + " 기기를 로그아웃시켰어요") }
                    // 계정이 지워졌다. 화면이 닫히도록 표시한다.
                    ChildAccountAction.UNPAIR -> _state.update { it.copy(working = false, unpaired = true) }
                }
            }.onFailure { t ->
                _state.update { it.copy(working = false, error = t.userMessage("처리하지 못했어요")) }
            }
        }
    }
}

@Composable
fun ParentChildAccountScreen(
    onBack: () -> Unit = {},
    viewModel: ChildAccountViewModel = koinViewModel(),
) {
    val colors = LocalKidhackColors.current
    val state by viewModel.state.collectAsStateWithLifecycle()
    val child = state.child

    // 연결 해제로 계정이 지워지면 이 화면은 더 볼 것이 없다. 목록으로 돌려보낸다.
    if (state.unpaired) onBack()

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
                Text("‹", color = colors.text, fontSize = 26.sp)
            }
            Text(
                stringResource(R.string.parent_child_account_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if ((state.loading && child == null) || state.error != null) {
                Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                    ParentLoadingOrError(state.loading && child == null, state.error, viewModel::load)
                }
            }
            if (child == null) return@Column

            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(colors.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    val photo = child.profileImageUrl
                    if (photo.isNullOrBlank()) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person),
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = colors.border,
                        )
                    } else {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(RemoteUrl.absolute(photo)).crossfade(true).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        child.name,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBlack,
                        fontSize = 18.sp,
                        letterSpacing = (-0.5).sp,
                        color = colors.text,
                    )
                    // paired 는 상세 응답에만 온다. 연결돼 있을 때만 초록 배지를 단다.
                    if (child.paired) {
                        Spacer(Modifier.height(6.dp))
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(PairedBg)
                                .padding(horizontal = 9.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check),
                                contentDescription = null,
                                modifier = Modifier.size(11.dp),
                                tint = PairedFg,
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                stringResource(R.string.parent_child_account_paired),
                                color = PairedFg,
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBold,
                                fontSize = 11.sp,
                            )
                        }
                    }
                }
            }

            GroupLabel(stringResource(R.string.parent_child_account_info))
            InfoRow(stringResource(R.string.parent_child_account_login_id), child.loginId)
            child.alias?.takeIf { it.isNotBlank() && it != child.name }?.let {
                InfoRow(stringResource(R.string.parent_child_account_alias), it)
            }
            InfoRow(stringResource(R.string.parent_child_account_phone), child.phone)
            InfoRow(stringResource(R.string.parent_child_account_email), child.email)
            InfoRow(stringResource(R.string.parent_child_account_address), child.address, last = true)

            state.info?.let {
                Text(
                    it,
                    modifier = Modifier.padding(start = 22.dp, top = 16.dp),
                    color = colors.safe,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.5.sp,
                )
            }

            GroupLabel(stringResource(R.string.parent_child_account_actions))
            ActionRow(
                title = stringResource(R.string.parent_child_account_force_logout),
                desc = stringResource(R.string.parent_child_account_force_logout_desc),
                danger = false,
                enabled = !state.working,
            ) { viewModel.ask(ChildAccountAction.FORCE_LOGOUT) }
            ActionRow(
                title = stringResource(R.string.parent_child_account_unpair),
                desc = stringResource(R.string.parent_child_account_unpair_desc),
                danger = true,
                enabled = !state.working,
                last = true,
            ) { viewModel.ask(ChildAccountAction.UNPAIR) }

            Spacer(Modifier.height(28.dp))
        }
    }

    val pending = state.pending
    if (pending != null && child != null) {
        ConfirmSheet(
            action = pending,
            childName = child.name,
            onConfirm = viewModel::confirm,
            onDismiss = viewModel::dismiss,
        )
    }
}

private val PairedBg = Color(0xFFE6F5EC)
private val PairedFg = Color(0xFF166B3C)
private val WarnBg = Color(0xFFFBEAEA)

/** 작은 회색 묶음 이름. 계정 설정·알림 설정과 같은 모양이다. */
@Composable
private fun GroupLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 22.dp, top = 24.dp, bottom = 4.dp),
        color = LocalKidhackColors.current.subtle,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

/** 읽기 전용 계정 정보 한 줄. 값이 없으면 「등록되지 않음」. */
@Composable
private fun InfoRow(label: String, value: String?, last: Boolean = false) {
    val colors = LocalKidhackColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.width(78.dp),
                color = colors.subtle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.5.sp,
            )
            Text(
                text = value?.takeIf { it.isNotBlank() } ?: stringResource(R.string.parent_child_account_unset),
                modifier = Modifier.weight(1f),
                color = if (value.isNullOrBlank()) colors.subtle else colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 14.sp,
            )
        }
        if (!last) HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
    }
}

/** 되돌릴 수 없는 동작 한 줄. 무슨 일이 일어나는지 설명이 붙어, 눌러 보기 전에 안다. */
@Composable
private fun ActionRow(
    title: String,
    desc: String,
    danger: Boolean,
    enabled: Boolean,
    last: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 22.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 14.5.sp,
                    letterSpacing = (-0.2).sp,
                    color = if (danger) colors.danger else colors.text,
                )
                Spacer(Modifier.height(3.dp))
                Text(desc, color = colors.subtle, fontSize = 11.5.sp, lineHeight = 17.sp)
            }
            Text("›", color = colors.border, fontSize = 17.sp)
        }
        if (!last) HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
    }
}

/**
 * 확인 시트.
 *
 * <p>연결 해제는 되돌릴 수 없는 계정 삭제라 일반 대화상자보다 한 단계 무겁게 묻는다 —
 * 무슨 일이 일어나는지 목록으로 적고, 실행 버튼을 빨강으로 둔다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConfirmSheet(
    action: ChildAccountAction,
    childName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalKidhackColors.current
    val destructive = action == ChildAccountAction.UNPAIR
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = colors.card,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 22.dp, end = 22.dp, bottom = 24.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (destructive) WarnBg else colors.brandLight),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(if (destructive) R.drawable.ic_warning else R.drawable.ic_key),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = if (destructive) colors.danger else colors.brandDark,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                text = stringResource(
                    if (destructive) R.string.parent_child_account_unpair_confirm_title
                    else R.string.parent_child_account_logout_confirm_title,
                    childName,
                ),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 18.sp,
                letterSpacing = (-0.4).sp,
                color = colors.text,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(
                    if (destructive) R.string.parent_child_account_unpair_confirm_message
                    else R.string.parent_child_account_logout_confirm_message,
                ),
                color = colors.muted,
                fontSize = 13.sp,
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(20.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(if (destructive) colors.danger else colors.text)
                    .clickable(onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(
                        if (destructive) R.string.parent_child_account_unpair
                        else R.string.parent_child_account_force_logout,
                    ),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 15.sp,
                )
            }
            Text(
                text = stringResource(R.string.parent_child_account_confirm_cancel),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 14.dp),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}
