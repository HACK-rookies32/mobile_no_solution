package com.hack.kidhack

import com.hack.kidhack.background.accessibility.HarmfulBlockAccessibilityService.Companion.shouldBlock
import com.hack.kidhack.core.common.HarmfulCategories
import com.hack.kidhack.domain.model.BlocklistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 카테고리 토글이 실제로 페이지를 막는지 검증한다.
 *
 * 예전에는 카테고리당 키워드가 하나씩(`gambling.com` 등)뿐이라 화면 텍스트에 그 문자열이
 * 그대로 찍히지 않으면 아무것도 걸리지 않았다.
 */
class HarmfulBlockTest {

    private fun blacklistOf(vararg categoryIds: String): List<BlocklistItem> =
        categoryIds.flatMap { HarmfulCategories.keywords(it) }
            .map { BlocklistItem(id = it, pattern = it) }

    @Test
    fun `도박 카테고리는 한글·영문 도박 페이지를 모두 막는다`() {
        val black = blacklistOf(HarmfulCategories.GAMBLING)
        assertNotNull(shouldBlock("온라인 카지노 바카라 무료 머니", black))
        assertNotNull(shouldBlock("Best Online Casino and Sportsbook 2026", black))
        assertNotNull(shouldBlock("사설토토 먹튀 검증", black))
    }

    @Test
    fun `성인·폭력·마약 카테고리도 동작한다`() {
        // 붙여 쓴 도메인(pornhub)도 잡혀야 한다.
        assertNotNull(shouldBlock("PornHub - free videos", blacklistOf(HarmfulCategories.ADULT)))
        assertNotNull(shouldBlock("야동 사이트 모음 19금", blacklistOf(HarmfulCategories.ADULT)))
        assertNotNull(shouldBlock("bestgore beheading video", blacklistOf(HarmfulCategories.VIOLENCE)))
        assertNotNull(shouldBlock("고어물 모음", blacklistOf(HarmfulCategories.VIOLENCE)))
        assertNotNull(shouldBlock("buy cocaine online", blacklistOf(HarmfulCategories.DRUGS)))
        assertNotNull(shouldBlock("대마초 구매 후기", blacklistOf(HarmfulCategories.DRUGS)))
    }

    @Test
    fun `영단어 키워드는 단어 경계를 지켜 오탐하지 않는다`() {
        val drugs = blacklistOf(HarmfulCategories.DRUGS)
        // meth ⊂ method, lsd ⊂ "false-lsdx" 같은 문자열에 걸리면 안 된다.
        assertNull(shouldBlock("The scientific method and methodology", drugs))
        assertNull(shouldBlock("checksum a3xxx1f", blacklistOf(HarmfulCategories.ADULT)))
    }

    /**
     * 서버 BlockRuleResponse 는 차단 패턴 하나뿐이라 **허용 목록 예외가 없다.**
     * 예전에는 화이트리스트가 블랙리스트를 덮었는데, 그 개념이 사라졌으므로
     * 교육용 도메인이라도 차단 패턴에 걸리면 그대로 막힌다.
     */
    @Test
    fun `허용 목록 개념이 없어 차단 패턴에 걸리면 그대로 막는다`() {
        val items = blacklistOf(HarmfulCategories.GAMBLING) +
            BlocklistItem(id = "edu.ac.kr", pattern = "edu.ac.kr")
        assertNotNull(shouldBlock("카지노 중독 예방 교육 - edu.ac.kr", items))
    }

    @Test
    fun `적중한 키워드를 그대로 돌려준다`() {
        val items = listOf(BlocklistItem(id = "바카라", pattern = "바카라"))
        assertEquals("바카라", shouldBlock("실시간 바카라 배팅", items))
    }

    @Test
    fun `카테고리 묶음은 서로 겹치지 않는다`() {
        val seen = mutableSetOf<String>()
        HarmfulCategories.all.forEach { bundle ->
            bundle.keywords.forEach { keyword ->
                assertEquals("중복 키워드: $keyword", true, seen.add(keyword.lowercase()))
            }
        }
    }
}
