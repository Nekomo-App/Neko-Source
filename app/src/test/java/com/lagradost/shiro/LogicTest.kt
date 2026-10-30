package com.lagradost.shiro

import com.google.common.truth.Truth.assertThat
import com.lagradost.shiro.utils.InAppUpdater
import com.lagradost.shiro.utils.SourceHealth
import com.lagradost.shiro.utils.home.HomeFilters
import org.junit.Test

class LogicTest {
    @Test
    fun versionsCompareNumerically() {
        val v = { s: String -> InAppUpdater.parseVersion(s)!! }
        assertThat(InAppUpdater.compareVersions(v("2.10.0"), v("2.9.9"))).isGreaterThan(0)
        assertThat(InAppUpdater.compareVersions(v("2.8.1"), v("2.8.1"))).isEqualTo(0)
        assertThat(InAppUpdater.compareVersions(v("2.8"), v("2.8.1"))).isLessThan(0)
        assertThat(InAppUpdater.parseVersion("v2.8.1-beta")).containsExactly(2, 8, 1).inOrder()
        assertThat(InAppUpdater.parseVersion("no version here")).isNull()
    }

    @Test
    fun homeBlocklistHidesSazaeSan() {
        assertThat(HomeFilters.isBlocked("Sazae-san")).isTrue()
        assertThat(HomeFilters.isBlocked(null, "SAZAE SAN")).isTrue()
        assertThat(HomeFilters.isBlocked("サザエさん")).isTrue()
        assertThat(HomeFilters.isBlocked("Sousou no Frieren", null)).isFalse()
    }

    @Test
    fun sourceHealthNeedsTwoFailuresInARow() {
        val key = "cs:Test"
        SourceHealth.ok(key)
        SourceHealth.fail(key)
        assertThat(SourceHealth.isFailing(key)).isFalse()
        SourceHealth.fail(key)
        assertThat(SourceHealth.isFailing(key)).isTrue()
        assertThat(SourceHealth.label("Test", key)).contains("not responding")
        SourceHealth.ok(key)
        assertThat(SourceHealth.isFailing(key)).isFalse()
    }
}

class ApiHealthTest {
    @org.junit.Test
    fun rateLimitPausesAndResets() {
        val id = "test-api"
        com.lagradost.shiro.utils.meta.ApiHealth.reset()
        com.google.common.truth.Truth.assertThat(com.lagradost.shiro.utils.meta.ApiHealth.blocked(id)).isFalse()
        com.lagradost.shiro.utils.meta.ApiHealth.rateLimited(id, 60)
        com.google.common.truth.Truth.assertThat(com.lagradost.shiro.utils.meta.ApiHealth.blocked(id)).isTrue()
        com.google.common.truth.Truth.assertThat(com.lagradost.shiro.utils.meta.ApiHealth.status(id)).contains("Paused")
        com.lagradost.shiro.utils.meta.ApiHealth.reset()
        com.google.common.truth.Truth.assertThat(com.lagradost.shiro.utils.meta.ApiHealth.status(id)).isEqualTo("Available")
    }

    @org.junit.Test
    fun threeFailuresInARowPause() {
        val id = "test-api-2"
        com.lagradost.shiro.utils.meta.ApiHealth.reset()
        repeat(2) { com.lagradost.shiro.utils.meta.ApiHealth.failure(id) }
        com.google.common.truth.Truth.assertThat(com.lagradost.shiro.utils.meta.ApiHealth.blocked(id)).isFalse()
        com.lagradost.shiro.utils.meta.ApiHealth.failure(id)
        com.google.common.truth.Truth.assertThat(com.lagradost.shiro.utils.meta.ApiHealth.blocked(id)).isTrue()
        com.lagradost.shiro.utils.meta.ApiHealth.success(id)
    }
}

class NekomoRepoTest {
    @org.junit.Test
    fun nicknameAndUrlsResolve() {
        val r = com.lagradost.shiro.utils.repo.NekomoRepo
        com.google.common.truth.Truth.assertThat(r.resolve("nekomo")).isEqualTo(r.OFFICIAL_URL)
        com.google.common.truth.Truth.assertThat(r.resolve("  NEKOMO ")).isEqualTo(r.OFFICIAL_URL)
        com.google.common.truth.Truth.assertThat(r.resolve("https://example.com/index.json")).isEqualTo("https://example.com/index.json")
        com.google.common.truth.Truth.assertThat(r.resolve("some-other-name")).isNull()
        com.google.common.truth.Truth.assertThat(r.resolve("ftp://x")).isNull()
    }
}
