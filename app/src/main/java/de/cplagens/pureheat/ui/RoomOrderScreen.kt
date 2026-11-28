/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.cplagens.pureheat.R
import de.cplagens.pureheat.hmip.Room

/**
 * Order of the room cards on the dashboard, laid out like the help screen (back + title, cards).
 * Every move is saved right away; an empty list means the default (alphabetical) order.
 */
@Composable
fun RoomOrderScreen(
    rooms: List<Room>,
    onChange: (List<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(0.dp)) { Text(stringResource(R.string.back)) }
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.room_order_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { onChange(emptyList()) }) { Text(stringResource(R.string.room_order_reset)) }
            }
        }
        itemsIndexed(rooms, key = { _, r -> r.id }) { i, room ->
            fun move(to: Int) = onChange(rooms.map { it.id }.toMutableList().apply { add(to, removeAt(i)) })
            Card(Modifier.fillMaxWidth().animateItem()) {
                Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(room.label.trim(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    val up = stringResource(R.string.move_up)
                    val down = stringResource(R.string.move_down)
                    TextButton(
                        onClick = { move(i - 1) },
                        enabled = i > 0,
                        modifier = Modifier.semantics { contentDescription = up },
                    ) { Text("↑", style = MaterialTheme.typography.titleMedium) }
                    TextButton(
                        onClick = { move(i + 1) },
                        enabled = i < rooms.lastIndex,
                        modifier = Modifier.semantics { contentDescription = down },
                    ) { Text("↓", style = MaterialTheme.typography.titleMedium) }
                }
            }
        }
    }
}
