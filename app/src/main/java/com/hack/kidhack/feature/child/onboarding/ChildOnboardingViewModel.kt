package com.hack.kidhack.feature.child.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hack.kidhack.R
import com.hack.kidhack.background.ChildPlatformController
import com.hack.kidhack.core.permission.KidhackPermissions
import com.hack.kidhack.domain.repository.ChildOnboardingStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class OnboardingPermissionKind {
    RuntimeMulti,
    RuntimeSingle,
    SettingsUsage,
    SettingsOverlay,
    SettingsAccessibility,
    /** App details / location permission page (e.g. background location on API 30+). */
    SettingsAppDetails,
    MediaProjection,
}

/**
 * 목록을 나누는 세 덩이.
 *
 * <p>여덟 줄을 죽 늘어놓으면 벽처럼 보여 어디부터 눌러야 할지 안 잡힌다. 무엇보다
 * "설정 앱으로 나갔다 와야 한다"는 사정은 항목마다 반복할 설명이 아니라 <b>묶음의 성격</b>이다.
 * 묶음 제목에서 한 번만 말하면 각 줄에는 이름과 한 줄 설명만 남는다.
 */
enum class OnboardingGroup(@StringRes val titleRes: Int) {
    /** 시스템 팝업이 앱 위에 뜬다. 화면을 떠나지 않는다. */
    InApp(R.string.child_onboarding_group_inapp),

    /** 설정 앱으로 나갔다 돌아와야 한다. 한 번 켜면 유지된다. */
    Settings(R.string.child_onboarding_group_settings),

    /** 없어도 보호 기능은 돈다. */
    Optional(R.string.child_onboarding_group_optional),
}

data class OnboardingStep(
    val kind: OnboardingPermissionKind,
    val runtimePermission: String? = null,
    /** Required for child control — cannot skip until granted. */
    val required: Boolean = true,
    val group: OnboardingGroup,
    @DrawableRes val iconRes: Int,
    @StringRes val titleRes: Int,
    @StringRes val bodyRes: Int,
) {
    /** 오류 문구를 어느 줄 아래에 붙일지 가리는 이름표. */
    val key: String get() = kind.name + ":" + runtimePermission.orEmpty()
}

data class ChildOnboardingUiState(
    val items: List<OnboardingStep> = emptyList(),
    val error: String? = null,
    /**
     * [error] 가 어느 줄 이야기인지. null 이면 특정 줄이 아니라 화면 전체 이야기라
     * 하단 버튼 위에 붙는다.
     */
    val errorKey: String? = null,
    val finished: Boolean = false,
)

class ChildOnboardingViewModel(
    private val store: ChildOnboardingStore,
    private val platformController: ChildPlatformController,
    private val persistDone: Boolean = true,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ChildOnboardingUiState(items = buildItems()),
    )
    val state: StateFlow<ChildOnboardingUiState> = _state.asStateFlow()

    fun clearError() = _state.update { it.copy(error = null, errorKey = null) }

    fun resetFinished() = _state.update {
        it.copy(finished = false, error = null, errorKey = null)
    }

    fun denyOrMissingRequired(key: String? = null) {
        _state.update {
            it.copy(error = "권한이 필요해요. 「허용」을 다시 눌러 주세요.", errorKey = key)
        }
    }

    fun notifyNeedForegroundLocationFirst(key: String? = null) {
        _state.update {
            it.copy(error = "먼저 위 「위치」를 허용한 뒤에 항상 위치를 설정할 수 있어요.", errorKey = key)
        }
    }

    fun notifyPermissionBlocked(key: String? = null) {
        _state.update {
            it.copy(
                error = "시스템 팝업이 더 이상 안 떠요. 설정에서 권한을 허용한 뒤 돌아와 주세요.",
                errorKey = key,
            )
        }
    }

    fun finish() {
        viewModelScope.launch {
            if (persistDone) {
                store.markDone()
                platformController.reevaluate()
            }
            _state.update { it.copy(finished = true, error = null, errorKey = null) }
        }
    }

    companion object {
        fun buildItems(): List<OnboardingStep> = buildList {
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.RuntimeMulti,
                    required = true,
                    group = OnboardingGroup.InApp,
                    iconRes = R.drawable.ic_place,
                    titleRes = R.string.child_onboarding_location_title,
                    bodyRes = R.string.child_onboarding_location_body,
                ),
            )
            if (KidhackPermissions.backgroundLocation != null) {
                add(
                    OnboardingStep(
                        // API 30+: requestPermissions opens the app Location permission page
                        // (Allow all the time). API 29: system dialog.
                        kind = OnboardingPermissionKind.RuntimeSingle,
                        runtimePermission = KidhackPermissions.backgroundLocation,
                        required = true,
                        group = OnboardingGroup.InApp,
                        iconRes = R.drawable.ic_target,
                        titleRes = R.string.child_onboarding_bg_location_title,
                        bodyRes = R.string.child_onboarding_bg_location_body,
                    ),
                )
            }
            if (KidhackPermissions.notifications != null) {
                add(
                    OnboardingStep(
                        kind = OnboardingPermissionKind.RuntimeSingle,
                        runtimePermission = KidhackPermissions.notifications,
                        required = true,
                        group = OnboardingGroup.InApp,
                        iconRes = R.drawable.ic_bell,
                        titleRes = R.string.child_onboarding_notif_title,
                        bodyRes = R.string.child_onboarding_notif_body,
                    ),
                )
            }
            add(
                OnboardingStep(
                    // 설정 앱이 아니라 앱 위에 뜨는 시스템 동의창이라 InApp 묶음이다.
                    kind = OnboardingPermissionKind.MediaProjection,
                    required = true,
                    group = OnboardingGroup.InApp,
                    iconRes = R.drawable.ic_capture,
                    titleRes = R.string.child_onboarding_capture_title,
                    bodyRes = R.string.child_onboarding_capture_body,
                ),
            )
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.SettingsUsage,
                    required = true,
                    group = OnboardingGroup.Settings,
                    iconRes = R.drawable.ic_apps,
                    titleRes = R.string.child_onboarding_usage_title,
                    bodyRes = R.string.child_onboarding_usage_body,
                ),
            )
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.SettingsOverlay,
                    required = true,
                    group = OnboardingGroup.Settings,
                    iconRes = R.drawable.ic_warning,
                    titleRes = R.string.child_onboarding_overlay_title,
                    bodyRes = R.string.child_onboarding_overlay_body,
                ),
            )
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.SettingsAccessibility,
                    required = true,
                    group = OnboardingGroup.Settings,
                    iconRes = R.drawable.ic_shield,
                    titleRes = R.string.child_onboarding_accessibility_title,
                    bodyRes = R.string.child_onboarding_accessibility_body,
                ),
            )
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.RuntimeSingle,
                    runtimePermission = KidhackPermissions.camera,
                    required = false,
                    group = OnboardingGroup.Optional,
                    iconRes = R.drawable.ic_camera,
                    titleRes = R.string.child_onboarding_camera_title,
                    bodyRes = R.string.child_onboarding_camera_body,
                ),
            )
            add(
                OnboardingStep(
                    kind = OnboardingPermissionKind.RuntimeSingle,
                    runtimePermission = KidhackPermissions.microphone,
                    required = false,
                    group = OnboardingGroup.Optional,
                    iconRes = R.drawable.ic_mic,
                    titleRes = R.string.child_onboarding_mic_title,
                    bodyRes = R.string.child_onboarding_mic_body,
                ),
            )
        }
    }
}
