package com.hack.kidhack.core.common

/**
 * 한국어 조사 자동 선택.
 *
 * 문자열 리소스에 `%1$s이` 처럼 조사를 박아 두면 「엄마이 지금…」 같은 문장이 나온다.
 * 보호자 이름은 서버에서 오는 값이라 받침이 있을지 없을지 미리 알 수 없으므로
 * 마지막 글자의 종성을 보고 코드에서 붙인다.
 */
object KoreanParticles {

    /** 이/가 */
    fun subject(word: String): String = word + if (hasFinalConsonant(word)) "이" else "가"

    /** 은/는 */
    fun topic(word: String): String = word + if (hasFinalConsonant(word)) "은" else "는"

    /**
     * 야/아 — 이름을 부를 때.
     *
     * 「지호야」/「민준아」. 받침이 있으면 「아」다.
     */
    fun vocative(word: String): String = word + if (hasFinalConsonant(word)) "아" else "야"

    /** 을/를 */
    fun objective(word: String): String = word + if (hasFinalConsonant(word)) "을" else "를"

    /**
     * 마지막 글자에 받침이 있는지.
     *
     * 한글 음절은 0xAC00 부터 28개 종성 단위로 배열돼 있어서, 오프셋을 28로 나눈
     * 나머지가 0 이면 받침이 없다. 한글이 아니면(영문·숫자) 받침 없음으로 본다.
     */
    private fun hasFinalConsonant(word: String): Boolean {
        val last = word.trim().lastOrNull() ?: return false
        if (last !in HANGUL_SYLLABLE_RANGE) return false
        return (last.code - HANGUL_BASE) % JONGSUNG_COUNT != 0
    }

    private const val HANGUL_BASE = 0xAC00
    private const val JONGSUNG_COUNT = 28
    private val HANGUL_SYLLABLE_RANGE = '가'..'힣'
}
