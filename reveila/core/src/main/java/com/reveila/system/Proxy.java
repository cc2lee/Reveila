package com.reveila.system;

import java.util.List;

import com.reveila.event.EventConsumer;

public interface Proxy extends EventConsumer {
	String getName();
	List<String> getRequiredRoles();
	Object invoke(final String methodName, final Object[] args) throws Exception;
}