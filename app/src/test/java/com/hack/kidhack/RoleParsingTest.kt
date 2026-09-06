package com.hack.kidhack

import com.hack.kidhack.data.repository.toRoleOrNull
import com.hack.kidhack.domain.model.Role
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 자녀 계정이 부모로 로그인되던 원인 검증.
 *
 * 예전 코드는 `Role.valueOf(role)` 만 써서, 아래 표기들이 전부 예외로 떨어진 뒤
 * 조용히 PARENT 로 대체됐다.
 */
class RoleParsingTest {

    @Test
    fun `스프링 시큐리티 표기와 대소문자를 모두 받아들인다`() {
        assertEquals(Role.CHILD, "CHILD".toRoleOrNull())
        assertEquals(Role.CHILD, "child".toRoleOrNull())
        assertEquals(Role.CHILD, "ROLE_CHILD".toRoleOrNull())
        assertEquals(Role.CHILD, " role_child ".toRoleOrNull())
        assertEquals(Role.CHILD, "KID".toRoleOrNull())
        assertEquals(Role.PARENT, "PARENT".toRoleOrNull())
        assertEquals(Role.PARENT, "ROLE_PARENT".toRoleOrNull())
        assertEquals(Role.PARENT, "guardian".toRoleOrNull())
    }

    @Test
    fun `알 수 없으면 PARENT 로 넘겨짚지 않고 null 을 준다`() {
        assertNull(null.toRoleOrNull())
        assertNull("".toRoleOrNull())
        assertNull("   ".toRoleOrNull())
        assertNull("ROLE_".toRoleOrNull())
        assertNull("ADMIN".toRoleOrNull())
    }
}
