package com.hack.kidhack.data.remote.interceptor

import com.hack.kidhack.domain.repository.SessionRepository
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(
    private val sessionRepository: SessionRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val path = original.url.encodedPath
        val skipAuth = path.contains("/auth/login") ||
            path.contains("/auth/signup") ||
            path.contains("/auth/token/refresh") ||
            path.contains("/auth/find-id") ||
            path.contains("/auth/reset-password")
        if (skipAuth) return chain.proceed(original)
        val token = sessionRepository.accessToken()?.takeIf { it.isNotBlank() }
            ?: return chain.proceed(original)
        val authed = original.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
        return chain.proceed(authed)
    }
}
