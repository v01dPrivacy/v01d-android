package com.voidprivacy.app.tools.txchecker

import org.bitcoinj.core.Transaction
import org.bitcoinj.params.MainNetParams
import org.bitcoinj.script.Script

/**
 * Transaction privacy heuristics engine.
 * Inspired by privateTx + am-i-exposed: analyzes a raw Bitcoin transaction
 * for common privacy leaks. Fully offline — parses raw tx hex locally.
 *
 * Heuristics implemented:
 *  1. Address reuse — input address reappears as output
 *  2. Common-input ownership — multiple inputs likely same wallet
 *  3. Change output detection — round-amount + address-reuse heuristics
 *  4. Peeling chain — 1 input, 2 outputs, one ≈ input amount
 *  5. Round amount — "clean" numbers likely payment, not change
 *  6. Mixed address types — different script types leak wallet software
 *  7. Single output — no change = good privacy
 */
object TxPrivacyAnalyzer {

    private val params = MainNetParams.get()

    data class AnalysisResult(
        val score: Int,
        val findings: List<Finding>,
        val txSummary: TxSummary?,
    )

    data class TxSummary(
        val txid: String,
        val inputCount: Int,
        val outputCount: Int,
        val totalOutput: Long, // satoshis
        val outputAddresses: List<String>,
    )

    data class Finding(
        val name: String,
        val severity: Severity,
        val description: String,
    )

    enum class Severity { LOW, MEDIUM, HIGH }

    fun analyzeRawTx(hex: String): AnalysisResult {
        val cleanHex = hex.trim().replace(Regex("\\s"), "")
        val txBytes = try {
            cleanHex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        } catch (e: Exception) {
            return errorResult("Invalid hex string: ${e.message}")
        }

        val tx = try {
            Transaction(params, txBytes)
        } catch (e: Exception) {
            return errorResult("Cannot parse transaction: ${e.message}")
        }

        return analyzeTransaction(tx)
    }

    fun analyzeInput(input: String): AnalysisResult {
        val trimmed = input.trim()

        // Raw tx hex (long hex string, typically > 100 chars)
        if (trimmed.length > 100 && trimmed.matches(Regex("^[a-fA-F0-9\\s]+$"))) {
            return analyzeRawTx(trimmed)
        }

        // Txid (64 hex chars) — can't analyze offline without tx data
        if (trimmed.matches(Regex("^[a-fA-F0-9]{64}$"))) {
            return AnalysisResult(
                score = -1,
                findings = listOf(
                    Finding(
                        "Network Lookup Required",
                        Severity.MEDIUM,
                        "Txid detected. Offline analysis requires raw transaction hex.\n\n" +
                        "To get raw hex: use your wallet's 'export transaction' feature, " +
                        "or a block explorer's 'raw transaction' endpoint.\n\n" +
                        "Paste the full raw hex for local analysis — no network calls needed.",
                    )
                ),
                txSummary = null,
            )
        }

        // BTC address — limited analysis
        if (trimmed.startsWith("bc1") || trimmed.startsWith("1") || trimmed.startsWith("3")) {
            return AnalysisResult(
                score = -1,
                findings = listOf(
                    Finding(
                        "Address Detected",
                        Severity.LOW,
                        "Address analysis requires transaction data. Paste a raw transaction hex " +
                        "or a txid for analysis.",
                    )
                ),
                txSummary = null,
            )
        }

        return errorResult("Unrecognized input. Paste a raw transaction hex, txid, or BTC address.")
    }

