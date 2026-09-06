package com.hack.kidhack.feature.parent

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
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
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidLineBreak
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.PairingRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class PairingUiState(
    val step: Int = 0,
    val code: String = "",
    val remaining: Int = 0,
    val child: ChildProfile? = null,
    val loading: Boolean = false,
    val error: String? = null,
    /** 「다음」을 눌렀는데 아직 연결된 자녀가 없는 상태. 조회 실패(error)와 구분한다. */
    val notConnected: Boolean = false,
)

class PairingViewModel(
    private val pairingRepository: PairingRepository,
    private val childRepository: ChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PairingUiState())
    val state: StateFlow<PairingUiState> = _state.asStateFlow()
    private var timer: Job? = null

    /**
     * 코드를 발급한 시점에 이미 연결돼 있던 자녀 id.
     *
     * 완료 화면에 **이번에 연결된** 자녀를 보여주기 위해 필요하다. 목록의 첫 항목을 쓰면
     * 둘째를 연결해도 첫째가 뜬다.
     */
    private var knownChildIds: Set<String> = emptySet()

    fun nextFromInstall() {
        issue(thenStep = 1)
    }

    fun issue(thenStep: Int = _state.value.step) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notConnected = false) }
            runCatching { pairingRepository.issueCode() }
                .onSuccess { issued ->
                    // 발급 직전의 자녀를 기억해 둔다. 실패해도 발급 자체는 막지 않는다.
                    knownChildIds = runCatching { childRepository.children() }
                        .getOrDefault(emptyList())
                        .map { it.id }
                        .toSet()
                    _state.update {
                        it.copy(loading = false, code = issued.pairingCode, remaining = issued.expiresIn, step = thenStep)
                    }
                    startTimer()
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.message) }
                }
        }
    }

    /**
     * 「다음」 — 발급한 코드가 **실제로 사용됐는지** 확인한 뒤에만 완료 화면으로 넘어간다.
     *
     * 서버는 연결이 성공하면 그 코드를 지우고(`PairingService.connect`), `GET /pairing/code` 는
     * 아직 살아 있는 코드만 준다. 그래서 발급한 코드가 목록에 남아 있으면 아무도 쓰지 않은 것이다.
     *
     * 예전에는 「자녀가 하나라도 있는가」만 봤다. 그래서 첫째가 이미 연결된 부모는 둘째용 코드를
     * 아무도 입력하지 않아도 완료 화면으로 넘어갔고, 거기에 **첫째**가 표시됐다.
     */
    fun confirm() {
        val issuedCode = _state.value.code
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, notConnected = false) }
            runCatching {
                val stillActive = pairingRepository.activeCodes().any { it.pairingCode == issuedCode }
                if (stillActive) null else childRepository.children()
            }
                .onSuccess { children ->
                    if (children == null) {
                        _state.update { it.copy(loading = false, notConnected = true) }
                        return@onSuccess
                    }
                    // 이번에 새로 붙은 자녀. 이미 연결돼 있던 자녀가 같은 부모의 새 코드를 다시
                    // 입력하면 목록이 늘지 않아 특정할 수 없다. 그때는 엉뚱한 자녀를 보여주느니
                    // 카드를 생략한다(연결 자체는 성공이므로 완료로 넘어간다).
                    val linked = children.firstOrNull { it.id !in knownChildIds }
                    _state.update { it.copy(loading = false, step = 2, child = linked) }
                    timer?.cancel()
                }
                .onFailure { t ->
                    _state.update { it.copy(loading = false, error = t.userMessage("연결 상태를 확인하지 못했어요")) }
                }
        }
    }

    private fun startTimer() {
        timer?.cancel()
        timer = viewModelScope.launch {
            while (_state.value.remaining > 0) {
                delay(1_000)
                _state.update { it.copy(remaining = (it.remaining - 1).coerceAtLeast(0)) }
            }
        }
    }
}

