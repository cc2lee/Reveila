package reveila.system;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetaObject extends HashMap<String, Object> {

	private JsonConfiguration json;

	public MetaObject (Map<String,Object> map, JsonConfiguration json) {
		super.putAll(map);
		this.json = json;
	}

	public Map<String,Object> toMap() {
		return new HashMap<String,Object>(this);
	}

	public void save() throws IOException {
		json.writeToFile();
	}
	
	public String getName() {
		return (String)super.get(Constants.C_NAME);
	}

	public String getImplementationClassName() {
		return (String)super.get(Constants.C_CLASS);
	}

	public String getDescription() {
		return (String)super.get(Constants.C_DESCRIPTION);
	}

	public String getVersion() {
		return (String)super.get(Constants.C_VERSION);
	}

	public String getAuthor() {
		return (String)super.get(Constants.C_AUTHOR);
	}

	public String getLicense() {
		return (String)super.get(Constants.C_LICENSE_TOKEN);
	}

	@SuppressWarnings("unchecked")
	public List<Map<String, Object>> getArguments() {
		return (List<Map<String, Object>>)super.get(Constants.C_ARGUMENTS);
	}

	@SuppressWarnings("unchecked")
	public void setArgument(String name, Object value) {
		List<Map<String, Object>> list = (List<Map<String, Object>>)super.get(Constants.C_ARGUMENTS);
		if (list == null || list.isEmpty()) {
			throw new IllegalArgumentException("Argumment not defined in configuration: " + name);
		}
		for (Map<String, Object> map : list) {
			if (map.containsKey(name)) {
				map.put(name, value);
			}
		}
	}

	public Object newObject() throws Exception {	
		Class<?> clazz = Class.forName(getImplementationClassName());
		Object object;
		try {
			object = clazz.getDeclaredConstructor(new Class<?>[] { MetaObject.class }).newInstance(this);
		} catch (NoSuchMethodException e) {
			object = clazz.getDeclaredConstructor().newInstance();
			if (object instanceof Proxy) {
				((Proxy)object).setMetaObject(this);
			}
		}
        
		List<Map<String, Object>> list = getArguments();
		if (list != null) {
			for (Map<String, Object> map : list) {
				String name = (String)map.get(Constants.C_NAME);
				String type = (String)map.get(Constants.C_TYPE);
				Object value = map.get(Constants.C_VALUE);

				String setterName = "set" + name.substring(0, 1).toUpperCase() + name.substring(1);
				Method method = clazz.getMethod(setterName, Class.forName(type));
				method.invoke(object, value);
			}
		}
		
		return object;
	}
}