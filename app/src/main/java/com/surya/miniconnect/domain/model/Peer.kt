@file:Suppress("unused")

package com.surya.miniconnect.domain.model

/**
 * Data model representing a Wi‑Fi Direct peer device.
 *
 * @property name Human readable device name (may be empty).
 * @property address Hardware address of the device.
 * @property status One of WifiP2pDevice status constants.
 */
data class Peer(
    val name: String,
    val address: String,
    val status: Int
)

