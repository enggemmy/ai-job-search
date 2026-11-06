package com.unifiedstreaminghub.poc.data

import android.content.Context
import com.unifiedstreaminghub.poc.domain.Provider

/** Persists the tester-entered title URL per provider so it survives restarts. */
interface UrlStore {
    fun get(provider: Provider): String
    fun set(provider: Provider, url: String)
}

class SharedPrefsUrlStore(context: Context) : UrlStore {
    private val prefs = context.applicationContext
        .getSharedPreferences("provider_test_urls", Context.MODE_PRIVATE)

    override fun get(provider: Provider): String = prefs.getString(provider.name, "").orEmpty()

    override fun set(provider: Provider, url: String) {
        prefs.edit().putString(provider.name, url).apply()
    }
}
