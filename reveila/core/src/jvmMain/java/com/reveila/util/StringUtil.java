package com.reveila.util;

import java.util.Properties;

/**
 * @author Charles Lee
 *
 *         This utility class provides convenience methods for manipulating Java
 *         strings.
 */
public class StringUtil {

	private StringUtil() {
		// Private constructor to prevent instantiation
	}

	/**
	 * Truncates the source string to specified length, and appends the suffix if
	 * provided.
	 * 
	 * @param srcStr   source string
	 * @param toLength length of characters after truncate
	 * @param suffix   suffix to append
	 * @return new truncated string
	 */
	public static String truncate(String srcStr, int toLength, String suffix) {
		if (srcStr == null)
			throw new IllegalArgumentException("null source string");
		String s = srcStr.trim();

		if (s.length() > toLength) {
			s = s.substring(0, toLength - 1);
			if (suffix != null) {
				s = s + suffix;
			}
		}

		return s;
	}

	/**
	 * Replaces tagged placeholders in a given string.
	 * For example, if there is a placeholder like this one "{key-name}"
	 * in the source string, you can replace the whole tag with a
	 * string from the replacements Properties whose key equals "key-name".
	 * 
	 * @param source           string to be parsed
	 * @param tagLeft          begin tag
	 * @param tagRight         close tag
	 * @param replacements     replacements properties
	 * @param isTrimKey        if keys should be trimmed before lookup
	 * @param isKeyToLowerCase if converting keys to lower case before lookup
	 * @param escChars         escape character sequence used in the source string
	 * @return new string with placeholders replaced
	 */
	public static String replace(String source, String tagLeft, String tagRight,
			Properties replacements, boolean isTrimKey, boolean isKeyToLowerCase, String escChars) {

		// 1. Fail-fast boundary checks
		if (source == null || source.isEmpty() || tagLeft == null || tagRight == null || replacements == null) {
			return source;
		}

		int sourceLen = source.length();
		StringBuilder newString = new StringBuilder(sourceLen + 32);

		int currentIndex = 0;
		int escLen = (escChars != null && !escChars.isEmpty()) ? escChars.length() : 0;
		boolean hasEsc = escLen > 0;

		while (currentIndex < sourceLen) {
			// 2. Delegate escape sequence tracking out of the main loop
			if (hasEsc && source.startsWith(escChars, currentIndex)) {
				currentIndex = handleEscapeSequence(source, currentIndex, escLen, tagLeft, escChars, newString);
			} else {
				// 3. Delegate placeholder scanning and token transformation
				currentIndex = handleTagParsing(source, currentIndex, tagLeft, tagRight,
						replacements, isTrimKey, isKeyToLowerCase, hasEsc, escChars, newString);
			}
		}

		return newString.toString();
	}

	/**
	 * Handles isolated escape sequences in the raw text stream.
	 */
	private static int handleEscapeSequence(
			String source, int currentIndex, int escLen,
			String tagLeft, String escChars, StringBuilder newString) {

		int nextEscPos = currentIndex + escLen;
		int sourceLen = source.length();

		// Check for double escape sequence (e.g., \\)
		if (nextEscPos < sourceLen && source.startsWith(escChars, nextEscPos)) {
			newString.append(escChars);
			return nextEscPos + escLen;
		}

		// Check if a single escape sequence targets the left tag boundary
		int tagLeftPos = currentIndex + escLen;
		if (source.startsWith(tagLeft, tagLeftPos)) {
			newString.append(tagLeft);
			return tagLeftPos + tagLeft.length();
		}

		// Standard literal fallback
		newString.append(escChars);
		return currentIndex + escLen;
	}

	/**
	 * Locates, extracts, filters, and resolves matching template tags.
	 */
	@SuppressWarnings("squid:S107")
	private static int handleTagParsing(
			String source, int currentIndex, String tagLeft, String tagRight,
			Properties replacements, boolean isTrimKey, boolean isKeyToLowerCase,
			boolean hasEsc, String escChars, StringBuilder newString) {

		int sourceLen = source.length();
		int startTagIdx = source.indexOf(tagLeft, currentIndex);

		if (startTagIdx == -1) {
			newString.append(source, currentIndex, sourceLen);
			return sourceLen; // Forces outer loop termination naturally
		}

		// Flush text accumulated leading up to the tag
		newString.append(source, currentIndex, startTagIdx);
		int keyStartIdx = startTagIdx + tagLeft.length();

		int endTagIdx = source.indexOf(tagRight, keyStartIdx);
		if (endTagIdx == -1) {
			newString.append(source, startTagIdx, sourceLen);
			return sourceLen; // Handles malformed trailing tags safely
		}

		// Transform token and resolve against properties map
		String processedKey = extractAndFormatKey(
				source, keyStartIdx, endTagIdx,
				isTrimKey, isKeyToLowerCase, hasEsc, escChars);
		String replacement = replacements.getProperty(processedKey);

		if (replacement != null) {
			newString.append(replacement);
		} else {
			newString.append(source, startTagIdx, endTagIdx + tagRight.length());
		}

		return endTagIdx + tagRight.length();
	}

	/**
	 * Extracts token segments and cleans up internal escape chars or formatting
	 * bounds.
	 */
	private static String extractAndFormatKey(String source, int start, int end,
			boolean isTrimKey, boolean isKeyToLowerCase,
			boolean hasEsc, String escChars) {
		String key = source.substring(start, end);

		if (hasEsc && key.contains(escChars)) {
			key = removeEscapeChars(key, escChars); // Calls your underlying utility engine
		}
		if (isTrimKey) {
			key = key.trim();
		}
		if (isKeyToLowerCase) {
			key = key.toLowerCase();
		}

		return key;
	}

	/**
	 * Helper to strip escape signatures out of extracted token keys
	 */
	private static String removeEscapeChars(String rawKey, String escChars) {
		int escLen = escChars.length();
		StringBuilder sb = new StringBuilder(rawKey.length());
		int idx = 0;
		int len = rawKey.length();

		while (idx < len) {
			if (rawKey.startsWith(escChars, idx)) {
				int next = idx + escLen;
				if (next < len && rawKey.startsWith(escChars, next)) {
					sb.append(escChars);
					idx = next + escLen;
				} else {
					idx += escLen; // Skip singular escape characters
				}
			} else {
				sb.append(rawKey.charAt(idx));
				idx++;
			}
		}
		return sb.toString();
	}
}
