package com.reveila.util

import kotlin.jvm.JvmStatic

/**
 * Utility class providing convenience methods for manipulating strings.
 *
 * @author Charles Lee
 */
object StringUtil {

    /**
     * Truncates the source string to specified length, and appends the suffix if provided.
     *
     * @param srcStr source string
     * @param toLength length of characters after truncate
     * @param suffix suffix to append
     * @return new truncated string
     */
    @JvmStatic
    fun truncate(srcStr: String?, toLength: Int, suffix: String?): String {
        requireNotNull(srcStr) { "null source string" }
        var s = srcStr.trim()

        if (s.length > toLength) {
            s = s.substring(0, toLength - 1)
            if (suffix != null) {
                s += suffix
            }
        }
        return s
    }

    /**
     * Replaces tagged placeholders in a given string.
     *
     * @param source string to be parsed
     * @param tagLeft begin tag
     * @param tagRight close tag
     * @param replacements replacements properties/map
     * @param isTrimKey if keys should be trimmed before lookup
     * @param isKeyToLowerCase if converting keys to lower case before lookup
     * @param escChars escape character sequence used in the source string
     * @return new string with placeholders replaced
     */
    @JvmStatic
    fun replace(
        source: String?,
        tagLeft: String?,
        tagRight: String?,
        replacements: Map<*, *>?,
        isTrimKey: Boolean,
        isKeyToLowerCase: Boolean,
        escChars: String?
    ): String? {
        if (source == null || source.isEmpty() || tagLeft == null || tagRight == null || replacements == null) {
            return source
        }

        val sourceLen = source.length
        val newString = StringBuilder(sourceLen + 32)
        var currentIndex = 0
        val escLen = if (!escChars.isNullOrEmpty()) escChars.length else 0
        val hasEsc = escLen > 0

        val nonNullEsc = escChars ?: ""
        while (currentIndex < sourceLen) {
            if (hasEsc && source.startsWith(nonNullEsc, currentIndex)) {
                currentIndex = handleEscapeSequence(source, currentIndex, escLen, tagLeft, nonNullEsc, newString)
            } else {
                currentIndex = handleTagParsing(
                    source, currentIndex, tagLeft, tagRight,
                    replacements, isTrimKey, isKeyToLowerCase, hasEsc, escChars, newString
                )
            }
        }
        return newString.toString()
    }

    private fun handleEscapeSequence(
        source: String,
        currentIndex: Int,
        escLen: Int,
        tagLeft: String,
        escChars: String,
        newString: StringBuilder
    ): Int {
        val nextEscPos = currentIndex + escLen
        val sourceLen = source.length

        if (nextEscPos < sourceLen && source.startsWith(escChars, nextEscPos)) {
            newString.append(escChars)
            return nextEscPos + escLen
        }

        val tagLeftPos = currentIndex + escLen
        if (source.startsWith(tagLeft, tagLeftPos)) {
            newString.append(tagLeft)
            return tagLeftPos + tagLeft.length
        }

        newString.append(escChars)
        return currentIndex + escLen
    }

    private fun handleTagParsing(
        source: String,
        currentIndex: Int,
        tagLeft: String,
        tagRight: String,
        replacements: Map<*, *>,
        isTrimKey: Boolean,
        isKeyToLowerCase: Boolean,
        hasEsc: Boolean,
        escChars: String?,
        newString: StringBuilder
    ): Int {
        val sourceLen = source.length
        val startTagIdx = source.indexOf(tagLeft, currentIndex)

        if (startTagIdx == -1) {
            newString.append(source, currentIndex, sourceLen)
            return sourceLen
        }

        newString.append(source, currentIndex, startTagIdx)
        val keyStartIdx = startTagIdx + tagLeft.length
        val endTagIdx = source.indexOf(tagRight, keyStartIdx)

        if (endTagIdx == -1) {
            newString.append(source, startTagIdx, sourceLen)
            return sourceLen
        }

        val processedKey = extractAndFormatKey(
            source, keyStartIdx, endTagIdx,
            isTrimKey, isKeyToLowerCase, hasEsc, escChars
        )
        val replacement = replacements[processedKey]?.toString()

        if (replacement != null) {
            newString.append(replacement)
        } else {
            newString.append(source, startTagIdx, endTagIdx + tagRight.length)
        }

        return endTagIdx + tagRight.length
    }

    private fun extractAndFormatKey(
        source: String,
        start: Int,
        end: Int,
        isTrimKey: Boolean,
        isKeyToLowerCase: Boolean,
        hasEsc: Boolean,
        escChars: String?
    ): String {
        var key = source.substring(start, end)
        if (hasEsc && escChars != null && key.contains(escChars)) {
            key = removeEscapeChars(key, escChars)
        }
        if (isTrimKey) {
            key = key.trim()
        }
        if (isKeyToLowerCase) {
            key = key.lowercase()
        }
        return key
    }

    private fun removeEscapeChars(rawKey: String, escChars: String): String {
        val escLen = escChars.length
        val sb = StringBuilder(rawKey.length)
        var idx = 0
        val len = rawKey.length

        while (idx < len) {
            if (rawKey.startsWith(escChars, idx)) {
                val next = idx + escLen
                if (next < len && rawKey.startsWith(escChars, next)) {
                    sb.append(escChars)
                    idx = next + escLen
                } else {
                    idx += escLen
                }
            } else {
                sb.append(rawKey[idx])
                idx++
            }
        }
        return sb.toString()
    }
}
