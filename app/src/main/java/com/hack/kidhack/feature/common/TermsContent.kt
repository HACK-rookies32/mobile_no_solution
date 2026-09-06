package com.hack.kidhack.feature.common

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import com.hack.kidhack.core.designsystem.theme.FontBlack
import com.hack.kidhack.core.designsystem.theme.KidLineBreak
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hack.kidhack.R
import com.hack.kidhack.core.designsystem.theme.FontBold
import com.hack.kidhack.core.designsystem.theme.KidhackFontFamily
import com.hack.kidhack.core.designsystem.theme.LocalKidhackColors

/**
 * 이용약관 한 조(條).
 *
 * 정적 법무 문구라 서버에서 받아오지 않는다. 개정 시 이 파일의 [TERMS_ARTICLES] 만 고치면 된다.
 * 본문을 strings.xml 이 아니라 여기에 둔 것은 `NoticeScreen` 의 공지 본문과 같은 방식이다.
 *
 * 부모용([com.hack.kidhack.feature.parent.ParentTermsScreen])과
 * 자녀용([com.hack.kidhack.feature.child.mypage.ChildTermsScreen])이 같은 본문을 써야 하므로
 * 스캐폴드 없이 [TermsBody] 만 공유한다. 화면 껍데기는 역할별 디자인이 달라 각자 둔다.
 */
private data class TermsArticle(
    val title: String,
    val paragraphs: List<String> = emptyList(),
    /** 제5조처럼 표로 된 조항. 모바일 폭에서 3열은 좁아 항목별 블록으로 편다. */
    val table: List<TermsTableRow> = emptyList(),
)

private data class TermsTableRow(
    val category: String,
    val items: String,
    val purpose: String,
)

