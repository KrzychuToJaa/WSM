package pl.edu.slowscore.ui

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

private val dateFormat = DateTimeFormatter.ofPattern("dd.MM.uuuu HH:mm")
    .withResolverStyle(ResolverStyle.STRICT)
private val zone = ZoneId.of("Europe/Warsaw")

internal fun date(value: Long): String = Instant.ofEpochMilli(value).atZone(zone).format(dateFormat)

internal fun parseDate(value: String): Long {
    val local = LocalDateTime.parse(value.trim(), dateFormat)
    require(zone.rules.getValidOffsets(local).size == 1) {
        "Termin wypada przy zmianie czasu. Wybierz inną godzinę."
    }
    return local.atZone(zone).toInstant().toEpochMilli()
}
