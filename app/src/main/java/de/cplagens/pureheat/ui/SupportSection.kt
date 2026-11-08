/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import de.cplagens.pureheat.R

/**
 * Voluntary tip through Google Play. Shows nothing when Play can't offer it (installed from
 * elsewhere, debug build, product not set up) – the app works the same either way.
 */
@Composable
fun SupportSection(vm: SupportViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    val price = state.price ?: return
    val activity = LocalContext.current.findActivity()
    HorizontalDivider()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.support_text))
        FilledTonalButton(onClick = { activity?.let(vm::buy) }, enabled = activity != null) {
            Text(stringResource(R.string.coffee_buy, price))
        }
        if (state.pending) Text(stringResource(R.string.coffee_pending), style = MaterialTheme.typography.bodySmall)
        if (state.thanked) Text(stringResource(R.string.coffee_thanks), color = MaterialTheme.colorScheme.primary)
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
