package com.aviansh.aifilemanager.domain.transactions

/**
 * How dangerous a tool call is. Used to decide presentation emphasis in the UI;
 * whether a call needs confirmation is declared by the tool itself.
 */
enum class RiskLevel {
    SAFE,
    MODERATE,
    HIGH
}
