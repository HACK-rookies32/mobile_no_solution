package com.hack.kidhack.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.sp

/**
 * Nunito (Figma) has no Hangul coverage.
 * Using the platform sans-serif so Korean glyphs resolve to Noto Sans CJK / One UI Sans
 * on typical Korean devices, with the Figma 900 / 700 / 600 weight ladder.
 */
val KidhackFontFamily: FontFamily = FontFamily.SansSerif

val FontBlack = FontWeight.W900
val FontBold = FontWeight.W700
val FontSemibold = FontWeight.W600

/**
 * 한글 본문 줄바꿈. 줄 길이를 고르게 맞춰 마지막 줄에 한두 글자만 남지 않게 한다.
 *
 * <p>어절이 갈라지는 것은 이것만으로 막지 못한다. 한글은 글자 사이 아무 데서나 줄을
 * 끊는 것이 표준 규칙이라 "모바일기기 / 를" 처럼 조사가 떨어져 나간다. 안드로이드의
 * 어절 단위 줄바꿈(`WordBreak.Phrase`)은 일본어에만 적용되므로, 한글은 [keepWordsWhole]
 * 로 어절 안쪽을 붙여서 함께 쓴다.
 */
val KidLineBreak = LineBreak(
    strategy = LineBreak.Strategy.Balanced,
    strictness = LineBreak.Strictness.Normal,
    wordBreak = LineBreak.WordBreak.Default,
)

/** 줄을 끊지 않게 붙이는 보이지 않는 글자(WORD JOINER). */
private const val WORD_JOINER = '⁠'

/**
 * 띄어쓰기에서만 줄이 바뀌도록 어절 안쪽을 붙인다.
 *
 * <p>낱말 가운데가 갈라지면 읽는 흐름이 끊기고 화면이 지저분해 보인다. 어절 안 글자
 * 사이마다 보이지 않는 [WORD_JOINER] 를 끼워 그 자리에서는 줄이 바뀌지 않게 한다.
 * 글자 자체는 그대로라 보이는 모습과 복사한 값은 달라지지 않는다.
 *
 * <p>한 어절이 화면 폭보다 길면 끊을 자리가 없어 넘치므로, 긴 URL 같은 값에는 쓰지 않는다.
 */
fun String.keepWordsWhole(): String =
    Regex("""\S+""").replace(this) { m -> m.value.toCharArray().joinToString(WORD_JOINER.toString()) }

val KidhackTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 32.sp,
        lineHeight = 40.sp,
        color = Text,
    ),
    displayMedium = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        color = Text,
    ),
    headlineLarge = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        color = Text,
    ),
    headlineMedium = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        color = Text,
    ),
    titleLarge = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        color = Text,
    ),
    titleMedium = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = Text,
    ),
    titleSmall = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Text,
    ),
    bodyLarge = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontSemibold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        color = Text,
    ),
    bodyMedium = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontSemibold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Text,
    ),
    bodySmall = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontSemibold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Muted,
    ),
    labelLarge = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        color = Text,
    ),
    labelMedium = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        color = Muted,
    ),
    labelSmall = TextStyle(
        fontFamily = KidhackFontFamily,
        fontWeight = FontSemibold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp,
        color = Muted,
    ),
)
