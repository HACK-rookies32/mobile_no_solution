package com.hack.kidhack.feature.efin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

/**
 * 거래 승인 다이얼로그 — 앱 루트에 **딱 하나만** 띄운다([TxAuthCoordinator] 참고).
 *
 * 송금·회수·충전 어디서 시작하든 이 다이얼로그가 결제 비밀번호 + OTP 를 받는다. OTP 코드는 알림으로 온다.
 */
@Composable
fun TxAuthHost(coordinator: TxAuthCoordinator) {
    val pending by coordinator.pending.collectAsStateWithLifecycle()
    val req = pending ?: return
    val colors = LocalKidhackColors.current

    val title = if (req.targetName != null) "${req.targetName}님 · ₩${format(req.amount)} 송금"
    else "₩${format(req.amount)} 충전"

    AlertDialog(
        onDismissRequest = coordinator::cancel,
        title = { Text("거래 인증", fontWeight = FontBold) },
        text = {
            Column {
                Text(title, color = colors.text, fontFamily = KidhackFontFamily, fontSize = 13.sp)
                Spacer(Modifier.height(2.dp))
                Text(
                    "인증번호를 ${req.phone} 로 전송했습니다. 알림으로 온 6자리를 입력하세요.",
                    color = colors.muted, fontFamily = KidhackFontFamily, fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                Field(req.paymentPw, coordinator::onPaymentPw, "결제 비밀번호 4자리", mask = true)
                Spacer(Modifier.height(8.dp))
                Field(req.otp, coordinator::onOtp, "인증번호 6자리", mask = false)
                if (req.error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(req.error, color = colors.danger, fontFamily = KidhackFontFamily,
                        fontWeight = FontBold, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = coordinator::submit,
                enabled = req.paymentPw.length == 4 && req.otp.length == 6 && !req.working,
            ) { Text("확인", color = colors.brand, fontWeight = FontBold) }
        },
        dismissButton = {
            TextButton(onClick = coordinator::cancel) { Text("취소", color = colors.muted) }
        },
    )
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, placeholder: String, mask: Boolean) {
    val colors = LocalKidhackColors.current
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surface)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = colors.muted, fontFamily = KidhackFontFamily, fontSize = 15.sp)
        }
        BasicTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = colors.text, fontFamily = KidhackFontFamily, fontWeight = FontBold, fontSize = 15.sp),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (mask) KeyboardType.NumberPassword else KeyboardType.Number,
            ),
            visualTransformation = if (mask) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
            singleLine = true,
            cursorBrush = SolidColor(colors.brand),
        )
    }
}

private fun format(amount: Int): String = "%,d".format(amount)
