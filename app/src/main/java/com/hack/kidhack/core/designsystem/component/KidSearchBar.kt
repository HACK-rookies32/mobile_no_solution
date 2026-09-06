package com.hack.kidhack.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

/**
 * 알약 검색바.
 *
 * <p>전에는 라벨이 붙은 폼 입력칸([KidTextField]) 옆에 「검색」 버튼이 서 있었다. 라벨과
 * 버튼이 같은 말을 두 번 했고, 회원가입 폼에 쓰는 칸이라 검색창처럼 보이지 않았다.
 *
 * <p>알약 하나로 줄이고 실행은 키보드의 「검색」 키에 맡긴다. 버튼이 사라진 만큼 글자가
 * 들어갈 자리가 넓어진다.
 *
 * <p>공지사항과 게시판이 함께 쓴다. 한 앱 안에서 검색이 두 모양이면 두 기능처럼 보인다.
 *
 * @param onSearch 키보드 「검색」 키를 눌렀을 때. 글자마다 부르지 않는다 — 서버로 요청이
 *   나가는 검색이라 한 글자씩 칠 때마다 부르면 그만큼 왕복이 생긴다.
 */
@Composable
fun KidSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalKidhackColors.current
    val keyboard = LocalSoftwareKeyboardController.current
    // 캔버스가 회색(surface)이라 검색바도 회색이면 묻힌다 — 흰 알약에 얇은 테두리를 둘러
    // 캔버스 위로 또렷하게 띄운다. 돋보기만 브랜드색으로 「여기가 검색」임을 알린다.
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(21.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(21.dp))
            .padding(start = 13.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = colors.brand,
        )
        Spacer(Modifier.width(9.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            textStyle = TextStyle(
                fontFamily = KidhackFontFamily,
                fontSize = 13.5.sp,
                color = colors.text,
            ),
            cursorBrush = SolidColor(colors.brand),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(
                onSearch = {
                    keyboard?.hide()
                    onSearch()
                },
            ),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(hint, color = colors.subtle, fontSize = 13.5.sp)
                }
                inner()
            },
        )
        // 적은 말을 한 번에 지운다 — 한 글자씩 지우게 두면 검색을 물리는 데 열 번을 눌러야
        // 한다. 빈 칸일 때도 자리는 지킨다. 나타났다 사라지면 글자를 지울 때마다 칸이 흔들린다.
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .then(
                    if (value.isNotEmpty()) {
                        Modifier.clickable {
                            onValueChange("")
                            onSearch()
                        }
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (value.isNotEmpty()) {
                Box(
                    Modifier.size(18.dp).clip(CircleShape).background(colors.border),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_close),
                        contentDescription = stringResource(R.string.search_clear),
                        modifier = Modifier.size(10.dp),
                        tint = colors.card,
                    )
                }
            }
        }
    }
}
