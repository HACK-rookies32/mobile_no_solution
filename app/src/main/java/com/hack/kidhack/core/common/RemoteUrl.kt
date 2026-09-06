package com.hack.kidhack.core.common

import com.hack.kidhack.BuildConfig

/**
 * 서버가 내려 주는 첨부 경로를 열 수 있는 절대 URL 로 바꾼다.
 *
 * 공지·문의 첨부는 `/api/files/notice/{filename}` 같은 **상대 경로**로 온다. 그대로
 * 이미지 로더나 브라우저에 넘기면 스킴이 없어 아무것도 열리지 않는다.
 *
 * origin 은 `BuildConfig.BASE_URL`(`http://host:8080/api/`) 에서 경로를 떼어 만든다.
 * 이미 `http`/`https` 로 시작하는 값은 그대로 둔다(서버가 S3 절대 URL 을 줄 수도 있다).
 */
object RemoteUrl {

    private val origin: String by lazy {
        val base = BuildConfig.BASE_URL
        val schemeEnd = base.indexOf("://")
        if (schemeEnd < 0) return@lazy base.trimEnd('/')
        val pathStart = base.indexOf('/', schemeEnd + 3)
        if (pathStart < 0) base.trimEnd('/') else base.substring(0, pathStart)
    }

    fun absolute(path: String): String {
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return trimmed
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) return trimmed
        return origin + if (trimmed.startsWith("/")) trimmed else "/$trimmed"
    }

    /** 확장자로만 이미지 여부를 본다. 서버가 파일 종류를 따로 알려주지 않는다. */
    fun isImage(path: String): Boolean {
        val name = path.substringAfterLast('/').substringBefore('?').lowercase()
        return IMAGE_SUFFIXES.any { name.endsWith(it) }
    }

    private val IMAGE_SUFFIXES = listOf(".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".heic")
}
