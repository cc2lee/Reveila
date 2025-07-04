/*
 * Created on Feb 8, 2005
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package reveila.util;

/**
 * @author Charles Lee
 */
public final class FormField {
	
	private String name;
	private String label;
	private String description;
	private String className;
	private boolean isReadable = true;
	private boolean isWritable = true;
	private boolean isBoolean = false;
	private String value;

	public FormField(String name, String className, String value) {
		super();
		if (name == null || className == null || value == null) {
			throw new IllegalArgumentException(
				"name=" + name +
				", class-name=" + className +
				", value=" + value);
		}
		this.name = name;
		this.className = className;
		this.value = value;
	}
	
	/**
	 * @return Returns the note.
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @return Returns the Java type.
	 */
	public String getClassName() {
		return this.className;
	}
	/**
	 * @return Returns the displayName.
	 */
	public String getLabel() {
		return label;
	}
	/**
	 * @param label The displayName to set.
	 */
	public void setLabel(String label) {
		this.label = label;
	}
	
	public boolean isBoolean() {
		return isBoolean;
	}
	
	public void setBoolean(boolean isBoolean) {
		this.isBoolean = isBoolean;
	}
	
	public boolean isReadable() {
		return isReadable;
	}
	
	public void setReadable(boolean isReadable) {
		this.isReadable = isReadable;
	}
	
	public boolean isWritable() {
		return isWritable;
	}
	
	public void setWritable(boolean isWritable) {
		this.isWritable = isWritable;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public String getName() {
		return this.name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getValue() {
		return this.value;
	}
	
	public String setValue(String value) {
		String oldValue = this.value;
		this.value = value;
		return oldValue;
	}
}
