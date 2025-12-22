package com.reveila.spring.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * A Data Transfer Object for product details.
 * Using a DTO provides better type safety than a generic Map.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDetailsDTO(String id, String name, double price) {}