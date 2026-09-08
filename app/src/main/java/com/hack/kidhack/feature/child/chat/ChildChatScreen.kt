package com.hack.kidhack.feature.child.chat

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.ChatMessage
import com.hack.kidhack.domain.model.MessageType
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.feature.chat.ChatAudioBubble
import com.hack.kidhack.feature.chat.ChatComposer
import com.hack.kidhack.feature.chat.ChatImageBubble
import com.hack.kidhack.feature.parent.formatChatSentAt
import org.koin.androidx.compose.koinViewModel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * 자녀 ↔ 부모 채팅.
 *
 * <p>말풍선 폭이 화면의 70%로 <b>고정</b>이라 「응」 한 글자도 화면 대부분을 차지했다. 이제
 * 글자만큼만 커진다. 배경도 흰색이라 흰 말풍선이 테두리 1dp 로만 겨우 구분됐는데, 캔버스를
 * 회색으로 내려 말풍선이 그 위에 얹히게 했다.
 *
 * <p>시각은 말풍선 <b>바깥</b>에 둔다. 안에 넣으면 한 글자짜리 말도 두 줄이 되어, 짧게 주고받는
 * 대화일수록 화면이 길어진다.
 *
 * <p>목록은 뒤집어 쌓는다(`reverseLayout`). 키보드가 올라오면 목록 높이가 줄어드는데, 위에서
 * 쌓으면 기준점이 맨 위라 줄어드는 만큼 마지막 말이 화면 밖으로 밀려난다. 아래를 기준으로
 * 삼으면 마지막 말이 입력창 바로 위에 붙은 채로 남는다.
 */
@Composable
fun ChildChatScreen(
    viewModel: ChildChatViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val listState = rememberLazyListState()

    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.onScreenResume()
        onPauseOrDispose { }
    }

    // 새 말이 붙었을 때만 아래로 내린다. 크기로 판단하면 지난 대화를 앞에 붙일 때도 내려가서,
    // 위로 올려 읽던 자리를 잃는다. 뒤집힌 목록이라 가장 최근이 0 번이다.
    val newestId = state.messages.lastOrNull()?.id
    LaunchedEffect(newestId) {
        if (newestId != null) listState.animateScrollToItem(0)
    }

    LaunchedEffect(state.error) {
        val message = state.error
        // 방 로드 실패는 화면에 다시 시도를 보여 주므로 토스트만 띄우고 clear 하지 않음
        if (message != null && state.room != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    val partner = stringResource(R.string.child_chat_mom)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        // 다른 탭은 제목이 곧 화면 이름이지만, 채팅방은 <b>상대</b>가 제목이다.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(name = partner, size = 34, fontSize = 14)
            Spacer(Modifier.size(10.dp))
            Text(
                text = partner,
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
            )
            // 서버가 주는 값은 안 읽음 수뿐이라, 있으면 브랜드색 배지로 붙인다.
            val unread = state.room?.unreadCount ?: 0
            if (unread > 0) {
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "$unread",
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brand)
                        .padding(horizontal = 7.dp, vertical = 1.dp),
                    color = Color.White,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 11.sp,
                )
            }
        }
        KidHeaderDivider()

        if (!state.loading && state.room == null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(colors.surface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = state.error ?: stringResource(R.string.child_chat_empty),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 14.sp,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.child_chat_retry),
                        color = colors.brand,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 15.sp,
                        modifier = Modifier.clickable(onClick = viewModel::load),
                    )
                }
            }
        } else {
            // 뒤집어 쌓는다. 목록의 기준점이 <b>맨 아래</b>가 되어, 키보드가 올라와 목록이
            // 줄어들 때도 마지막 말이 입력창 바로 위에 그대로 붙어 있는다. 위에서부터 쌓으면
            // 기준점이 맨 위라, 높이가 줄어드는 만큼 아래쪽 말이 화면 밖으로 밀려난다.
            val shown = remember(state.messages) { state.messages.asReversed() }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(colors.surface),
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                reverseLayout = true,
            ) {
                itemsIndexed(shown, key = { _, m -> m.id }) { index, message ->
                    // 뒤집힌 목록이라 <b>더 오래된</b> 말이 한 칸 뒤에 있다.
                    val older = shown.getOrNull(index + 1)
                    val date = chatDate(message.sentAt)
                    // 날짜를 못 읽는 형식이면 칩을 그리지 않는다. 틀린 날짜보다 없는 편이 낫다.
                    val showDate = date != null && date != older?.let { chatDate(it.sentAt) }
                    // 한 칸 안에서 여러 개를 그대로 늘어놓으면 reverseLayout 이 그 안쪽 순서까지
                    // 뒤집어, 날짜 칩이 말풍선 아래로 내려간다. Column 으로 묶어 한 덩이로 만든다.
                    Column {
                        if (showDate) {
                            DateChip(date)
                            Spacer(Modifier.height(6.dp))
                        }
                        ChatBubble(
                            message = message,
                            partner = partner,
                            // 같은 사람이 잇달아 보내면 얼굴은 한 번만 — 같은 얼굴이 줄마다
                            // 반복되면 누가 말했는지가 아니라 얼굴이 읽힌다.
                            showAvatar = showDate || older?.fromRole != message.fromRole,
                        )
                    }
                }
                if (state.loading) {
                    item {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = colors.brand)
                        }
                    }
                }
                if (!state.loading && state.messages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.child_chat_empty),
                            modifier = Modifier.fillMaxWidth(),
                            color = colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }

            ChatComposer(
                draft = state.draft,
                onDraft = viewModel::onDraft,
                onSend = viewModel::send,
                canSend = state.canSend,
                onImage = viewModel::sendImage,
                onAudio = viewModel::sendAudio,
                hint = stringResource(R.string.child_chat_hint),
                sendContentDescription = stringResource(R.string.child_chat_send),
                imeSendAction = true,
                // 탭 껍데기(TabShell)가 이미 탭 바 높이만큼 비워 두고 그만큼을 소비했다고
                // 알려 주므로, 여기서는 모자란 만큼만 더하면 된다.
                modifier = Modifier.imePadding(),
            )
        }
    }
}

