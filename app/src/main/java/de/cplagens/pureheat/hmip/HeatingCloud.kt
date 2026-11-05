/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.hmip

import kotlinx.serialization.json.JsonObject

/**
 * The cloud calls the repository makes after login: the real [HmipClient], or the offline demo
 * home (`data/demo/DemoCloud`). Same raw JSON either way, so the demo runs through the real parser.
 */
interface HeatingCloud {
    suspend fun getCurrentState(sgtin: String, authToken: String, restUrl: String?): JsonObject

    suspend fun getProfile(
        sgtin: String,
        authToken: String,
        rest: String,
        groupId: String,
        profileIndex: String,
        profileId: String,
    ): JsonObject

    suspend fun updateProfile(sgtin: String, authToken: String, rest: String, groupId: String, profileIndex: String, profile: JsonObject)

    suspend fun setActiveProfile(sgtin: String, authToken: String, rest: String, groupId: String, profileIndex: String)
}