private val TERMS_ARTICLES = listOf(
    TermsArticle(
        title = "제1조 (목적)",
        paragraphs = listOf(
            "이 약관은 루키즈(이하 \"회사\")가 제공하는 자녀 안전관리 서비스(이하 \"서비스\")의 이용과 관련하여 회사와 이용자 간의 권리, 의무 및 책임사항을 규정함을 목적으로 합니다.",
        ),
    ),
    TermsArticle(
        title = "제2조 (정의)",
        paragraphs = listOf(
            "1. \"이용자\"란 이 약관에 따라 서비스를 이용하는 부모(보호자) 및 자녀 회원을 말합니다.",
            "2. \"부모 회원\"이란 자녀 계정을 관리·감독할 목적으로 서비스에 가입한 자를 말합니다.",
            "3. \"자녀 회원\"이란 부모 회원과 페어링되어 서비스를 이용하는 미성년 이용자를 말합니다.",
            "4. \"페어링\"이란 부모 회원이 발급한 코드를 통해 부모-자녀 계정을 연결하는 절차를 말합니다.",
        ),
    ),
    TermsArticle(
        title = "제3조 (약관의 효력 및 변경)",
        paragraphs = listOf(
            "1. 회사는 이 약관의 내용을 이용자가 쉽게 확인할 수 있도록 서비스 초기 화면에 게시합니다.",
            "2. 회사는 관련 법령을 위배하지 않는 범위에서 약관을 개정할 수 있으며, 개정 시 적용일자 및 개정사유를 명시하여 사전 공지합니다.",
        ),
    ),
    TermsArticle(
        title = "제4조 (회원가입 및 계정 관리)",
        paragraphs = listOf(
            "1. 이용자는 실명, 생년월일, 휴대폰번호 등 회사가 정한 정보를 제공하여 가입을 신청합니다.",
            "2. 자녀 회원은 부모 회원이 발급한 페어링 코드(유효시간 5분)를 입력하여 부모 계정과 연결됩니다.",
            "3. 자녀 회원은 부모 회원의 요청에 따라 강제 로그아웃될 수 있으며, 자녀 회원 스스로는 로그아웃할 수 없습니다.",
            "4. 부모 회원은 페어링을 해제할 수 있으며, 이 경우 관련 자녀 계정 정보는 일정 기간 후 완전 삭제됩니다.",
        ),
    ),
    TermsArticle(
        title = "제5조 (수집하는 정보 및 이용 목적)",
        paragraphs = listOf("서비스는 다음 정보를 수집·처리합니다."),
        table = listOf(
            TermsTableRow(
                category = "위치정보",
                items = "GPS 좌표, 지오펜스(등록 장소), 이동(진입/이탈/도착) 이력",
                purpose = "자녀 위치 확인, 안전구역 알림",
            ),
            TermsTableRow(
                category = "기기·앱 정보",
                items = "설치 앱 목록, 앱별 사용시간, 전원/GPS 상태",
                purpose = "스마트폰 사용 관리, 트래킹 불가 알림",
            ),
            TermsTableRow(
                category = "화면 정보",
                items = "화면 캡처 이미지(자동/수동)",
                purpose = "유해 콘텐츠 노출 확인",
            ),
            TermsTableRow(
                category = "유해차단 정보",
                items = "접속 시도 URL/앱 정보",
                purpose = "유해사이트·앱 차단",
            ),
            TermsTableRow(
                category = "결제정보",
                items = "카드정보(암호화 저장), 지갑 거래내역, QR결제 내역",
                purpose = "용돈 충전·이체·소액결제 서비스 제공",
            ),
            TermsTableRow(
                category = "커뮤니케이션",
                items = "채팅 메시지, 커뮤니티 게시글/댓글, 1:1 문의 내용",
                purpose = "부모-자녀 소통, 커뮤니티 운영, 고객 문의 처리",
            ),
        ),
    ),
    TermsArticle(
        title = "제6조 (미성년자 보호)",
        paragraphs = listOf(
            "1. 자녀 회원의 위치, 화면, 사용내역 등은 보호자인 부모 회원의 관리 목적으로만 제공됩니다.",
            "2. 회사는 관계 법령이 정하는 바에 따라 만 14세 미만 아동의 개인정보 수집 시 법정대리인의 동의를 확인합니다.",
            "3. 자녀 회원의 페어링 해제 및 계정 삭제는 부모 회원의 요청에 따라 처리되며, 별도의 자녀 본인 동의 절차를 거치지 않을 수 있습니다.",
        ),
    ),
    TermsArticle(
        title = "제7조 (결제 서비스 이용)",
        paragraphs = listOf(
            "1. 부모 회원은 지갑 충전을 통해 자녀 회원에게 용돈을 이체할 수 있습니다.",
            "2. 충전은 준비-승인 2단계로 진행되며, 결제 수단 등록 시 카드정보는 암호화되어 전송·저장됩니다.",
            "3. 자녀 회원은 매장에서 QR코드를 통해 소액결제를 이용할 수 있으며, 부모 회원은 결제 내역을 조회하고 필요 시 취소·환불을 요청할 수 있습니다.",
            "4. 이용자는 본인 명의가 아닌 결제수단을 등록하거나, 타인의 지갑·결제 정보에 부정하게 접근해서는 안 됩니다.",
        ),
    ),
    TermsArticle(
        title = "제8조 (이용자의 의무)",
        paragraphs = listOf(
            "1. 이용자는 가입 시 정확한 정보를 제공해야 하며, 계정 정보를 제3자에게 양도·대여할 수 없습니다.",
            "2. 이용자는 서비스를 이용하여 타인의 정보를 무단으로 수집·이용하거나, 시스템에 부정하게 접근해서는 안 됩니다.",
            "3. 커뮤니티 게시판 이용 시 타인을 비방하거나 허위 사실을 유포하는 게시물을 작성해서는 안 되며, 회사는 신고된 게시물에 대해 삭제 등의 조치를 취할 수 있습니다.",
        ),
    ),
    TermsArticle(
        title = "제9조 (서비스의 제공 및 변경)",
        paragraphs = listOf(
            "1. 회사는 위치추적, 화면캡처, 유해차단, 사용시간 관리, 결제, 커뮤니티 등의 기능을 제공합니다.",
            "2. 회사는 서비스의 안정적 제공을 위해 기능의 일부를 변경하거나 중단할 수 있으며, 이 경우 사전에 공지합니다.",
        ),
    ),
    TermsArticle(
        title = "제10조 (개인정보의 보호)",
        paragraphs = listOf(
            "1. 회사는 관계 법령이 정하는 바에 따라 이용자의 개인정보를 보호하기 위해 노력하며, 자세한 사항은 별도의 개인정보처리방침에 따릅니다.",
            "2. 위치정보, 화면 캡처 등 민감한 정보는 목적 달성에 필요한 범위 내에서만 보관하며, 보관 기간 경과 후 파기합니다.",
        ),
    ),
    TermsArticle(
        title = "제11조 (면책조항)",
        paragraphs = listOf(
            "1. 회사는 천재지변, 통신장애 등 회사의 귀책사유 없는 사유로 서비스를 제공할 수 없는 경우 책임을 지지 않습니다.",
            "2. 회사는 이용자 상호간 또는 이용자와 제3자 간에 서비스를 매개로 발생한 분쟁에 대해 개입할 의무가 없으며, 이로 인한 손해를 배상할 책임이 없습니다.",
            "3. 위치정보 및 사용내역의 정확성은 기기 환경(GPS 신호, 네트워크 상태 등)에 따라 달라질 수 있습니다.",
        ),
    ),
    TermsArticle(
        title = "제12조 (분쟁해결)",
        paragraphs = listOf(
            "1. 서비스 이용과 관련하여 분쟁이 발생한 경우 회사와 이용자는 이를 원만히 해결하기 위해 노력합니다.",
            "2. 협의가 이루어지지 않을 경우 관련 법령 및 상관례에 따릅니다.",
        ),
    ),
)

