package com.hack.kidhack.feature.child.pay

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidButtonVariant
import com.hack.kidhack.core.designsystem.component.KidLabeledSection
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.component.QrCode
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildTabScaffold
import org.koin.androidx.compose.koinViewModel

/**
 * 루키즈 페이 (자녀 탭).
 *
 * 자녀는 잔액 한도 안에서 **결제 QR** 을 띄우고, 매장 단말이 스캔·승인하면 결제가 완료된다.
 * 금액은 자녀가 정하지 않고 단말이 확정한다(보내기·회수는 부모 기능이라 여기 없다).
 *
 * <p>잔액을 흰 카드 위 숫자로만 두었더니 페이 화면인데 얼굴이 없었다. 잔액이 곧 이 화면의
 * 주인공이므로 <b>색 있는 카드</b>로 세우고, 결제 QR 버튼을 그 안에 넣는다 — 결제는 잔액이
 * 하는 일이지 따로 떠 있는 버튼이 아니다. 화면에서 색을 쓰는 곳은 이 카드 하나뿐이다.
 *
 * <p>바탕은 [Brand] 다. 이 앱에서 <b>채워진 면</b>은 버튼이든 말풍선이든 전부 이 색이고,
 * [BrandDark] 는 흰 바탕 위 글자·아이콘 색으로만 쓴다. 한때 대비를 벌어 보려고 이 카드만
 * BrandDark 로 깔았는데, 큰 면에 쓰이는 순간 홈과 다른 주황으로 보였다. 색은 한 벌로 보이는
 * 것이 먼저다.
 *
 * <p>흰 글씨 대비는 2.6:1 로 WCAG 기준에 못 미친다. 이 앱의 주황 버튼(KidButton Primary)이
 * 모두 같은 조건이라, 이 카드만 다르게 두면 화면이 갈라진다. 고치려면 [Brand] 자체를 한 단
 * 내려야 한다 — 한 곳이 아니라 색 하나를 바꾸는 일이다.
 */
@Composable
fun ChildPayScreen(
    onBack: () -> Unit = {},
    viewModel: ChildPayViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    // 매장 단말 테스트 다이얼로그 입력값. 화면 로컬 상태로만 둔다.
    var showTestDialog by remember { mutableStateOf(false) }
    var testStore by remember { mutableStateOf("") }
    var testAmount by remember { mutableStateOf("") }

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    ChildTabScaffold(title = stringResource(R.string.child_tab_pay)) {
        val ticket = state.ticket

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.brand)
                .padding(18.dp),
        ) {
            Text(
                stringResource(R.string.child_pay_balance_label),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.child_pay_balance_value, formatPoint(state.balance)),
                color = Color.White,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 30.sp,
            )
            // QR 이 이미 떠 있으면 아래 카드가 그 일을 하고 있으므로 버튼을 두 번 두지 않는다.
            if (ticket == null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.child_pay_issue),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (state.issuing) colors.brandLight else colors.card)
                        .clickable(enabled = !state.issuing, onClick = viewModel::issueQr)
                        .padding(vertical = 14.dp),
                    color = colors.brandDark,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (ticket == null) {
            Text(
                stringResource(R.string.child_pay_balance_note),
                color = colors.muted,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        } else {
            KidSectionCard(contentPadding = PaddingValues(16.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        stringResource(R.string.child_pay_qr_title),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .size(220.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(12.dp),
                    ) {
                        QrCode(content = ticket.qrToken, modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.child_pay_qr_expires, state.secondsLeft),
                        color = colors.brand,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.child_pay_qr_hint),
                        color = colors.muted,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                    // 매장 단말이 QR 을 찍는 걸 대신하는 테스트 버튼. 이 화면에서 결제를 끝까지
                    // 돌려 볼 수 있게 둔다(시연·개발용).
                    KidButton(
                        text = stringResource(R.string.child_pay_test),
                        onClick = {
                            testStore = ""
                            testAmount = ""
                            showTestDialog = true
                        },
                        modifier = Modifier.fillMaxWidth(),
                        variant = KidButtonVariant.Secondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    KidButton(
                        text = stringResource(R.string.child_pay_cancel),
                        onClick = viewModel::cancelTicket,
                        modifier = Modifier.fillMaxWidth(),
                        variant = KidButtonVariant.Ghost,
                    )
                }
            }
        }

        if (showTestDialog && ticket != null) {
            val amountValue = testAmount.toIntOrNull() ?: 0
            AlertDialog(
                onDismissRequest = { if (!state.testPaying) showTestDialog = false },
                title = {
                    Text(
                        stringResource(R.string.child_pay_test_dialog_title),
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                    )
                },
                text = {
                    Column {
                        Text(
                            stringResource(R.string.child_pay_test_dialog_desc),
                            color = colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 12.sp,
                        )
                        Spacer(Modifier.height(14.dp))
                        KidTextField(
                            value = testStore,
                            onValueChange = { testStore = it },
                            label = stringResource(R.string.child_pay_test_store_label),
                            placeholder = stringResource(R.string.child_pay_test_store_hint),
                        )
                        Spacer(Modifier.height(10.dp))
                        KidTextField(
                            value = testAmount,
                            onValueChange = { input -> testAmount = input.filter { it.isDigit() }.take(9) },
                            label = stringResource(R.string.child_pay_test_amount_label),
                            placeholder = stringResource(R.string.child_pay_test_amount_hint),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = amountValue > 0 && !state.testPaying,
                        onClick = {
                            viewModel.testPay(testStore, amountValue)
                            showTestDialog = false
                        },
                    ) {
                        Text(
                            stringResource(
                                if (state.testPaying) R.string.child_pay_test_approving
                                else R.string.child_pay_test_approve,
                            ),
                            color = colors.brand,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                        )
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !state.testPaying,
                        onClick = { showTestDialog = false },
                    ) {
                        Text(
                            stringResource(R.string.child_pay_cancel),
                            color = colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                        )
                    }
                },
            )
        }

        KidLabeledSection(
            label = stringResource(R.string.child_pay_history),
            contentPadding = PaddingValues(horizontal = 14.dp),
        ) {
            if (state.history.isEmpty()) {
                Text(
                    stringResource(R.string.child_pay_history_empty),
                    color = colors.muted,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 14.dp),
                )
            }
            state.history.forEachIndexed { index, item ->
                val cancelled = item.status == "CANCELLED"
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 같은 카드 아이콘을 줄마다 반복하면 아이콘이 줄을 구분해 주지 못한다.
                    // 가게 이름 첫 글자가 훨씬 빨리 읽힌다.
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(colors.line),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            item.storeName.trim().take(1).ifBlank { "?" },
                            color = if (cancelled) colors.subtle else colors.muted,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 13.sp,
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            item.storeName,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontBold,
                            fontSize = 14.sp,
                            color = if (cancelled) colors.subtle else colors.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(1.dp))
                        Text(
                            item.paidAtLabel,
                            color = colors.subtle,
                            fontFamily = KidhackFontFamily,
                            fontWeight = FontSemibold,
                            fontSize = 12.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (cancelled) {
                            stringResource(R.string.child_pay_cancelled)
                        } else {
                            stringResource(R.string.child_pay_amount_spent, formatPoint(item.amount))
                        },
                        color = if (cancelled) colors.subtle else colors.text,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.sp,
                    )
                }
                if (index != state.history.lastIndex) HorizontalDivider(color = colors.line)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

/** 1234567 → "1,234,567" */
private fun formatPoint(value: Int): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()
