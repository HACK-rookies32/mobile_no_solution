package com.hack.kidhack.feature.parent

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.hack.kidhack.R
import com.hack.kidhack.core.common.RemoteUrl
import com.hack.kidhack.core.designsystem.component.KidHeaderDivider
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidLineBreak
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.data.remote.userMessage
import com.hack.kidhack.domain.model.ChildProfile
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.SelectedChildRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class ChildProfileUiState(
    val child: ChildProfile? = null,
    val name: String = "",
    val alias: String = "",
    val birthDate: String = "",
    val profileImageUrl: String? = null,
    val showPhotoOptions: Boolean = false,
    /** 사진을 서버로 올리는 중. 그동안 저장을 막는다 — 올리기 전에 저장하면 옛 사진이 남는다. */
    val uploading: Boolean = false,
    val saved: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
) {
    /** 고친 것이 있는지. 없으면 저장 버튼을 꺼 둔다. */
    val dirty: Boolean
        get() = child != null &&
            (alias != child.alias.orEmpty() || profileImageUrl != child.profileImageUrl)
}

class ChildProfileViewModel(
    private val childRepository: ChildRepository,
    private val selectedChildRepository: SelectedChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ChildProfileUiState())
    val state: StateFlow<ChildProfileUiState> = _state.asStateFlow()

    init { load() }

    fun onAlias(v: String) = _state.update { it.copy(alias = v, saved = false) }
    
    fun togglePhotoOptions(show: Boolean) = _state.update { it.copy(showPhotoOptions = show) }

    /**
     * 고른 사진을 **서버에 올리고** 받은 경로를 담는다.
     *
     * <p>전에는 갤러리가 준 `content://` 주소를 그대로 넣었다. 그 주소는 이 기기에서만
     * 열리므로, 저장은 되지만 다른 기기에서도 재설치 뒤에도 사진이 뜨지 않았다.
     */
    fun onPhotoSelected(uri: Uri?) {
        val picked = uri?.toString() ?: run {
            _state.update { it.copy(showPhotoOptions = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(showPhotoOptions = false, uploading = true, error = null, saved = false) }
            runCatching { childRepository.uploadProfileImage(picked) }
                .onSuccess { url -> _state.update { it.copy(uploading = false, profileImageUrl = url) } }
                .onFailure { t ->
                    _state.update {
                        it.copy(uploading = false, error = t.userMessage("사진을 올리지 못했어요"))
                    }
                }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                val id = selectedChildRepository.resolveId(childRepository.children()) ?: return@runCatching null
                childRepository.child(id)
            }.onSuccess { child ->
                if (child != null) {
                    _state.update {
                        it.copy(
                            loading = false,
                            child = child,
                            name = child.name,
                            alias = child.alias.orEmpty(),
                            birthDate = child.birthDate.orEmpty(),
                            profileImageUrl = child.profileImageUrl
                        )
                    }
                } else {
                    _state.update { it.copy(loading = false) }
                }
            }.onFailure { t ->
                _state.update { it.copy(loading = false, error = t.message) }
            }
        }
    }

    fun save() {
        val child = _state.value.child ?: return
        viewModelScope.launch {
            runCatching {
                // 서버 ChildUpdateRequest 는 nickname·profileImageUrl 만 받는다.
                // 이름·생년월일을 같이 보내던 시절에는 서버가 조용히 버리는데도
                // 화면은 "저장되었습니다" 를 띄웠다.
                childRepository.updateChild(
                    childId = child.id,
                    nickname = _state.value.alias.trim().ifBlank { null },
                    profileImageUrl = _state.value.profileImageUrl,
                )
            }.onSuccess { updated ->
                _state.update {
                    it.copy(
                        child = updated,
                        name = updated.name,
                        alias = updated.alias.orEmpty(),
                        birthDate = updated.birthDate.orEmpty(),
                        profileImageUrl = updated.profileImageUrl,
                        saved = true,
                    )
                }
            }.onFailure { t ->
                _state.update { it.copy(error = t.message) }
            }
        }
    }
}

/**
 * 자녀 프로필 수정.
 *
 * <p>서버가 받는 것은 <b>별명과 사진 둘뿐</b>이다(`ChildUpdateRequest{nickname, profileImageUrl}`).
 * 전에는 넷을 똑같은 입력 상자로 그려 놓고 이름·생년월일만 회색으로 꺼 뒀는데, 그러면
 * 「지금은 못 고치지만 언젠가 고칠 수 있는 값」처럼 보인다. 바꿀 수 있는 것과 가입할 때
 * 정해진 것을 묶음으로 갈랐다.
 */
