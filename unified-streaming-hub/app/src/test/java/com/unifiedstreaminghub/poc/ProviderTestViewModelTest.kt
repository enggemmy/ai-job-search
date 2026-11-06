package com.unifiedstreaminghub.poc

import com.unifiedstreaminghub.poc.data.UrlStore
import com.unifiedstreaminghub.poc.domain.DetectionStatus
import com.unifiedstreaminghub.poc.domain.InstallState
import com.unifiedstreaminghub.poc.domain.LaunchKind
import com.unifiedstreaminghub.poc.domain.LaunchOutcome
import com.unifiedstreaminghub.poc.domain.Provider
import com.unifiedstreaminghub.poc.domain.ProviderLauncher
import com.unifiedstreaminghub.poc.domain.StartResult
import com.unifiedstreaminghub.poc.ui.ProviderTestViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ProviderTestViewModelTest {

    private class MemoryStore(val data: MutableMap<Provider, String> = mutableMapOf()) : UrlStore {
        override fun get(provider: Provider) = data[provider].orEmpty()
        override fun set(provider: Provider, url: String) { data[provider] = url }
    }

    private val url = "https://title.example.org/watch/1"

    @Test
    fun `loads saved urls and persists edits`() {
        val store = MemoryStore(mutableMapOf(Provider.Netflix to url))
        val vm = ProviderTestViewModel(store)
        assertEquals(url, vm.state.value.rows.getValue(Provider.Netflix).url)
        assertEquals("", vm.state.value.rows.getValue(Provider.AppleTv).url)

        vm.setUrl(Provider.AppleTv, "https://x.example.org/a")
        assertEquals("https://x.example.org/a", store.get(Provider.AppleTv))
    }

    @Test
    fun `editing the url clears the stale detection result`() {
        val vm = ProviderTestViewModel(MemoryStore())
        val launcher = ProviderLauncher(FakeIntentGateway())
        vm.setUrl(Provider.Netflix, url)
        vm.detect(Provider.Netflix, launcher)
        assertEquals(DetectionStatus.NO_HANDLER, vm.state.value.rows.getValue(Provider.Netflix).detection?.status)

        vm.setUrl(Provider.Netflix, "$url?x=2")
        assertNull(vm.state.value.rows.getValue(Provider.Netflix).detection)
    }

    @Test
    fun `install state starts unknown and refresh fills it for every provider`() {
        val vm = ProviderTestViewModel(MemoryStore())
        Provider.entries.forEach {
            assertEquals(InstallState.UNKNOWN, vm.state.value.rows.getValue(it).install.state)
        }
        vm.refreshInstall(ProviderLauncher(FakeIntentGateway(install = FakeIntentGateway.INSTALLED)))
        Provider.entries.forEach {
            assertEquals(InstallState.INSTALLED, vm.state.value.rows.getValue(it).install.state)
        }
    }

    @Test
    fun `last title result is tracked per provider and ignores app launches`() {
        val vm = ProviderTestViewModel(MemoryStore())
        val gateway = FakeIntentGateway(
            install = FakeIntentGateway.INSTALLED,
            packageStart = StartResult.Started,
        )
        val launcher = ProviderLauncher(gateway)
        vm.setUrl(Provider.Netflix, url)

        assertNull(vm.state.value.lastTitleResult(Provider.Netflix))
        vm.open(Provider.Netflix, launcher)
        gateway.packageStart = StartResult.NoActivity
        vm.launchApp(Provider.Netflix, launcher) // app-home entry must not replace the title record

        val last = vm.state.value.lastTitleResult(Provider.Netflix)
        assertEquals(LaunchOutcome.APP_LAUNCH_REQUESTED, last?.outcome)
        assertEquals(LaunchKind.TITLE_URL, last?.kind)
        assertNull(vm.state.value.lastTitleResult(Provider.DisneyPlus))
        assertEquals(2, vm.state.value.log.size)
    }

    @Test
    fun `open appends newest-first log entries and clear empties it`() {
        val vm = ProviderTestViewModel(MemoryStore())
        val gateway = FakeIntentGateway(anyStart = StartResult.Started)
        var t = 0L
        val launcher = ProviderLauncher(gateway) { ++t }
        vm.setUrl(Provider.Netflix, url)
        vm.setUrl(Provider.DisneyPlus, url)

        vm.open(Provider.Netflix, launcher)
        vm.open(Provider.DisneyPlus, launcher)

        val log = vm.state.value.log
        assertEquals(listOf(Provider.DisneyPlus, Provider.Netflix), log.map { it.provider })
        assertEquals(LaunchOutcome.BROWSER_FALLBACK, log.first().outcome)

        vm.clearLog()
        assertEquals(0, vm.state.value.log.size)
        assertNotNull(vm.state.value.rows[Provider.Netflix])
    }
}
