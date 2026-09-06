package com.hack.kidhack.ui.theme

import androidx.compose.runtime.Composable
import com.hack.kidhack.core.designsystem.theme.KidhackTheme as CoreKidhackTheme

@Deprecated("Use core.designsystem.theme.KidhackTheme", ReplaceWith("KidhackTheme(content)", "com.hack.kidhack.core.designsystem.theme.KidhackTheme"))
@Composable
fun KidhackTheme(content: @Composable () -> Unit) {
    CoreKidhackTheme(content)
}