/** 날짜가 바뀌는 자리에만 놓는 가운데 칩. */
@Composable
private fun DateChip(date: LocalDate?) {
    val colors = LocalKidhackColors.current
    if (date == null) return
    val label = when (date) {
        LocalDate.now() -> stringResource(R.string.child_chat_today)
        LocalDate.now().minusDays(1) -> stringResource(R.string.child_chat_yesterday)
        else -> date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.getDefault()))
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text = label,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(colors.border)
                .padding(horizontal = 10.dp, vertical = 3.dp),
            color = colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, partner: String, showAvatar: Boolean) {
    val colors = LocalKidhackColors.current
    val mine = message.fromRole == Role.CHILD
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            if (showAvatar) {
                Avatar(name = partner, size = 26, fontSize = 11)
            } else {
                // 얼굴을 지워도 말풍선 왼쪽 줄은 맞춰 둔다.
                Spacer(Modifier.size(26.dp))
            }
            Spacer(Modifier.size(8.dp))
        } else {
            Text(
                text = chatTime(message.sentAt),
                color = colors.subtle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 10.sp,
            )
            Spacer(Modifier.size(6.dp))
        }
        when (message.type) {
            MessageType.IMAGE -> ChatImageBubble(mediaUrl = message.mediaUrl)
            MessageType.AUDIO -> ChatAudioBubble(
                mediaUrl = message.mediaUrl,
                bubbleColor = if (mine) colors.brand else colors.card,
                contentColor = if (mine) Color.White else colors.text,
            )
            MessageType.TEXT -> Text(
                text = message.text,
                modifier = Modifier
                    // 폭을 고정하지 않는다. 글자가 짧으면 말풍선도 짧아야 한다.
                    .widthIn(max = 240.dp)
                    .clip(
                        if (mine) {
                            RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp)
                        } else {
                            RoundedCornerShape(16.dp, 16.dp, 16.dp, 4.dp)
                        },
                    )
                    .background(if (mine) colors.brand else colors.card)
                    .padding(horizontal = 14.dp, vertical = 9.dp),
                color = if (mine) Color.White else colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 14.sp,
            )
        }
        if (!mine) {
            Spacer(Modifier.size(6.dp))
            Text(
                text = chatTime(message.sentAt),
                color = colors.subtle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun Avatar(name: String, size: Int, fontSize: Int) {
    val colors = LocalKidhackColors.current
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(colors.brandLight),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            name.trim().take(1).ifBlank { "?" },
            color = colors.brandDark,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = fontSize.sp,
        )
    }
}

/** 서버 시각 문자열 → 날짜. 형식이 다르면 null(= 날짜 칩을 그리지 않는다). */
private fun chatDate(raw: String): LocalDate? = parseSentAt(raw)?.toLocalDate()

/**
 * 말풍선 옆에 붙는 시각.
 *
 * <p>날짜는 위쪽 칩이 이미 말했으므로 시:분만 남긴다. 못 읽는 형식이면 부모 화면과 같은
 * 규칙([formatChatSentAt])으로 넘긴다.
 */
private fun chatTime(raw: String): String =
    parseSentAt(raw)?.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
        ?: formatChatSentAt(raw)

private fun parseSentAt(raw: String): LocalDateTime? {
    if (raw.isBlank()) return null
    val normalized = raw.replace(Regex("""\.\d+"""), "")
    return runCatching {
        LocalDateTime.parse(normalized, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
    }.getOrNull()
}
