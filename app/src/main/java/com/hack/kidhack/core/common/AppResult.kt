package com.hack.kidhack.core.common

import kotlinx.serialization.json.Json

object KidhackJson {
    val instance: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
    }
}

sealed class AppResult<out T> {
    data class Ok<T>(val value: T) : AppResult<T>()
    data class Err(val throwable: Throwable) : AppResult<Nothing>()
}

inline fun <T> runCatchingResult(block: () -> T): AppResult<T> =
    try {
        AppResult.Ok(block())
    } catch (t: Throwable) {
        AppResult.Err(t)
    }