/**
 * 자녀 연결 — 세 걸음.
 *
 * <p>주황 그라데이션 껍데기와 48sp 이모지(📱 🔢 🎉)를 걷었다. 몇 걸음째인지는 단계 번호와
 * 진행 막대가 말한다 — 그림은 그 자리를 대신하지 못한다.
 *
 * <p>「새 코드 발급」은 <b>버튼이 아니라 밑줄 글자</b>다. 폭 전체 버튼 두 개가 세로로 붙어
 * 있으면 어느 쪽이 다음 걸음인지 흐려진다.
 */
@Composable
fun ParentPairingScreen(
    onBack: () -> Unit = {},
    onDone: () -> Unit = {},
    viewModel: PairingViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            // 카드관리 등 다른 서브 페이지와 같은 헤더 규격(위 12·아래 4).
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
                Text("‹", color = colors.text, fontSize = 26.sp)
            }
            Text(
                stringResource(R.string.parent_pairing_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
        }
        KidHeaderDivider()

        Text(
            text = stringResource(R.string.parent_pairing_step, state.step + 1, 3),
            modifier = Modifier.padding(start = 22.dp, top = 12.dp, bottom = 8.dp),
            color = colors.brandDark,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 11.5.sp,
            letterSpacing = 1.sp,
        )
        // 세 토막으로 나눈 막대. 지금 걸음이 어디까지 왔는지 색으로 읽힌다.
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(3) { index ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (index <= state.step) colors.brand else colors.line),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loading || state.error != null) {
                Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                    ParentLoadingOrError(state.loading, state.error)
                }
            }
            if (state.notConnected) {
                Text(
                    stringResource(R.string.parent_pairing_not_connected),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
                    color = colors.danger,
                    fontSize = 13.sp,
                )
            }

            when (state.step) {
                0 -> InstallStep(context)
                1 -> CodeStep(state = state, onCopy = { clipboard.setText(AnnotatedString(state.code)) })
                else -> DoneStep(state.child)
            }
            Spacer(Modifier.height(24.dp))
        }

        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 14.dp)) {
            when (state.step) {
                0 -> KidButton(
                    text = stringResource(R.string.parent_pairing_installed),
                    onClick = viewModel::nextFromInstall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
                1 -> {
                    KidButton(
                        text = stringResource(R.string.parent_pairing_next),
                        onClick = viewModel::confirm,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                        enabled = state.code.isNotBlank(),
                    )
                    // 버튼이 아니라 밑줄 글자. 다음 걸음과 경쟁하지 않는다.
                    Text(
                        text = stringResource(R.string.parent_pairing_refresh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.issue(1) }
                            .padding(top = 14.dp, bottom = 2.dp),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        textDecoration = TextDecoration.Underline,
                    )
                }
                else -> KidButton(
                    text = stringResource(R.string.parent_pairing_to_dashboard),
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                )
            }
        }
    }
}

/* ---- 1걸음: 설치 ---- */

@Composable
private fun ColumnScope.InstallStep(context: android.content.Context) {
    val colors = LocalKidhackColors.current
    StepHeadline(
        title = stringResource(R.string.parent_pairing_install_title),
        desc = stringResource(R.string.parent_pairing_install_desc),
    )
    Spacer(Modifier.height(26.dp))
    LinkRow(stringResource(R.string.parent_pairing_share_title)) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, context.getString(R.string.parent_pairing_share_text))
        }
        context.startActivity(
            Intent.createChooser(send, context.getString(R.string.parent_pairing_share)),
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
}

/* ---- 2걸음: 코드 ---- */

