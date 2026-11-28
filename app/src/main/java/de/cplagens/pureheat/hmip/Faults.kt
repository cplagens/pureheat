/*
 * PureHeat – Copyright 2026 Christian Plagens
 * Licensed under the Apache License, Version 2.0 (see LICENSE.md).
 * SPDX-License-Identifier: Apache-2.0
 */
package de.cplagens.pureheat.hmip

/** What kind of problem a device or heating circuit reports; texts live in `ui/FaultTexts.kt`. */
enum class FaultKind(val severity: Severity) {
    /** `ADJUSTMENT_TOO_BIG`: stroke too large – actuator not pressing on the valve, circuit stuck open. */
    VALVE_TOO_BIG(Severity.ERROR),
    /** `ADJUSTMENT_TOO_SMALL` on a connected channel: valve pin hardly moves (stuck). */
    VALVE_TOO_SMALL(Severity.ERROR),
    /** `TOO_TIGHT`: valve pin too stiff. */
    VALVE_TOO_TIGHT(Severity.ERROR),
    /** `ERROR_POSITION`: requested position not reached. */
    VALVE_POSITION(Severity.ERROR),
    /** `RUN_TO_START`, `WAIT_FOR_ADAPTION`, `ADAPTION_IN_PROGRESS`: learning the stroke, normal after mounting. */
    VALVE_ADAPTING(Severity.INFO),
    /** `STATE_NOT_AVAILABLE` or a state this app doesn't know. */
    VALVE_UNKNOWN(Severity.WARNING),
    /** `emergencyOperationActive`: controller lost its thermostat and runs a fixed emergency value. */
    EMERGENCY(Severity.WARNING),
    /** `unreach`: device does not answer the access point; its values are stale. */
    UNREACHABLE(Severity.ERROR),
    /** `lowBat` */
    LOW_BATTERY(Severity.WARNING),
    /** Overheated, under-voltage, power failure, overload, … (code says which). */
    DEVICE(Severity.ERROR),
    ;

    enum class Severity { INFO, WARNING, ERROR }
}

/** One problem to show to the user. [channelIndex] is null for device-wide faults. */
data class Fault(
    val kind: FaultKind,
    /** Raw API value, shown in the details (e.g. "ADJUSTMENT_TOO_BIG"). */
    val code: String,
    val deviceId: String,
    val deviceLabel: String,
    val channelIndex: Int? = null,
    val channelLabel: String? = null,
    val roomId: String? = null,
    val roomLabel: String? = null,
) {
    fun matches(circuit: HeatingCircuit): Boolean =
        channelIndex != null && deviceId == circuit.deviceId && channelIndex == circuit.channelIndex
}

object Faults {

    /** Device-base flags that mean a hardware problem (all booleans in `DEVICE_BASE*` channels). */
    val DEVICE_FLAGS = listOf(
        "deviceOverheated", "deviceUndervoltage", "devicePowerFailureDetected",
        "deviceOverloaded", "coProFaulty", "temperatureOutOfRange",
    )

    /** Maps the channel's `valveState`; null = fine (or nothing reported). */
    fun valveKind(valveState: String?): FaultKind? = when (valveState) {
        null, "", "ADAPTION_DONE" -> null
        "ADJUSTMENT_TOO_BIG" -> FaultKind.VALVE_TOO_BIG
        "ADJUSTMENT_TOO_SMALL" -> FaultKind.VALVE_TOO_SMALL
        "TOO_TIGHT" -> FaultKind.VALVE_TOO_TIGHT
        "ERROR_POSITION" -> FaultKind.VALVE_POSITION
        "RUN_TO_START", "WAIT_FOR_ADAPTION", "ADAPTION_IN_PROGRESS" -> FaultKind.VALVE_ADAPTING
        else -> FaultKind.VALVE_UNKNOWN
    }

    /** Most severe first; stable within a severity. */
    fun sorted(faults: List<Fault>): List<Fault> = faults.sortedByDescending { it.kind.severity }
}
