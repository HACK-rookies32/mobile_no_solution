package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.hack.kidhack.core.designsystem.theme.KidhackTheme
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

@Composable
fun KidCard(
    modifier: Modifier = Modifier,
    containerColor: androidx.compose.ui.graphics.Color? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = LocalKidhackColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = containerColor ?: colors.card,
        border = BorderStroke(1.dp, colors.border),
        shadowElevation = 1.dp,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Preview(showBackground = true)
@Composable
private fun KidCardPreview() {
    KidhackTheme {
        KidCard(modifier = Modifier.padding(16.dp)) {
            Text("김민준")
        }
    }
}
