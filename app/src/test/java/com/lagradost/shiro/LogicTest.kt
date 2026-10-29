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
