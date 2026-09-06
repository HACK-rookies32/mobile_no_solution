package com.hack.kidhack.core.common

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 오늘 0시 ~ 지금.
 *
 * `GET /api/monitoring/usage/{childId}` 는 `from`/`to` 가 없으면 **최근 1주일**을 합산한다.
 * 「오늘 스크린타임」을 보여 주는 화면이 범위를 넘기지 않으면 일주일치가 오늘 값으로 찍힌다.
 *
 * 서버는 오프셋 없는 `LocalDateTime` 을 받는다(서버 시간대 KST 가정).
 */
fun todayRange(): Pair<String, String> {
    val start = LocalDate.now().atStartOfDay()
    val now = LocalDateTime.now()
    return start.toString() to now.toString()
}

/**
 * 오늘을 포함한 최근 [days] 일. 0시부터 지금까지.
 *
 * <p>주간 보고서처럼 여러 날을 한꺼번에 볼 때 쓴다. 오늘을 끝에 두는 이유는 부모가
 * "요즘 어떤지"를 보기 때문이다 — 지난주 월요일부터가 아니라 오늘까지가 기준이다.
 */
fun recentDaysRange(days: Int): Pair<String, String> {
    val start = LocalDate.now().minusDays((days - 1).toLong()).atStartOfDay()
    val now = LocalDateTime.now()
    return start.toString() to now.toString()
}
