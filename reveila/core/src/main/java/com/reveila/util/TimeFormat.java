package com.reveila.util;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class TimeFormat {

	private TimeFormat() {
		super();
	}

	/*
	 * Formats a timestamp in milliseconds to a human-readable string.
	 * Example: "2026-05-20 23:32:15.482"
	 * 
	 * @param ms the timestamp in milliseconds
	 * 
	 * @return the formatted timestamp
	 */
	public static String timestamp(long ms) {
		// 1. Convert the raw milliseconds to a stateless Instant footprint
		Instant instant = Instant.ofEpochMilli(ms);

		// 2. Bind the instant to a time zone and format it
		DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")
				.withZone(ZoneId.systemDefault());

		return formatter.format(instant);
	}

	/*
	 * Formats a duration in milliseconds to a human-readable string.
	 * Example: "01:23:45.678"
	 * 
	 * @param ms the duration in milliseconds
	 * 
	 * @return the formatted duration
	 */
	public static String duration(long ms) {
		long totalSecs = ms / 1000;
		long hours = totalSecs / 3600;
		long minutes = (totalSecs % 3600) / 60;
		long seconds = totalSecs % 60;
		long millis = ms % 1000;

		StringBuilder sb = new StringBuilder(12); // "00:00:00.000" is 12 chars
		if (hours < 10)
			sb.append('0');
		sb.append(hours).append(':');
		if (minutes < 10)
			sb.append('0');
		sb.append(minutes).append(':');
		if (seconds < 10)
			sb.append('0');
		sb.append(seconds).append('.');
		if (millis < 100)
			sb.append('0');
		if (millis < 10)
			sb.append('0');
		sb.append(millis);

		return sb.toString();
	}

}
