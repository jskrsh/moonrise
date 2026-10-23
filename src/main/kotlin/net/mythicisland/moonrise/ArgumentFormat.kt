package net.mythicisland.moonrise

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.MessageFormat
import java.text.NumberFormat
import java.time.DateTimeException
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoField
import java.time.temporal.TemporalAccessor
import java.util.Locale

internal sealed interface ArgumentFormat {

    fun format(value: Any, locale: Locale): String?

    /** `<coins>`, formats numbers and dates for the locale. */
    data object Default : ArgumentFormat {
        override fun format(value: Any, locale: Locale): String? = when (value) {
            is Number -> NumberFormat.getInstance(locale).format(value)
            is TemporalAccessor -> localized(value)?.withLocale(locale)?.format(value)
            else -> null
        }

        private fun localized(value: TemporalAccessor): DateTimeFormatter? {
            val date = value.isSupported(ChronoField.EPOCH_DAY)
            val time = value.isSupported(ChronoField.NANO_OF_DAY)
            return when {
                date && time -> DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
                date -> DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
                time -> DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)
                else -> null
            }
        }
    }

    /** `<coins:'#,##0.00'>` or `<date:'dd.MM.yyyy'>`. */
    data class Pattern(private val pattern: String) : ArgumentFormat {

        init {
            val valid = runCatching { DecimalFormat(pattern) }.isSuccess || runCatching { DateTimeFormatter.ofPattern(pattern) }.isSuccess
            require(valid) { "Invalid format pattern '$pattern'" }
        }

        override fun format(value: Any, locale: Locale): String? = try {
            when (value) {
                is Number -> DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale)).format(value)
                is TemporalAccessor -> DateTimeFormatter.ofPattern(pattern, locale).format(value)
                else -> null
            }
        } catch (exception: IllegalArgumentException) {
            null
        } catch (exception: DateTimeException) {
            null
        }
    }

    /** `<coins:choice:'0#no coins|1#one coin|1<{0} coins'>`. */
    data class Choice(private val pattern: String) : ArgumentFormat {

        init {
            MessageFormat(template())
        }

        override fun format(value: Any, locale: Locale): String? =
            if (value is Number) MessageFormat(template(), locale).format(arrayOf(value)) else null

        private fun template(): String = "{0,choice,$pattern}"
    }

    companion object {
        private const val CHOICE = "choice"

        fun isFormat(arguments: List<String>): Boolean =
            arguments.size <= 1 || (arguments.size == 2 && arguments[0].lowercase(Locale.ROOT) == CHOICE)

        fun of(arguments: List<String>): ArgumentFormat? = when {
            arguments.isEmpty() -> Default
            arguments.size == 1 -> Pattern(arguments[0])
            isFormat(arguments) -> Choice(arguments[1])
            else -> null
        }
    }
}
