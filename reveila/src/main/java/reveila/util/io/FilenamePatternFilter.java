/*
 * Created on May 1, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package reveila.util.io;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author Charles Lee
 */
public class FilenamePatternFilter implements FilenameFilter {
	
	public static final short INCLUDE = 0;
	public static final short EXCLUDE = 1;
	
	private boolean caseSensitive = false;
	private Map<String, String[]> iMap = Collections.synchronizedMap(new HashMap<String, String[]>());
	private Map<String, String[]> eMap = Collections.synchronizedMap(new HashMap<String, String[]>());
	
	public FilenamePatternFilter() {
		super();
	}
	
	public FilenamePatternFilter(String pattern) {
		this();
		include(pattern);
	}
	
	public FilenamePatternFilter(String[] patterns) {
		this();
		if (patterns != null) {
			for (int i = 0; i < patterns.length; i++) {
				include(patterns[i]);
			}
		}
	}
	
	public FilenamePatternFilter(String patterns, String delimiter) {
		this();
		StringTokenizer t = new StringTokenizer(patterns, delimiter);
		while (t.hasMoreTokens()) {
			include(t.nextToken());
		}
	}
	
	public void include(String pattern) {
		this.addPattern(pattern, INCLUDE);
	}
	
	public void exclude(String pattern) {
		this.addPattern(pattern, EXCLUDE);
	}
	
	public synchronized void addPattern(String pattern, short rule) {
		if (pattern == null) {
			throw new IllegalArgumentException("Cannot use null pattern");
		}
		else if (pattern.length() == 0) {
			throw new IllegalArgumentException("Cannot use empty string, use '*' instead");
		}
		
		StringTokenizer t = new StringTokenizer(pattern, "*", true);
		int count = t.countTokens();
		String[] tokens = new String[count];
		for (int i = 0; i < count; i++) {
			tokens[i] = t.nextToken();
		}
		
		if (rule == INCLUDE) {
			this.eMap.remove(pattern);
			this.iMap.put(pattern, tokens);
		}
		else if (rule == EXCLUDE) {
			this.iMap.remove(pattern);
			this.eMap.put(pattern, tokens);
		}
		else {
			throw new IllegalArgumentException(
				"Illegal rule: " + rule + "! " +
					"Use either " + this.getClass().getName() + ".INCLUDE or " + 
						this.getClass().getName() + ".EXCLUDE");
		}
	}
	
	public void removePattern(String pattern) {
		if (pattern == null) {
			return;
		} 
		
		if (this.eMap.remove(pattern) == null) {
			this.iMap.remove(pattern);
		}
	}
	
	/**
	 * This method only evaluates the second argument <code>filename</code>.
	 * The first argument <cod>dir</code> is ignored.
	 * 
	 * @see java.io.FilenameFilter#accept(java.io.File, java.lang.String)
	 */
	public boolean accept(File dir, String filename) {
		if (filename == null) {
			throw new IllegalArgumentException("Cannot filter null filename");
		}
		
		if (!this.caseSensitive) {
			filename = filename.toLowerCase();
		}
		
		// It's guaranteed that the same pattern will never be present in both maps.
		// If a match is found in one map, there is no need to check the other map.
		// We start with the EXCLUDE map first, as it takes precedence.
		if (hasMatch(this.eMap, filename)) return false;
		if (hasMatch(this.iMap, filename)) return true;
		
		// If the filename matches none of the patterns in any map,
		// the filename is not qualified. Return false.
		return false;
	}
	
	private boolean hasMatch(Map<String, String[]> m, String filename) {
		boolean match = false;
		String token = null;
		String[] tokens = null;
		Collection<String[]> c = m.values();
		Iterator<String[]> itr = c.iterator();
		while (itr.hasNext()) {
			tokens = itr.next();
			token = tokens[0];
			if (!this.caseSensitive) {
				token = token.toLowerCase();
			}
			
			int cursor = 0;
			match = token.equals("*");
			if (!match) {
				match = filename.startsWith(token);
			}
			
			if (match) {
				cursor += token.length();
				for (int i2 = 1; i2 < tokens.length; i2++) {
					token = tokens[i2];
					if (token.equals("*")) {
						continue;
					}
					
					if (!this.caseSensitive) {
						token = token.toLowerCase();
					}
					
					cursor = filename.indexOf(token, cursor);
					match = cursor != -1;
					if (!match) {
						break;
					}
					else {
						cursor += token.length();
					}
				}
			}
			
			if (match && !token.equals("*")) {
				match = cursor == filename.length();
			}
			
			if (match) {
				break;
			}
		}
		
		return match;
	}
	
	/**
	 * @return
	 */
	public boolean isCaseSensitive() {
		return this.caseSensitive;
	}

	/**
	 * @param b
	 */
	public void setCaseSensitive(boolean b) {
		this.caseSensitive = b;
	}

}
