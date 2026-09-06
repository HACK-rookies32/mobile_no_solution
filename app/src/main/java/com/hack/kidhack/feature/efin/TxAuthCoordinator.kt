package com.hack.kidhack.feature.efin

import com.hack.kidhack.data.crypto.TxSigner
import com.hack.kidhack.domain.repository.EfinRepository
import com.hack.kidhack.domain.repository.TxSecurity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 거래 승인 공용 코디네이터 — 돈이 움직이는 모든 화면(송금·회수·충전)이 하나의 승인 플로우를 공유한다.
 *
 * <p>화면마다 다이얼로그를 붙이던 구조(마이페이지 버그의 근본 원인)를 없앤다. 승인 UI 는 앱 루트에
 * [TxAuthHost] 하나만 띄우고, 각 화면 VM 은 [authorize] 를 호출해 결과(계좌비번+OTP 묶음)를 받는다.
 *
 * <p>흐름: authorize() → 서버에 OTP 발급(문자 발송 mock) + 로컬 알림 → 루트 다이얼로그가 계좌비번·OTP
 * 입력 → submit() 이 계좌비번·OTP 를 사전 확인(틀리면 거부 — 데모도 실서버처럼) → 서명·nonce 를 붙여
 * [TxSecurity] 반환. 최종 검증은 서버 관문(EfinTxGuard)이 한 번 더 한다.
 */
class TxAuthCoordinator(
    private val efinRepository: EfinRepository,
    private val otpNotifier: OtpNotifier,
) {
    /** 승인 대기 상태. null 이면 다이얼로그를 띄우지 않는다. */
    data class Pending(
        val amount: Int,
        /** 대상 이름(송금=자녀명, 충전=null). 서명 소유주 검증에 쓰이고 안내에 표시된다. */
        val targetName: String?,
        /** 서명 refId(송금=childId, 충전=0). */
        val refId: Long,
        /** OTP 발송 대상(마스킹). */
        val phone: String,
        val paymentPw: String = "",
        val otp: String = "",
        val working: Boolean = false,
        val error: String? = null,
    )

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private val _pending = MutableStateFlow<Pending?>(null)
    val pending: StateFlow<Pending?> = _pending.asStateFlow()
    private var deferred: CompletableDeferred<TxSecurity?>? = null

    /**
     * 거래 승인을 요청한다. OTP 를 발급·알림으로 띄우고 다이얼로그를 연 뒤, 사용자가 확인하면
     * 계좌비번+OTP+서명+nonce 를 담은 [TxSecurity] 를, 취소하면 null 을 돌려준다.
     */
    suspend fun authorize(amount: Int, targetName: String?, refId: Long): TxSecurity? {
        val issued = efinRepository.issueOtp()
        otpNotifier.notifyCode(issued.code)
        val d = CompletableDeferred<TxSecurity?>()
        deferred = d
        _pending.value = Pending(amount = amount, targetName = targetName, refId = refId, phone = issued.phone)
        return d.await()
    }

    fun onPaymentPw(v: String) = _pending.update {
        it?.copy(paymentPw = v.filter { c -> c.isDigit() }.take(4), error = null)
    }

    fun onOtp(v: String) = _pending.update {
        it?.copy(otp = v.filter { c -> c.isDigit() }.take(6), error = null)
    }

    /** 확인 — 계좌비번·OTP 를 사전 확인하고, 통과하면 서명·nonce 를 붙여 승인 결과를 완료한다. */
    fun submit() {
        val req = _pending.value ?: return
        if (req.working) return
        _pending.update { it?.copy(working = true, error = null) }
        scope.launch {
            val okPw = runCatching { efinRepository.verifyPaymentPassword(req.paymentPw) }.getOrDefault(false)
            val okOtp = runCatching { efinRepository.verifyOtp(req.otp) }.getOrDefault(false)
            if (!okPw) {
                _pending.update { it?.copy(working = false, error = "결제 비밀번호가 올바르지 않습니다.") }
                return@launch
            }
            if (!okOtp) {
                _pending.update { it?.copy(working = false, error = "인증번호가 올바르지 않거나 만료되었습니다.") }
                return@launch
            }
            val nonce = TxSigner.newNonce()
            val sec = TxSecurity(
                otp = req.otp,
                paymentPassword = req.paymentPw,
                nonce = nonce,
                signature = TxSigner.sign(req.amount.toLong(), req.refId, nonce),
                accountHolder = req.targetName,
            )
            _pending.value = null
            deferred?.complete(sec)
            deferred = null
        }
    }

    fun cancel() {
        _pending.value = null
        deferred?.complete(null)
        deferred = null
    }
}
