package de.kewl.cashdy.domain

import java.text.Collator
import java.util.Locale

/** Alphabetisch wie im Telefonbuch: Groß/klein egal, „Ä“ steht bei „A“. */
object Sorting {
    private val collator: Collator = Collator.getInstance(Locale.GERMAN).apply { strength = Collator.SECONDARY }

    val german: Comparator<String> = Comparator { a, b -> collator.compare(a.trim(), b.trim()) }

    fun <T> byName(name: (T) -> String): Comparator<T> = compareBy(german, name)
}
