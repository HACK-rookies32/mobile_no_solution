package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

@Composable
fun PinCodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    length: Int = 6,
) {
    val colors = LocalKidhackColors.current
    val digits = value.filter { it.isDigit() }.take(length)
    BasicTextField(
        value = digits,
        onValueChange = { onValueChange(it.filter { ch -> ch.isDigit() }.take(length)) },
        modifier = modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            ) {
                repeat(length) { index ->
                    val char = digits.getOrNull(index)?.toString().orEmpty()
                    val focused = index == digits.length
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .border(
                                width = 2.dp,
                                color = if (char.isNotEmpty() || focused) colors.brand else colors.border,
                                shape = RoundedCornerShape(12.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = char,
                            style = TextStyle(
                                fontFamily = KidhackFontFamily,
                                fontWeight = FontBlack,
                                fontSize = 20.sp,
                                color = colors.brand,
                                textAlign = TextAlign.Center,
                            ),
                        )
                    }
                }
            }
        },
    )
}

@Composable
fun StepProgressBar(
    totalSteps: Int,
    currentStep: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.White.copy(alpha = 0.3f),
) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(totalSteps) { index ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(if (index <= currentStep) activeColor else inactiveColor),
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun PinCodeInputPreview() {
    KidhackTheme {
        Column(Modifier.padding(16.dp)) {
            PinCodeInput(value = "382", onValueChange = {})
            Spacer(Modifier.height(16.dp))
            StepProgressBar(totalSteps = 3, currentStep = 1)
        }
    }
}
