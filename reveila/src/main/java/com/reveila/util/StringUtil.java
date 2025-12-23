package com.reveila.util;

import java.util.Properties;

/**
 * @author Charles Lee
 *
 * This utility class provides convenience methods for manipulating Java strings.
 */
public class StringUtil {
	
	private static final int KEY_BEGIN_TAG = 0;
	private static final int KEY_END_TAG = 1;

	public static Properties splitCommandlineArguments(String[] strArray, String delimiter) {
		Properties cmdArgs = new Properties();
		if (strArray != null) {
			for (String arg : strArray) {
				String[] parts = arg.split(delimiter, 2);
				if (parts.length == 2 && !parts[0].isEmpty()) {
					cmdArgs.put(parts[0], parts[1]);
				} else {
					// It's good practice to warn about arguments that don't fit the expected format.
					// Since the logger isn't configured yet, System.err is the best option.
					System.err.println("Warning: Ignoring malformed command-line argument: " + arg);
				}
			}
		}
		return cmdArgs;
	}

	/**
	 * Tests if a string is null or has the length of 0.
	 * @param s string to be checked
	 * @return true if the string is empty (null or 0 length), false otherwise
	 */
	public static boolean hasCharacters(String s) {
		return s != null && s.length() > 0;
	}
	
	/**
	 * Tests if the character <code>c</code> is a space.
	 * @param c character
	 * @return true if the char is a space, or false if not
	 */
	public static boolean isSpace(char c) {
		return (c == ' ' || c == '\r' || c == '\t' || c == '\n');
	}
	
	/**
	 * Truncates the source string to specified length, and appends the suffix if provided.
	 * @param srcStr source string
	 * @param toLength length of characters after truncate
	 * @param suffix suffix to append
	 * @return new truncated string
	 */
	public static String truncate(String srcStr, int toLength, String suffix) {
		if (srcStr == null) throw new IllegalArgumentException("null source string");
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
     * Replaces tagged placeholders in a given string. For example, if there is a placeholder
     * like this one "{key-name}" in the source string, you can replace the whole tag with a
     * String from the passed-in Hashtable whose key equals "key-name".
     * @param source string to be parsed
     * @param tagLeft begin tag
     * @param tagRight close tag
     * @param replacements Map containing the strings for replacement
     * @param isTrimKey boolean indicating if the key should be trimmed first
     * @param isKeyToLowerCase boolean specifying if converting all keys to lower case
     * @param escChars escape character used in the source string
     * @return new string with placeholders replaced
     */
	public static String replace(String source, String tagLeft, String tagRight,
			Properties replacements, boolean isTrimKey, boolean isKeyToLowerCase, String escChars) {
		
		if (source == null || source.length() == 0) {
			return source;
		}
        
		StringBuffer newStringBuffer = new StringBuffer();
        
		String[] tags = new String[2];
		tags[KEY_BEGIN_TAG] = tagLeft;
		tags[KEY_END_TAG] = tagRight;
        
		int currentIndex = 0;
		int i = 0;
        
		while (currentIndex < source.length()) {
			for (int tag = 0; tag < 2 && currentIndex < source.length(); tag++) {
				
				i = source.indexOf(tags[tag], currentIndex);
				boolean tagMarkerFound = i == -1 ? false : true;
				
				StringBuffer strToken;
				if (tagMarkerFound) {
					strToken = new StringBuffer(source.substring(currentIndex, i));
					currentIndex = i + tags[tag].length();
				}
				else {
					strToken = new StringBuffer(source.substring(currentIndex));
					currentIndex = source.length();
				}
				
				// Remove escChars from strToken
				if (escChars != null && escChars.length() > 0) {
					
					int b = 0;
					int e = 0;
					String rawString = strToken.toString();
					strToken.setLength(0);
					
					boolean escaped = false;
					while (b < rawString.length()) {
						e = rawString.indexOf(escChars, b);
						if (e == -1) {
							strToken.append(rawString.substring(b));
							escaped = false;
							break;
						}
						else {
							strToken.append(rawString.substring(b, e));
							b = e + escChars.length();
							if (rawString.indexOf(escChars, b) == b) {
								strToken.append(escChars);
								b += escChars.length();
								escaped = false;
							} else {
								escaped = true;
							}
						}
					}
					
					// Check if the tag marker is escaped
					if (tagMarkerFound) {
						if (escaped) {
							tagMarkerFound = false;
							strToken.append(tags[tag]);
						}
						else if (currentIndex < source.length() && tags[tag].equals(escChars)) {
							// Check if it is followed by the escChar.
							// If yes, it is an escChar.
							if (source.indexOf(tags[tag], currentIndex) == currentIndex) {
								strToken.append(tags[tag]);
								currentIndex += tags[tag].length();
								tagMarkerFound = false;
							}
						}
					}
				}
                
				if (tagMarkerFound && tag == KEY_END_TAG) {
					
					// Substitute with replacement value
					String key = strToken.toString();
					if (isTrimKey) {
						key = key.trim();
					}
					if (isKeyToLowerCase) {
						key = key.toLowerCase();
					}
					
					Object obj = replacements.get(key);
					if (obj == null) {
						String originKey = strToken.toString();
						strToken.setLength(0);
						strToken.append(tagLeft).append(originKey).append(tagRight);
					}
					else {
						strToken.setLength(0);
						strToken.append(obj.toString());
					}
				}
                
				newStringBuffer.append(strToken);
			}
		}
        
		return newStringBuffer.toString();
	}

}
