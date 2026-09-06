package com.hack.kidhack.feature.child.mypage

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidLabeledSection
import com.hack.kidhack.core.designsystem.component.KidSectionCard
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.feature.child.components.ChildSubScaffold
import org.koin.androidx.compose.koinViewModel

/**
 * 마이페이지 › 프로필 수정.
 *
 * 저장에 성공하면 곧바로 마이페이지로 돌아간다. 마이페이지는 복귀할 때 다시 읽으므로
 * 바뀐 이름·이메일이 바로 반영된다.
 *
 * <p>주황 그라데이션 히어로 + 「←」 글자를 걷어내고 다른 서브 화면과 같은 껍데기를 쓴다.
 * 얼굴 자리의 😊 도 마이페이지와 같은 <b>이름 이니셜</b>로 바꿨다 — 이모지는 기기 폰트마다
 * 다르게 그려져 옆 화면과 다른 얼굴이 된다.
 *
 * <p>「취소」 버튼은 없앴다. 헤더의 ‹ 가 이미 같은 일을 하는데 버튼을 하나 더 두면 나가는
 * 길이 둘이 되고, 정작 눌러야 할 「저장」이 둘 중 하나로 보인다.
 */
@Composable
fun ChildProfileEditScreen(
    onBack: () -> Unit = {},
    viewModel: ChildProfileEditViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onBack()
    }

    ChildSubScaffold(
        title = stringResource(R.string.child_profile_title),
        onBack = onBack,
        bottomBar = {
            KidButton(
                text = stringResource(
                    if (state.saving) R.string.child_profile_saving else R.string.child_profile_save,
                ),
                onClick = viewModel::save,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.canSave,
            )
        },
    ) {
        KidSectionCard(contentPadding = PaddingValues(vertical = 20.dp, horizontal = 16.dp)) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.brandLight),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        state.name.trim().take(1).ifBlank { "?" },
                        color = colors.brandDark,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 26.sp,
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.child_profile_photo_note),
                    color = colors.subtle,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontSemibold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        KidLabeledSection(
            label = stringResource(R.string.child_mypage_section_info),
            contentPadding = PaddingValues(16.dp),
        ) {
            KidTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = stringResource(R.string.child_profile_name),
                placeholder = stringResource(R.string.child_profile_name_hint),
                enabled = !state.loading && !state.saving,
                isError = state.nameError != null,
                supportingText = state.nameError,
            )
            Spacer(Modifier.height(12.dp))
            KidTextField(
                value = state.phone,
                onValueChange = viewModel::onPhone,
                label = stringResource(R.string.child_profile_phone),
                placeholder = stringResource(R.string.child_profile_phone_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                enabled = !state.loading && !state.saving,
            )
            Spacer(Modifier.height(12.dp))
            KidTextField(
                value = state.email,
                onValueChange = viewModel::onEmail,
                label = stringResource(R.string.child_profile_email),
                placeholder = stringResource(R.string.child_profile_email_hint),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                enabled = !state.loading && !state.saving,
                isError = state.emailError != null,
                supportingText = state.emailError
                    ?: stringResource(R.string.child_profile_email_optional),
            )
        }
        Spacer(Modifier.height(4.dp))
    }
}
