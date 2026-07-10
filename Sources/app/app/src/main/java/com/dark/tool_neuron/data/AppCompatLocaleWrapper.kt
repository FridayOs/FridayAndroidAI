package com.dark.tool_neuron.data

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.dark.tool_neuron.model.AppLanguage
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale as JavaLocale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppCompatLocaleWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val languageController: LanguageController,
) {
    fun currentTag(): String = languageController.selected.value.uiTag

    fun wrap(base: Context): Context {
        val tag = currentTag()
        if (tag.isBlank()) return base
        val locale = JavaLocale.forLanguageTag(tag)
        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocale(locale)
            config.setLocales(android.os.LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        return base.createConfigurationContext(config)
    }

    fun currentLocale(): JavaLocale {
        val tag = currentTag()
        return if (tag.isBlank()) JavaLocale.getDefault()
        else JavaLocale.forLanguageTag(tag)
    }

    companion object {
        fun tagForAppLanguage(value: AppLanguage): String = value.uiTag
    }
}
