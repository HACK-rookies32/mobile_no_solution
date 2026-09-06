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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
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
import com.hack.kidhack.core.designsystem.component.KidButton
import com.hack.kidhack.core.designsystem.component.KidTextField
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.FontSemibold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors
import com.hack.kidhack.domain.model.User
import com.hack.kidhack.domain.repository.ChildRepository
import com.hack.kidhack.domain.repository.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

data class EditProfileUiState(
    val user: User? = null,
    val nickname: String = "",
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val profileImageUrl: String? = null,
    val showPhotoOptions: Boolean = false,
    val uploadingPhoto: Boolean = false,
    val saved: Boolean = false,
    val loading: Boolean = true,
    val error: String? = null,
) {
    /** 로드된 값과 하나라도 다르면 저장 버튼이 열린다. */
    val dirty: Boolean
        get() {
            val u = user ?: return false
            return nickname.trim() != u.nickname.orEmpty() ||
                phone.trim() != u.phone ||
                email.trim() != u.email.orEmpty() ||
                profileImageUrl != u.profileImageUrl
        }
}

class EditProfileViewModel(
    private val userRepository: UserRepository,
    private val childRepository: ChildRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(EditProfileUiState())
    val state: StateFlow<EditProfileUiState> = _state.asStateFlow()

    init { load() }

    fun onNickname(v: String) = _state.update { it.copy(nickname = v, saved = false) }
    fun onPhone(v: String) = _state.update { it.copy(phone = v, saved = false) }
    fun onEmail(v: String) = _state.update { it.copy(email = v, saved = false) }

    fun togglePhotoOptions(show: Boolean) = _state.update { it.copy(showPhotoOptions = show) }

    fun onPhotoSelected(uri: Uri?) {
        if (uri == null) {
            _state.update { it.copy(showPhotoOptions = false) }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(showPhotoOptions = false, uploadingPhoto = true, error = null, saved = false) }
            // 자녀 프로필과 같은 공용 업로드(files/profile). 반환된 URL 을 저장에 실어 보낸다.
            runCatching { childRepository.uploadProfileImage(uri.toString()) }
                .onSuccess { url -> _state.update { it.copy(profileImageUrl = url, uploadingPhoto = false) } }
                .onFailure { t -> _state.update { it.copy(uploadingPhoto = false, error = t.message) } }
        }
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { userRepository.me() }
                .onSuccess { user ->
                    _state.update {
                        it.copy(
                            loading = false,
                            user = user,
                            nickname = user.nickname.orEmpty(),
                            name = user.name,
                            phone = user.phone,
                            email = user.email.orEmpty(),
                            profileImageUrl = user.profileImageUrl,
                        )
                    }
                }
                .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
        }
    }

    fun save() {
        val s = _state.value
        viewModelScope.launch {
            runCatching {
                userRepository.updateMe(
                    nickname = s.nickname.trim().ifBlank { null },
                    phoneNumber = s.phone.trim(),
                    email = s.email.trim().ifBlank { null },
                    address = null,
                    profileImageUrl = s.profileImageUrl,
                )
            }.onSuccess { user ->
                _state.update {
                    it.copy(
                        user = user,
                        nickname = user.nickname.orEmpty(),
                        name = user.name,
                        phone = user.phone,
                        email = user.email.orEmpty(),
                        profileImageUrl = user.profileImageUrl,
                        saved = true,
                    )
                }
            }.onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentEditProfileScreen(
    onBack: () -> Unit = {},
    viewModel: EditProfileViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val colors = LocalKidhackColors.current
    val user = state.user

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        viewModel.onPhotoSelected(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.card)
            .statusBarsPadding(),
    ) {
        // 헤더 — ‹ 내 정보 수정
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) { Text("‹", color = colors.text, fontSize = 26.sp) }
            Text(
                stringResource(R.string.parent_editprofile_title),
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 18.sp,
                color = colors.text,
            )
        }

        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if ((state.loading && user == null) || state.error != null) {
                Box(Modifier.padding(horizontal = 22.dp, vertical = 12.dp)) {
                    ParentLoadingOrError(state.loading && user == null, state.error, viewModel::load)
                }
            }
            if (user == null) return@Column

            // 프로필 — 원형 아바타 + 카메라 배지
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(contentAlignment = Alignment.BottomEnd) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(colors.surface)
                            .clickable { viewModel.togglePhotoOptions(true) },
                        contentAlignment = Alignment.Center,
                    ) {
                        val photo = state.profileImageUrl
                        if (photo.isNullOrBlank()) {
                            Icon(
                                painter = painterResource(R.drawable.ic_person),
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = colors.border,
                            )
                        } else {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(RemoteUrl.absolute(photo)).crossfade(true).build(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        if (state.uploadingPhoto) {
                            Box(
                                Modifier.fillMaxSize().background(colors.text.copy(alpha = 0.35f)),
                                contentAlignment = Alignment.Center,
                            ) { CircularProgressIndicator(Modifier.size(22.dp), color = androidx.compose.ui.graphics.Color.White, strokeWidth = 2.dp) }
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(colors.brand)
                            .border(3.dp, colors.card, CircleShape)
                            .clickable { viewModel.togglePhotoOptions(true) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_camera),
                            contentDescription = stringResource(R.string.parent_editprofile_change_photo),
                            modifier = Modifier.size(15.dp),
                            tint = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    state.nickname.ifBlank { user.name },
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBlack,
                    fontSize = 19.sp,
                    letterSpacing = (-0.5).sp,
                    color = colors.text,
                )
                Text(user.loginId, color = colors.muted, fontSize = 13.sp)
            }

            // 바꿀 수 있어요 — 편집 필드
            EditGroupLabel(stringResource(R.string.parent_editprofile_editable))
            Column(Modifier.padding(horizontal = 20.dp)) {
                KidTextField(
                    state.nickname,
                    viewModel::onNickname,
                    stringResource(R.string.parent_editprofile_nickname),
                    placeholder = stringResource(R.string.parent_editprofile_nickname_placeholder),
                    supportingText = stringResource(R.string.parent_editprofile_nickname_hint),
                )
                Spacer(Modifier.height(4.dp))
                KidTextField(
                    state.phone,
                    viewModel::onPhone,
                    stringResource(R.string.parent_editprofile_phone),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                )
                Spacer(Modifier.height(4.dp))
                KidTextField(
                    state.email,
                    viewModel::onEmail,
                    stringResource(R.string.parent_editprofile_email),
                    placeholder = stringResource(R.string.parent_editprofile_email_placeholder),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                )
            }

            // 가입 정보 — 읽기 전용
            EditGroupLabel(stringResource(R.string.parent_editprofile_signup))
            LockedRow(stringResource(R.string.parent_editprofile_loginid), user.loginId, last = false)
            LockedRow(stringResource(R.string.parent_editprofile_name), user.name, last = true)

            Spacer(Modifier.height(16.dp))
        }

        // 저장 — 하단 고정, 바뀐 게 있을 때만 활성
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.card)
                .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 16.dp)
                .navigationBarsPadding(),
        ) {
            HorizontalDivider(color = colors.line)
            if (state.saved) {
                Spacer(Modifier.height(10.dp))
                Text(
                    stringResource(R.string.parent_editprofile_saved),
                    color = colors.safe,
                    fontSize = 12.sp,
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                )
            }
            Spacer(Modifier.height(12.dp))
            KidButton(
                stringResource(R.string.parent_editprofile_save),
                viewModel::save,
                Modifier.fillMaxWidth(),
                enabled = state.dirty && !state.uploadingPhoto,
            )
        }
    }

    if (state.showPhotoOptions) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.togglePhotoOptions(false) },
            sheetState = rememberModalBottomSheetState(),
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
                Text(
                    stringResource(R.string.parent_editprofile_change_photo),
                    modifier = Modifier.padding(20.dp),
                    fontFamily = KidhackFontFamily,
                    fontWeight = FontBold,
                    fontSize = 16.sp,
                    color = colors.text,
                )
                HorizontalDivider(color = colors.line)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { galleryLauncher.launch("image/*") }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(colors.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_image),
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = colors.muted,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Text(
                        stringResource(R.string.parent_editprofile_pick_gallery),
                        color = colors.text,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontSemibold,
                        fontSize = 15.sp,
                    )
                }
            }
        }
    }
}

/** 작은 회색 묶음 이름. 다른 설정 화면과 같은 모양. */
@Composable
private fun EditGroupLabel(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 4.dp),
        color = LocalKidhackColors.current.subtle,
        fontFamily = KidhackFontFamily,
        fontWeight = FontBold,
        fontSize = 12.sp,
    )
}

/** 읽기 전용 가입 정보 한 줄. 오른쪽 자물쇠로 못 바꾸는 값임을 알린다. */
@Composable
private fun LockedRow(label: String, value: String, last: Boolean) {
    val colors = LocalKidhackColors.current
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.width(70.dp),
                color = colors.subtle,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBold,
                fontSize = 12.5.sp,
            )
            Text(
                value,
                modifier = Modifier.weight(1f),
                color = colors.text,
                fontFamily = KidhackFontFamily,
                fontWeight = FontSemibold,
                fontSize = 14.sp,
            )
            Icon(
                painter = painterResource(R.drawable.ic_lock),
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = colors.border,
            )
        }
        if (!last) HorizontalDivider(modifier = Modifier.padding(horizontal = 22.dp), color = colors.line)
    }
}
