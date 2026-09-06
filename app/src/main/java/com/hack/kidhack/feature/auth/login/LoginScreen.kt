package com.hack.kidhack.feature.auth.login

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.Role
import com.hack.kidhack.domain.model.Session
import com.hack.kidhack.feature.auth.AuthScaffold
import com.hack.kidhack.feature.auth.AuthTabs
import org.koin.androidx.compose.koinViewModel

/** 탭 차례. 화면에 보이는 순서와 [Role] 을 잇는 유일한 자리다. */
private val LOGIN_ROLES = listOf(Role.PARENT, Role.CHILD)

@Composable
fun LoginScreen(
    onLoggedIn: (Session) -> Unit,
    onRegister: () -> Unit,
    onForgot: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current

    LaunchedEffect(state.session) {
        state.session?.let(onLoggedIn)
    }

    AuthScaffold(
        header = {
            Text(
                text = "ROOKIES",
                color = colors.brand,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 11.sp,
                letterSpacing = 1.5.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "안녕하세요",
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 26.sp,
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "우리 아이 안전, 루키즈가 지켜드려요",
                color = colors.muted,
                fontSize = 13.5.sp,
            )
            Spacer(Modifier.height(22.dp))
            AuthTabs(
                titles = listOf("부모님", "자녀"),
                selected = LOGIN_ROLES.indexOf(state.role).coerceAtLeast(0),
                onSelect = { viewModel.onRole(LOGIN_ROLES[it]) },
            )
        },
    ) {
        KidTextField(
            value = state.loginId,
            onValueChange = viewModel::onLoginId,
            label = "아이디",
            placeholder = "아이디를 입력하세요",
        )
        KidTextField(
            value = state.password,
            onValueChange = viewModel::onPassword,
            label = "비밀번호",
            placeholder = "비밀번호를 입력하세요",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        )
        if (state.error != null) {
            Text(state.error.orEmpty(), color = colors.danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(4.dp))
        KidButton(
            text = if (state.loading) "로그인 중..." else "로그인",
            onClick = viewModel::login,
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.loading,
        )
        // 전에는 「비밀번호를 잊으셨나요?」였다. 그 화면은 아이디 찾기도 하는데 글자가
        // 비밀번호만 말해서, 아이디를 잊은 사람은 갈 곳이 없다고 느꼈다.
        Text(
            text = "아이디 · 비밀번호 찾기",
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onForgot)
                .padding(vertical = 12.dp),
            color = colors.muted,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onRegister)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("계정이 없으신가요? ", color = colors.muted, fontSize = 13.sp)
            Text(
                text = "회원가입",
                color = colors.brand,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 13.sp,
            )
        }
    }
}