    private fun analyzeTransaction(tx: Transaction): AnalysisResult {
        val findings = mutableListOf<Finding>()
        var penalty = 0

        val outputs = tx.outputs
        val inputs = tx.inputs
        val txid = tx.txId.toString()

        // Collect output info
        val outputInfos = outputs.mapIndexed { idx, out ->
            val addr = try {
                out.scriptPubKey.getToAddress(params)?.toString() ?: "unknown"
            } catch (_: Exception) { "non-standard" }
            val value = out.value.value
            val scriptType = try {
                out.scriptPubKey.scriptType
            } catch (_: Exception) { null }
            OutputInfo(idx, addr, value, scriptType)
        }

        val totalOutput = outputInfos.sumOf { it.value }

        // Collect input addresses (only available if inputs are connected to prev outputs)
        val inputAddresses = inputs.mapNotNull { inp ->
            try {
                val connected = inp.connectedOutput
                connected?.scriptPubKey?.getToAddress(params)?.toString()
            } catch (_: Exception) { null }
        }

        // === HEURISTIC 1: Address reuse ===
        val outputAddrSet = outputInfos.map { it.address }.toSet()
        val reusedAddresses = inputAddresses.filter { it in outputAddrSet }
        if (reusedAddresses.isNotEmpty()) {
            penalty += 20
            findings.add(Finding(
                "Address Reuse",
                Severity.HIGH,
                "${reusedAddresses.size} input address(es) reappear as output addresses. " +
                "This links your old and new addresses, breaking unlinkability. " +
                "Use a fresh address for change.",
            ))
        }

        // === HEURISTIC 2: Common-input ownership ===
        if (inputs.size > 1) {
            penalty += 10
            findings.add(Finding(
                "Common Input Ownership",
                Severity.MEDIUM,
                "${inputs.size} inputs in this transaction. Observers assume all inputs " +
                "belong to the same wallet, clustering them together.",
            ))
        }

        // === HEURISTIC 3: Change output detection ===
        if (outputs.size == 2) {
            val (o1, o2) = outputInfos
            // Round amount heuristic: one output is "round", the other is change
            val o1Round = isRoundAmount(o1.value)
            val o2Round = isRoundAmount(o2.value)
            if (o1Round xor o2Round) {
                val changeIdx = if (o1Round) 1 else 0
                penalty += 10
                findings.add(Finding(
                    "Change Output Detected",
                    Severity.MEDIUM,
                    "Output #$changeIdx (${formatBtc(outputInfos[changeIdx].value)} BTC) " +
                    "is likely the change output (non-round amount). " +
                    "Observers can identify which output is payment vs change.",
                ))
            }

            // Address type heuristic: change often matches input address type
            if (inputAddresses.isNotEmpty()) {
                val changeByReuse = outputInfos.find { it.address in inputAddresses }
                if (changeByReuse != null) {
                    penalty += 5
                    findings.add(Finding(
                        "Change by Address Match",
                        Severity.LOW,
                        "Output #${changeByReuse.index} sends to an input address — " +
                        "likely the change output.",
                    ))
                }
            }
        }

        // === HEURISTIC 4: Peeling chain ===
        if (inputs.size == 1 && outputs.size == 2) {
            val larger = outputInfos.maxByOrNull { it.value }
            val smaller = outputInfos.minByOrNull { it.value }
            if (larger != null && smaller != null && larger.value > 0) {
                val ratio = larger.value.toDouble() / (larger.value + smaller.value)
                if (ratio > 0.8) {
                    penalty += 15
                    findings.add(Finding(
                        "Peeling Chain Pattern",
                        Severity.HIGH,
                        "1 input → 2 outputs where one output is ${"%.1f".format(ratio * 100)}% " +
                        "of the total. This looks like a peeling chain (systematic spend-down), " +
                        "which links a series of transactions together.",
                    ))
                }
            }
        }

        // === HEURISTIC 5: Round amounts ===
        val roundOutputs = outputInfos.filter { isRoundAmount(it.value) }
        if (roundOutputs.isNotEmpty() && outputs.size > 1) {
            penalty += 5
            findings.add(Finding(
                "Round Amount",
                Severity.LOW,
                "${roundOutputs.size} output(s) have round amounts " +
                "(${roundOutputs.joinToString { formatBtc(it.value) }} BTC). " +
                "Round numbers are likely payments; the other output is change.",
            ))
        }

        // === HEURISTIC 6: Mixed address types ===
        val scriptTypes = outputInfos.mapNotNull { it.scriptType }.distinct()
        if (scriptTypes.size > 1) {
            penalty += 10
            findings.add(Finding(
                "Mixed Address Types",
                Severity.MEDIUM,
                "Outputs use ${scriptTypes.size} different script types " +
                "(${scriptTypes.joinToString { it.name }}). " +
                "This reveals wallet software and breaks output uniformity.",
            ))
        }

        // === HEURISTIC 7: Single output (good) ===
        if (outputs.size == 1) {
            findings.add(Finding(
                "Single Output",
                Severity.LOW,
                "Only 1 output — no change output to identify. Good for privacy. " +
                "This could be a sweep or a CoinJoin-style transaction.",
            ))
        }

        // No findings = clean
        if (findings.isEmpty()) {
            findings.add(Finding(
                "No Issues Detected",
                Severity.LOW,
                "No common privacy leaks found in this transaction structure.",
            ))
        }

        val score = (100 - penalty).coerceIn(0, 100)

        return AnalysisResult(
            score = score,
            findings = findings,
            txSummary = TxSummary(
                txid = txid,
                inputCount = inputs.size,
                outputCount = outputs.size,
                totalOutput = totalOutput,
                outputAddresses = outputInfos.map { it.address },
            ),
        )
    }

    private data class OutputInfo(
        val index: Int,
        val address: String,
        val value: Long,
        val scriptType: Script.ScriptType?,
    )

    private fun isRoundAmount(satoshis: Long): Boolean {
        val btc = satoshis / 100_000_000.0
        // "Round" = fewer than 3 significant decimal digits
        return btc == Math.floor(btc * 100) / 100.0 && btc > 0
    }

    private fun formatBtc(satoshis: Long): String {
        return "%.8f".format(satoshis / 100_000_000.0).trimEnd('0').trimEnd('.')
    }

    private fun errorResult(msg: String) = AnalysisResult(
        score = -1,
        findings = listOf(Finding("Error", Severity.HIGH, msg)),
        txSummary = null,
    )
}