@Composable
fun ParentChildProfileScreen(
    onBack: () -> Unit = {},
    viewModel: ChildProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val context = LocalContext.current
    val child = state.child

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.onPhotoSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Text("\u2039", color = colors.text, fontSize = 26.sp)
            }
            Text(
                stringResource(R.string.parent_profile_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
            Spacer(Modifier.weight(1f))
            // 고친 것이 없거나 사진을 올리는 중이면 눌리지 않는다.
            val canSave = state.dirty && !state.uploading && !state.loading
            Text(
                text = stringResource(R.string.parent_profile_save),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .then(if (canSave) Modifier.clickable(onClick = viewModel::save) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 9.dp),
                color = if (canSave) colors.brandDark else colors.border,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.sp,
            )
        }
        KidHeaderDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            if (state.loading && child == null || state.error != null) {
                Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                    ParentLoadingOrError(state.loading && child == null, state.error, viewModel::load)
                }
            }
            if (child == null) return@Column

            // 사진은 카메라 배지로 바로 고른다. 「사진 변경」 버튼과 이모지 시트를 걷었다.
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier.size(88.dp).clip(CircleShape).background(colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        val photo = state.profileImageUrl
                        when {
                            state.uploading -> CircularProgressIndicator(
                                Modifier.size(22.dp),
                                color = colors.brand,
                                strokeWidth = 2.dp,
                            )
                            photo.isNullOrBlank() -> Icon(
                                painter = painterResource(R.drawable.ic_person),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = colors.border,
                            )
                            else -> AsyncImage(
                                model = ImageRequest.Builder(context)
                                    .data(RemoteUrl.absolute(photo))
                                    .crossfade(true)
                                    .build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(colors.card)
                            .border(1.dp, colors.border, CircleShape)
                            .clickable(enabled = !state.uploading) { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_camera),
                            contentDescription = stringResource(R.string.parent_profile_change_photo),
                            modifier = Modifier.size(15.dp),
                            tint = colors.muted,
                        )
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = child.name,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 19.sp,
                    letterSpacing = (-0.5).sp,
                    color = colors.text,
                )
                if (state.alias.isNotBlank() && state.alias != child.name) {
                    Spacer(Modifier.height(4.dp))
                    Text(state.alias, color = colors.subtle, fontSize = 12.5.sp, fontWeight = FontSemibold)
                }
            }

            FieldGroup(stringResource(R.string.parent_profile_editable))
            AliasField(value = state.alias, onValueChange = viewModel::onAlias)
            Hint(stringResource(R.string.parent_profile_alias_hint))

            FieldGroup(stringResource(R.string.parent_profile_fixed))
            ReadOnlyRow(stringResource(R.string.parent_profile_name), child.name)
            ReadOnlyRow(
                stringResource(R.string.parent_profile_birth),
                state.birthDate.ifBlank { stringResource(R.string.parent_profile_unknown) },
            )
            Hint(stringResource(R.string.parent_profile_fixed_hint))

            if (state.saved) {
                Text(
                    text = stringResource(R.string.parent_profile_saved),
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                    color = colors.safe,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 12.5.sp,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

/** 묶음 이름. 바꿀 수 있는 값과 정해진 값을 이 이름으로 가른다. */
@Composable
private fun FieldGroup(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 22.dp, top = 26.dp, bottom = 4.dp),
        color = LocalKidhackColors.current.subtle,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

/** 라벨 아래 설명. 왜 못 바꾸는지, 이 값이 어디에 쓰이는지를 여기서 말한다. */
@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp),
        color = LocalKidhackColors.current.subtle,
        fontSize = 11.5.sp,
        lineHeight = 19.sp,
        style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
    )
}

/** 별명 한 줄. 서버가 고쳐 주는 유일한 글자값이라 테두리 없이 밑줄만 둔다. */
@Composable
private fun AliasField(value: String, onValueChange: (String) -> Unit) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.parent_profile_alias),
            modifier = Modifier.width(82.dp),
            color = colors.subtle,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).padding(vertical = 15.dp),
            textStyle = TextStyle(
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 14.5.sp,
                color = colors.text,
            ),
            cursorBrush = SolidColor(colors.brand),
            singleLine = true,
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        stringResource(R.string.parent_profile_alias_placeholder),
                        color = colors.border,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 14.5.sp,
                    )
                }
                inner()
            },
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
}

/**
 * 못 바꾸는 값 한 줄.
 *
 * <p>꺼진 입력 상자 대신 자물쇠를 단다. 상자로 두면 눌러 보고 나서야 못 고치는 줄 안다.
 */
@Composable
private fun ReadOnlyRow(label: String, value: String) {
    val colors = LocalKidhackColors.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            modifier = Modifier.width(82.dp),
            color = colors.subtle,
            fontFamily = KidhackFontFamily,
            fontWeight = FontBold,
            fontSize = 13.sp,
        )
        Text(
            text = value,
            modifier = Modifier.weight(1f),
            color = colors.subtle,
            fontFamily = KidhackFontFamily,
            fontWeight = FontSemibold,
            fontSize = 14.5.sp,
        )
        Icon(
            painter = painterResource(R.drawable.ic_lock),
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = colors.border,
        )
    }
    HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
}
