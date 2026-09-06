package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

@Composable
fun KidTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    enabled: Boolean = true,
    isError: Boolean = false,
    singleLine: Boolean = true,
    minLines: Int = 1,
    supportingText: String? = null,
) {
    val colors = LocalKidhackColors.current
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 12.sp,
            letterSpacing = 0.6.sp,
            color = colors.muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            enabled = enabled,
            isError = isError,
            placeholder = {
                Text(
                    text = placeholder,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 14.sp,
                    color = colors.muted,
                )
            },
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            singleLine = singleLine,
            minLines = minLines,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                disabledContainerColor = colors.surface,
                errorContainerColor = colors.surface,
                focusedBorderColor = colors.brand,
                unfocusedBorderColor = colors.border,
                cursorColor = colors.brand,
                focusedTextColor = colors.text,
                unfocusedTextColor = colors.text,
            ),
            supportingText = supportingText?.let {
                { Text(it, color = if (isError) colors.danger else colors.muted) }
            },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun KidTextFieldPreview() {
    KidhackTheme {
        KidTextField(
            value = "",
            onValueChange = {},
            label = "아이디",
            placeholder = "아이디를 입력하세요",
            modifier = Modifier.padding(16.dp),
        )
    }
}
