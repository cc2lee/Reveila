package com.reveila.data;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Entity {

	public static final String TYPE = "type";
	public static final String KEY = "key";
	public static final String ATTRIBUTES = "attributes";

	private Map<String, Object> map = Collections.checkedMap(new HashMap<String, Object>(), String.class, Object.class);

	public Entity(String type, Map<String, Map<String, Object>> key, Map<String, Object> attributes) {
		map.put(TYPE, type);
		map.put(KEY, Map.copyOf(key));
		map.put(ATTRIBUTES, new HashMap<>(attributes));
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