/**
 * 약관 본문 전체(제1조 ~ 부칙)를 카드 목록으로 그린다.
 *
 * 호출부의 스캐폴드 안에 그대로 펼쳐지도록 [ColumnScope] 확장으로 둔다.
 */
/** 조 이름만. 위쪽 이동 칩이 쓴다. */
val TermsArticleTitles: List<String> = TERMS_ARTICLES.map { it.title }

/**
 * 약관 본문.
 *
 * <p>조마다 카드를 씌우던 것을 걷었다. 약관은 <b>끝까지 읽는 글</b>이라, 상자 열두 개로
 * 잘라 두면 어디까지 읽었는지 놓친다. 조 사이는 구분선 하나면 된다.
 *
 * @param onArticleTop 조가 놓인 세로 위치. 위쪽 이동 칩이 그 자리로 스크롤하는 데 쓴다.
 *   자녀 화면처럼 칩이 없으면 넘기지 않아도 된다.
 */
@Composable
fun ColumnScope.TermsBody(onArticleTop: (index: Int, y: Int) -> Unit = { _, _ -> }) {
    val colors = LocalKidhackColors.current

    TERMS_ARTICLES.forEachIndexed { index, article ->
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { onArticleTop(index, it.positionInParent().y.toInt()) }
                .padding(top = if (index == 0) 4.dp else 22.dp),
        ) {
            Text(
                article.title,
                fontFamily = KidhackFontFamily,
                fontWeight = FontBlack,
                fontSize = 15.sp,
                letterSpacing = (-0.35).sp,
                color = colors.text,
            )
            article.paragraphs.forEach { paragraph ->
                Spacer(Modifier.height(10.dp))
                Text(
                    paragraph,
                    fontSize = 13.sp,
                    color = colors.muted,
                    lineHeight = 24.sp,
                    style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
                )
            }
            article.table.forEachIndexed { rowIndex, row ->
                Spacer(Modifier.height(if (rowIndex == 0) 14.dp else 10.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surface)
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        row.category,
                        fontFamily = KidhackFontFamily,
                        fontWeight = FontBold,
                        fontSize = 12.5.sp,
                        color = colors.text,
                    )
                    Spacer(Modifier.height(6.dp))
                    TermsTableField(label = "수집 항목", value = row.items)
                    TermsTableField(label = "이용 목적", value = row.purpose)
                }
            }
        }
        if (index != TERMS_ARTICLES.lastIndex) {
            Spacer(Modifier.height(22.dp))
            HorizontalDivider(color = colors.line)
        }
    }

    Spacer(Modifier.height(22.dp))
    HorizontalDivider(color = colors.line)
    Spacer(Modifier.height(22.dp))
    Text(
        stringResource(R.string.terms_appendix),
        fontFamily = KidhackFontFamily,
        fontWeight = FontBlack,
        fontSize = 15.sp,
        letterSpacing = (-0.35).sp,
        color = colors.text,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        stringResource(R.string.terms_effective),
        fontSize = 13.sp,
        color = colors.muted,
        lineHeight = 24.sp,
        style = LocalTextStyle.current.copy(lineBreak = KidLineBreak),
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun TermsTableField(label: String, value: String) {
    val colors = LocalKidhackColors.current
    Text(
        "$label · $value",
        fontSize = 12.sp,
        color = colors.subtle,
        lineHeight = 19.sp,
    )
}
