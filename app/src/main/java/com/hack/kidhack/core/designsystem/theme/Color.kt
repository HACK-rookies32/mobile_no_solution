package com.hack.kidhack.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Brand = Color(0xFFF58220)
val BrandDark = Color(0xFFD96D12)
val BrandLight = Color(0xFFFEF1E6)
val Safe = Color(0xFF16A34A)
val Warn = Color(0xFFF59E0B)
val Danger = Color(0xFFDC2626)
val Info = Color(0xFF2563EB)
/**
 * 카드가 얹히는 캔버스.
 *
 * <p>전에는 #F7F8FA 였는데 흰 카드(#FFFFFF)와 밝기가 거의 같아, 카드가 그림자 1.5dp 하나로만
 * 겨우 떠 있었다. 한 단 내려서 카드 경계가 그림자 없이도 읽히게 한다.
 */
val Surface = Color(0xFFF2F4F7)
val Card = Color(0xFFFFFFFF)
val Border = Color(0xFFE5E7EB)
val Muted = Color(0xFF6B7280)

/**
 * 본문보다 한 단 뒤로 물러난 회색.
 *
 * <p>[Muted] 는 본문 글씨와 밝기가 가까워, 작성자·시간 같은 곁가지에 쓰면 제목과
 * 나란히 읽힌다. 곁가지는 이 색으로 눕힌다.
 */
val Subtle = Color(0xFF9AA0A8)

/**
 * 목록을 나누는 실선.
 *
 * <p>[Border] 는 카드 테두리용이라 목록에 줄줄이 그으면 표처럼 보인다. 줄 사이는
 * 있는지 없는지 겨우 보이는 정도면 된다.
 */
val Line = Color(0xFFF1F2F4)
val Text = Color(0xFF1F2937)

@Immutable
data class KidhackColors(
    val brand: Color,
    val brandDark: Color,
    val brandLight: Color,
    val safe: Color,
    val warn: Color,
    val danger: Color,
    val info: Color,
    val surface: Color,
    val card: Color,
    val border: Color,
    val muted: Color,
    val subtle: Color,
    val line: Color,
    val text: Color,
) {
    companion object {
        val Light = KidhackColors(
            brand = Brand,
            brandDark = BrandDark,
            brandLight = BrandLight,
            safe = Safe,
            warn = Warn,
            danger = Danger,
            info = Info,
            surface = Surface,
            card = Card,
            border = Border,
            muted = Muted,
            subtle = Subtle,
            line = Line,
            text = Text,
        )
    }
}

val LocalKidhackColors = staticCompositionLocalOf { KidhackColors.Light }
