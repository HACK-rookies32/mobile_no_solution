package com.hack.kidhack.core.common

/**
 * 「차단 카테고리」 4종을 **클라이언트 사전 키워드 묶음**으로 푼 것 (docs/spec-gaps.md GAP-04).
 *
 * 서버 블랙리스트는 평탄한 문자열 배열이라 카테고리 개념이 없다. 그래서 부모가 토글을 켜면
 * 이 묶음의 키워드들을 BLACK 으로 밀어 넣고, 자녀 기기의 접근성 서비스는 그 키워드로 판정한다.
 *
 * 묶음이 카테고리당 한 단어뿐이면(예전의 `gambling.com` 하나) 실제 사이트는 거의 걸리지 않는다.
 * 국내 자녀가 실제로 마주치는 한글 표현과 해외 사이트의 영어 표현을 함께 넣는다.
 */
object HarmfulCategories {

    const val ADULT = "adult"
    const val GAMBLING = "gambling"
    const val VIOLENCE = "violence"
    const val DRUGS = "drugs"

    data class Bundle(val id: String, val keywords: List<String>)

    val all: List<Bundle> = listOf(
        Bundle(
            ADULT,
            listOf(
                "성인용품", "음란", "야동", "19금", "포르노", "성인방송",
                "porn", "xvideos", "xhamster", "onlyfans", "hentai", "xxx", "nsfw",
            ),
        ),
        Bundle(
            GAMBLING,
            listOf(
                "도박", "카지노", "바카라", "사설토토", "슬롯머신", "홀덤", "먹튀", "배팅",
                "casino", "gambling", "betting", "poker", "roulette", "baccarat",
                "sportsbook", "jackpot", "bet365", "gambling.com",
            ),
        ),
        Bundle(
            VIOLENCE,
            listOf(
                "폭력물", "잔인한", "혐오표현", "고어물", "참수", "학대영상", "자살방법",
                "gore", "beheading", "snuff", "liveleak", "bestgore",
            ),
        ),
        Bundle(
            DRUGS,
            listOf(
                "마약", "대마초", "필로폰", "히로뽕", "코카인", "엑스터시", "케타민", "졸피뎀",
                "cocaine", "heroin", "cannabis", "marijuana", "meth", "lsd", "ecstasy",
            ),
        ),
    )

    fun bundle(id: String): Bundle? = all.firstOrNull { it.id == id }

    fun keywords(id: String): List<String> = bundle(id)?.keywords.orEmpty()

    /** 카테고리 묶음에 속한 키워드인지. 부모가 직접 추가한 키워드와 구분할 때 쓴다. */
    fun categoryOf(keyword: String): String? =
        all.firstOrNull { bundle -> bundle.keywords.any { it.equals(keyword, ignoreCase = true) } }?.id

    val allKeywords: List<String> = all.flatMap { it.keywords }

    /**
     * 다른 영단어 안에 그대로 들어 있는 키워드들.
     *
     * `meth` 는 `method` 에, `xxx` 는 해시 문자열에, `bet` 은 `alphabet` 에 걸린다.
     * 이런 키워드만 단어 경계를 요구한다. 나머지(`porn`, `casino` …)는 `pornhub` 처럼
     * 붙여 쓴 도메인도 잡아야 하므로 부분 일치를 유지한다.
     */
    val wordBoundaryKeywords: Set<String> = setOf(
        "meth", "lsd", "xxx", "gore", "bet", "ice", "snuff",
    )
}

/**
 * 화면 텍스트 ↔ 차단 키워드 매칭.
 *
 * 한글·도메인 조각은 부분 일치로 충분하지만, 영단어까지 부분 일치로 보면 오탐이 쏟아진다
 * (`meth` 가 `method` 를, `bet` 이 `alphabet` 을, `xxx` 가 임의의 해시를 때린다).
 * 그래서 순수 영숫자 키워드만 단어 경계를 요구한다.
 */
object HarmfulKeywordMatcher {

    private val cache = HashMap<String, Regex>()

    fun matches(text: String, keyword: String): Boolean {
        val kw = keyword.trim()
        if (kw.isEmpty()) return false
        if (kw.lowercase() !in HarmfulCategories.wordBoundaryKeywords) {
            return text.contains(kw, ignoreCase = true)
        }
        return regexFor(kw).containsMatchIn(text)
    }

    /** 텍스트에 걸린 첫 키워드. 없으면 null. */
    fun firstMatch(text: String, keywords: List<String>): String? =
        keywords.firstOrNull { matches(text, it) }

    private fun regexFor(keyword: String): Regex = synchronized(cache) {
        cache.getOrPut(keyword) {
            Regex(
                "(?<![a-z0-9])" + Regex.escape(keyword) + "(?![a-z0-9])",
                RegexOption.IGNORE_CASE,
            )
        }
    }
}
