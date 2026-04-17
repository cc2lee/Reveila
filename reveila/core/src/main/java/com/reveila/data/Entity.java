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
		Objects.requireNonNull(type, "type cannot be null");
		Objects.requireNonNull(key, "key cannot be null");
		Objects.requireNonNull(attributes, "attributes cannot be null");

		map.put(TYPE, type);
		map.put(KEY, Map.copyOf(key));
		map.put(ATTRIBUTES, Map.copyOf(attributes));
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