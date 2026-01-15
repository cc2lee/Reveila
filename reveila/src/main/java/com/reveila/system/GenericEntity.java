package com.reveila.system;

import java.util.Map;

public class GenericEntity {

	private Map<String, Object> key;
	private Map<String, Object> attributes;
	
	public GenericEntity(Map<String, Object> key, Map<String, Object> attributes) {
		
		super();
		if (key == null || key.isEmpty()) {
			throw new IllegalArgumentException("Key cannot be null or empty.");
		}
		this.key = key;
		this.attributes = attributes;
	}
	
	public Map<String, Object> getKey() {
		return key;
	}
	
	public Map<String, Object> getAttributes() {
		return attributes;
	}}
