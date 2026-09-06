package com.hack.kidhack.feature.child

/**
 * 사용 시간 표기.
 *
 * <p>전에는 「1h 42m」과 「3시간」이 한 화면에 같이 나왔다. 아이가 보는 화면에서 단위가
 * 두 나라 말로 섞이면 두 값을 비교하는 데 눈이 한 번 더 걸린다. 전부 우리말로 맞춘다.
 */
internal fun formatDurationSec(sec: Int): String {
    val hours = sec / 3600
    val minutes = (sec % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "${hours}시간 ${minutes}분"
        hours > 0 -> "${hours}시간"
        else -> "${minutes}분"
    }
}

internal fun formatDurationMin(minutes: Int): String = formatDurationSec(minutes * 60)

internal fun remainingLabel(usedMin: Int, limitMin: Int): String {
    val left = (limitMin - usedMin).coerceAtLeast(0)
    val hours = left / 60
    val minutes = left % 60
    return if (hours > 0) "${hours}시간 ${minutes}분" else "${minutes}분"
}

/**
 * 서버가 주는 시각 문자열을 화면용으로 다듬는다.
 *
 * ISO-8601(`2026-08-25T14:22:00`)이면 `08-25 14:22` 로, 이미 사람이 읽을 수 있는
 * 문자열(`오늘 14:22`)이면 그대로 둔다. 서버 포맷이 확정되지 않아 둘 다 받는다.
 */
internal fun formatTimeLabel(raw: String): String {
    if (!raw.contains('T')) return raw
    val date = raw.substringBefore('T').takeLast(5)
    val time = raw.substringAfter('T').take(5)
    return if (time.isBlank()) date else "$date $time"
}
