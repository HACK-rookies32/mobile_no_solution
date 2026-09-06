package com.hack.kidhack.core.common

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/** Digits only (max 11). */
fun digitsOnlyPhone(raw: String): String = raw.filter { it.isDigit() }.take(11)

/** Keeps digits only (max 11) and formats as 010-1234-5678. */
fun formatPhoneNumber(raw: String): String {
    val digits = digitsOnlyPhone(raw)
    return when {
        digits.length <= 3 -> digits
        digits.length <= 7 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
        else -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
    }
}

/**
 * 상태에는 숫자만 두고, 화면에는 하이픈을 그린다.
 * 값 문자열에 `-`를 넣으면 커서가 밀리므로 가입/찾기 입력은 이 transformation을 쓴다.
 */
class PhoneNumberVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = digitsOnlyPhone(text.text)
        val formatted = formatPhoneNumber(digits)
        return TransformedText(
            AnnotatedString(formatted),
            PhoneOffsetMapping(digitCount = digits.length, formattedLength = formatted.length),
        )
    }
}

private class PhoneOffsetMapping(
    private val digitCount: Int,
    private val formattedLength: Int,
) : OffsetMapping {
    override fun originalToTransformed(offset: Int): Int {
        val o = offset.coerceIn(0, digitCount)
        val mapped = when {
            o <= 3 -> o
            o <= 7 -> o + 1
            else -> o + 2
        }
        return mapped.coerceIn(0, formattedLength)
    }

    override fun transformedToOriginal(offset: Int): Int {
        val t = offset.coerceIn(0, formattedLength)
        val mapped = when {
            t <= 3 -> t
            t <= 8 -> t - 1
            else -> t - 2
        }
        return mapped.coerceIn(0, digitCount)
    }
}