@Composable
private fun ColumnScope.CodeStep(state: PairingUiState, onCopy: () -> Unit) {
    val colors = LocalKidhackColors.current
    StepHeadline(
        title = stringResource(R.string.parent_pairing_code_title),
        desc = stringResource(R.string.parent_pairing_code_desc),
    )
    Spacer(Modifier.height(28.dp))
    // 여섯 칸에 주황 테두리를 두르면 「여기에 입력하라」로 읽힌다. 이 코드는 부모가
    // 읽어 주는 값이라 옅은 회색 칸에 담는다.
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        state.code.take(6).padEnd(6, ' ').forEach { ch ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(58.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = ch.toString().trim(),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 24.sp,
                    color = colors.text,
                )
            }
        }
    }
    Spacer(Modifier.height(16.dp))
    Row(
        modifier = Modifier.padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_clock),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = if (state.remaining > 0) colors.subtle else colors.danger,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (state.remaining > 0) {
                stringResource(R.string.parent_pairing_expires, formatMmSs(state.remaining))
            } else {
                stringResource(R.string.parent_pairing_expired)
            },
            color = if (state.remaining > 0) colors.subtle else colors.danger,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.5.sp,
        )
        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(17.dp))
                .background(colors.surface)
                .clickable(enabled = state.code.isNotBlank(), onClick = onCopy)
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_copy),
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = colors.text,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                stringResource(R.string.parent_pairing_copy),
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.5.sp,
            )
        }
    }
}

/* ---- 3걸음: 완료 ---- */

@Composable
private fun ColumnScope.DoneStep(child: ChildProfile?) {
    val colors = LocalKidhackColors.current
    Spacer(Modifier.height(40.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(62.dp).clip(CircleShape).background(Color(0xFFE7F4EC)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_check),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = colors.safe,
            )
        }
    }
    Spacer(Modifier.height(22.dp))
    Text(
        stringResource(R.string.parent_pairing_done_title),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 23.sp,
        letterSpacing = (-0.8).sp,
        color = colors.text,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(R.string.parent_pairing_done_desc),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 30.dp),
        color = colors.muted,
        fontSize = 13.5.sp,
        lineHeight = 22.sp,
        textAlign = TextAlign.Center,
        style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
    )

    // 이번에 연결된 자녀를 특정하지 못한 경우(기존 자녀의 재연결)에는 카드를 생략한다.
    // 더미값으로 메우거나 목록의 첫 자녀를 보여주면 「누가 연결됐는지」를 속이게 된다.
    if (child != null) {
        Spacer(Modifier.height(34.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier.size(66.dp).clip(CircleShape).background(colors.surface),
                contentAlignment = Alignment.Center,
            ) {
                val photo = child.profileImageUrl
                if (photo.isNullOrBlank()) {
                    Icon(
                        painter = painterResource(R.drawable.ic_person),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = colors.border,
                    )
                } else {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(RemoteUrl.absolute(photo))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                child.name,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 17.sp,
                letterSpacing = (-0.4).sp,
                color = colors.text,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.parent_pairing_connected),
                color = colors.safe,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.5.sp,
            )
        }
    }
}

/** 걸음마다 같은 자리에 서는 제목과 설명. */
@Composable
private fun ColumnScope.StepHeadline(title: String, desc: String) {
    val colors = LocalKidhackColors.current
    Spacer(Modifier.height(24.dp))
    Text(
        text = title,
        modifier = Modifier.padding(horizontal = 22.dp),
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 23.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.9).sp,
        color = colors.text,
        style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
    )
    Spacer(Modifier.height(10.dp))
    Text(
        text = desc,
        modifier = Modifier.padding(horizontal = 22.dp),
        color = colors.muted,
        fontSize = 13.5.sp,
        lineHeight = 22.sp,
        style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
    )
}

/** 눌러서 어딘가로 가는 한 줄. 테두리 없이 구분선만 둔다. */
@Composable
private fun ColumnScope.LinkRow(label: String, onClick: () -> Unit) {
    val colors = LocalKidhackColors.current
    HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 14.5.sp,
            letterSpacing = (-0.25).sp,
            color = colors.text,
        )
        Text("›", color = colors.border, fontSize = 17.sp)
    }
}
