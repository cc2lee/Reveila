package com.reveila.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class Entity {

	public static final String TYPE = "type";
	public static final String KEY = "key";
	public static final String ATTRIBUTES = "attributes";

	private final Map<String, Object> map = new HashMap<>();

	public Entity(String type, Map<String, Map<String, Object>> key, Map<String, Object> attributes) {

		if (type == null || type.trim().isEmpty()) {
			throw new IllegalArgumentException("Type must not be null or blank");
		} else {
			map.put(TYPE, type);
		}

		if (key == null || key.isEmpty()) {
			map.put(KEY, null);
		} else {
			for (Map.Entry<String, Map<String, Object>> entry : key.entrySet()) {
				Objects.requireNonNull(entry.getKey(), "Key map cannot contain null keys");
				Objects.requireNonNull(entry.getValue(), "Key map cannot contain null values");
				for (Map.Entry<String, Object> innerEntry : entry.getValue().entrySet()) {
					Objects.requireNonNull(innerEntry.getKey(), "Inner key map cannot contain null keys");
					Objects.requireNonNull(innerEntry.getValue(), "Inner key map cannot contain null values");
				}
			}
			map.put(KEY, Map.copyOf(key));
		}

		Objects.requireNonNull(attributes, "Attributes map cannot be null");
		if (attributes.isEmpty()) {
			throw new IllegalArgumentException("Attributes map must contain at least one entry");
		} else {
			for (Map.Entry<String, Object> entry : attributes.entrySet()) {
				Objects.requireNonNull(entry.getKey(), "Attributes map cannot contain null keys");
			}
			map.put(ATTRIBUTES, attributes);
		}
	}

	public String getType() {
		return (String) map.get(TYPE);
	}

	@SuppressWarnings("unchecked")
	public Map<String, Map<String, Object>> getKey() {
		return (Map<String, Map<String, Object>>) map.get(KEY);
	}

	@SuppressWarnings("unchecked")
	public Map<String, Object> getAttributes() {
		return (Map<String, Object>) map.get(ATTRIBUTES);
	}
}