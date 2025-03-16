package com.nateshmbhat.card_scanner.scanner_core.scan_filters

import android.util.Log
import com.google.mlkit.vision.text.Text
import com.nateshmbhat.card_scanner.logger.debugLog
import com.nateshmbhat.card_scanner.scanner_core.constants.CardHolderNameConstants
import com.nateshmbhat.card_scanner.scanner_core.constants.CardScannerRegexps
import com.nateshmbhat.card_scanner.scanner_core.models.*
import java.util.*
import kotlin.math.max
import kotlin.math.min
class CardHolderNameFilter(visionText: Text, scannerOptions: CardScannerOptions, private val cardNumberScanResult: CardNumberScanResult) : ScanFilter(visionText, scannerOptions) {
    private val cardHolderRegex: Regex = Regex("[A-Z\\s]+", RegexOption.MULTILINE)
    val _maxBlocksBelowCardNumberToSearchForName = 4

    override fun filter(): CardHolderNameScanResult? {
        if (!scannerOptions.scanCardHolderName) return null
        if (cardNumberScanResult.cardNumber.isEmpty()) return null

        Log.d("CardHolderNameFilter", "Vision Text: ${visionText.textBlocks.joinToString("\n") { it.text }}")

        val minTextBlockIndexToSearchName = max(cardNumberScanResult.textBlockIndex - 1, 0)
        val maxTextBlockIndexToSearchName = min(cardNumberScanResult.textBlockIndex + _maxBlocksBelowCardNumberToSearchForName, visionText.textBlocks.size - 1)

        Log.d("CardHolderNameFilter", "Searching between blocks: $minTextBlockIndexToSearchName to $maxTextBlockIndexToSearchName")

        for (index in minTextBlockIndexToSearchName..maxTextBlockIndexToSearchName) {
            val block = visionText.textBlocks[index]
            val transformedBlockText = transformBlockText(block.text)

            if (!cardHolderRegex.containsMatchIn(transformedBlockText)) continue
            val cardHolderName = cardHolderRegex.find(transformedBlockText)!!.value.trim()

            Log.d("CardHolderNameFilter", "Found potential cardholder name: '$cardHolderName'")

            if (isValidName(cardHolderName)) {
                return CardHolderNameScanResult(
                    textBlockIndex = index, textBlock = block, cardHolderName = cardHolderName, visionText = visionText)
            }
        }
        return null
    }

    private fun isValidName(cardHolder: String): Boolean {
        if (cardHolder.length < 3 || cardHolder.length > scannerOptions.maxCardHolderNameLength) return false
        if (cardHolder.startsWith("valid from") || cardHolder.startsWith("valid thru")) return false
        if (cardHolder.endsWith("valid from") || cardHolder.endsWith("valid thru")) return false

        val excludedWords = setOf("bank", "finance", "microfinance", "islamic", "credit", "debit")
        if (excludedWords.any { cardHolder.contains it, ignoreCase = true) }) return false

        if (CardHolderNameConstants.defaultBlackListedWords
                        .union(scannerOptions.cardHolderNameBlackListedWords.toSet())
                        .contains(cardHolder.toLowerCase(Locale.ENGLISH))) {
            return false
        }

        val words = cardHolder.split(" ")
        if (words.size == 3 && words all { it == it.toUpperCase() } && words[1].length == 1) {
            return true
        }

        return false
    }

    private fun transformBlockText(blockText: String): String {
        return blockText.replace('c', 'C')
                .replace('o', 'O')
                .replace('p', 'P')
                .replace('v', 'V')
                .replace('w', 'W')
    }
}
