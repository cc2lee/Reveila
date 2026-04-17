package com.reveila.system;

import java.util.List;

public interface Proxy {

	ClassLoader getClassLoader();
	
	String getName();

	List<String> getRequiredRoles();

	Object invoke(String methodName, Object[] args) throws Exception;
}