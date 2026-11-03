/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data

import java.util.Locale

/**
 * Order of the room cards. Without a saved order: alphabetical, but a group whose name contains
 * another room's name as a word (e.g. "Towel radiator Bath" → "Bath") follows that room, so the
 * thermostats of one room stay together. A saved order (ids) wins; rooms that are not in it yet
 * (new in the cloud) keep their default place relative to each other at the end.
 */
object RoomOrder {
    fun <T> arrange(items: List<T>, id: (T) -> String, label: (T) -> String, saved: List<String> = emptyList()): List<T> {
        val default = grouped(items, label)
        if (saved.isEmpty()) return default
        val position = saved.withIndex().associate { (i, v) -> v to i }
        val known = default.filter { id(it) in position }.sortedBy { position[id(it)] }
        return known + default.filter { id(it) !in position }
    }

    fun <T> grouped(items: List<T>, label: (T) -> String): List<T> {
        val sorted = items.sortedBy { norm(label(it)) }
        // Each item's "parent": the room with the longest name that occurs in its name as a word.
        val parent = sorted.associateWith { item ->
            val name = norm(label(item))
            sorted.filter { it !== item && norm(label(it)).length < name.length && containsWord(name, norm(label(it))) }
                .maxByOrNull { norm(label(it)).length }
        }
        val out = mutableListOf<T>()
        fun add(item: T) {
            if (item in out) return
            out += item
            sorted.filter { parent[it] === item }.forEach { add(it) }
        }
        sorted.filter { parent[it] == null }.forEach { add(it) }
        sorted.forEach { add(it) } // safety net for cycles
        return out
    }

    private fun norm(s: String) = s.trim().lowercase(Locale.ROOT)

    private fun containsWord(text: String, word: String) =
        word.isNotEmpty() && Regex("(?<![\\p{L}\\p{N}])${Regex.escape(word)}(?![\\p{L}\\p{N}])").containsMatchIn(text)
}
