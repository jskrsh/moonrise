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

        private val numbers = object : LocaleFormats<NumberFormat>() {
            override fun create(locale: Locale): NumberFormat = NumberFormat.getInstance(locale)
        }
        private val dateTime = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        private val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)
        private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.MEDIUM)

        override fun format(value: Any, locale: Locale): String? = when (value) {
            is Number -> numbers.get(locale).format(value)
            is TemporalAccessor -> localized(value)?.withLocale(locale)?.format(value)
            else -> null
        }

        private fun localized(value: TemporalAccessor): DateTimeFormatter? {
            val hasDate = value.isSupported(ChronoField.EPOCH_DAY)
            val hasTime = value.isSupported(ChronoField.NANO_OF_DAY)
            return when {
                hasDate && hasTime -> dateTime
                hasDate -> date
                hasTime -> time
                else -> null
            }
        }
    }

    /** `<coins:'#,##0.00'>` or `<date:'dd.MM.yyyy'>`. */
    data class Pattern(private val pattern: String) : ArgumentFormat {

        private val numbers = object : LocaleFormats<DecimalFormat>() {
            override fun create(locale: Locale): DecimalFormat = DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale))
        }
        private val isNumber = runCatching { DecimalFormat(pattern) }.isSuccess
        private val date = runCatching { DateTimeFormatter.ofPattern(pattern) }.getOrNull()

        init {
            require(isNumber || date != null) { "Invalid format pattern '$pattern'" }
        }

        override fun format(value: Any, locale: Locale): String? = try {
            when (value) {
                is Number -> if (isNumber) numbers.get(locale).format(value) else null
                is TemporalAccessor -> date?.withLocale(locale)?.format(value)
                else -> null
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: DateTimeException) {
            null
        }
    }

    /** `<coins:choice:'0#no coins|1#one coin|1<{0} coins'>`. */
    data class Choice(private val pattern: String) : ArgumentFormat {

        private val template = "{0,choice,$pattern}"
        private val formats = object : LocaleFormats<MessageFormat>() {
            override fun create(locale: Locale): MessageFormat = MessageFormat(template, locale)
        }

        init {
            MessageFormat(template)
        }

        override fun format(value: Any, locale: Locale): String? =
            if (value is Number) formats.get(locale).format(arrayOf(value)) else null
    }

    /** `<online:'Online':'Offline'>`. */
    data class Bool(private val whenTrue: String, private val whenFalse: String) : ArgumentFormat {
        override fun format(value: Any, locale: Locale): String? =
            if (value is Boolean) (if (value) whenTrue else whenFalse) else null
    }

    companion object {
        private const val CHOICE = "choice"

        fun isFormat(arguments: List<String>): Boolean = arguments.size <= 2

        fun of(arguments: List<String>): ArgumentFormat? = when {
            arguments.isEmpty() -> Default
            arguments.size == 1 -> Pattern(arguments[0])
            arguments.size == 2 && arguments[0].lowercase(Locale.ROOT) == CHOICE -> Choice(arguments[1])
            arguments.size == 2 -> Bool(arguments[0], arguments[1])
            else -> null
        }
    }
}
