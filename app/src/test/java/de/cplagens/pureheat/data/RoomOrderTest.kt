/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.data

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomOrderTest {
    private data class R(val id: String, val label: String)

    private val rooms = listOf(
        R("1", "Kitchen"),
        R("2", "Towel radiator Bath "),
        R("3", "Bath"),
        R("4", "Bedroom"),
        R("5", "WC"),
        R("6", "Towel radiator WC"),
    )

    private fun labels(list: List<R>) = list.map { it.label.trim() }

    @Test
    fun thermostatsFollowTheirRoom() {
        assertEquals(
            listOf("Bath", "Towel radiator Bath", "Bedroom", "Kitchen", "WC", "Towel radiator WC"),
            labels(RoomOrder.arrange(rooms, { it.id }, { it.label })),
        )
    }

    @Test
    fun wordMatchOnly() {
        // "Bath" must not pull "Bathroom 2" (no whole-word match).
        val r = listOf(R("a", "Bathroom 2"), R("b", "Bath"))
        assertEquals(listOf("Bath", "Bathroom 2"), labels(RoomOrder.arrange(r, { it.id }, { it.label })))
    }

    @Test
    fun savedOrderWinsAndNewRoomsGoLast() {
        val saved = listOf("5", "1", "3")
        assertEquals(
            listOf("WC", "Kitchen", "Bath", "Towel radiator Bath", "Bedroom", "Towel radiator WC"),
            labels(RoomOrder.arrange(rooms, { it.id }, { it.label }, saved)),
        )
    }
}
