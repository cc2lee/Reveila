package reveila.system;

import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import reveila.util.StringUtil;
import reveila.util.xml.XmlConf;

/**
 * @author Charles Lee
 */
public final class Configuration {

	private static List<String> configFileList = new ArrayList<String>();

	private static Properties envs = new Properties();
	static {
		Map<String, String> m = System.getenv();
		if (m != null) {
			Set<String> keys = m.keySet();
			Iterator<String> i = keys.iterator();
			while (i.hasNext()) {
				String key = i.next();
				String value = m.get(key);
				envs.put(key.toLowerCase(), value);
			}
		}
	}

	public static void addConfigurationFilePath (String path) {
		if (path == null) {
			throw new IllegalArgumentException("Argument can not be null");
		}
		configFileList.add(path);
	}

	public static String fileContentToString(String filePath) throws IOException {
		String content = new String(Files.readAllBytes(Paths.get(filePath)), java.nio.charset.StandardCharsets.UTF_8);
		return content;
	}

	public static String resolveConfigurationVariables(String source) {
		if (source == null || source.length() == 0) {
			throw new IllegalArgumentException("Source string must not be null or empty");
		}
		
		String result = StringUtil.replace(source, 
			"${", "}", envs, true, true, "$");
		return result;
	}
	/* 
	public static Map<String, Object> xmlFileToMap(String filePathString) throws Exception {
		File file = new File(filePathString);
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = dbf.newDocumentBuilder();
		Document doc = db.parse(file);
		doc.getDocumentElement().normalize();
		return elementToMap(doc.getDocumentElement());
	} */

	/* 
	private static Map<String, Object> elementToMap(Element element) {
		Map<String, Object> map = new HashMap<>();
		NodeList nodeList = element.getChildNodes();
		for (int i = 0; i < nodeList.getLength(); i++) {
			Node node = nodeList.item(i);
			if (node.getNodeType() == Node.ELEMENT_NODE) {
				Element child = (Element) node;
				map.put(child.getNodeName(), elementToMap(child));
			}
		}
		// Add attributes if needed
		NamedNodeMap attrs = element.getAttributes();
		for (int i = 0; i < attrs.getLength(); i++) {
			Node attr = attrs.item(i);
			map.put(attr.getNodeName(), attr.getNodeValue());
		}
		return map;
	} */

   /**
	 * Retrieves the text content from the specified XML {@link Node}, replacing any variables
	 * found in the text with their corresponding values from system properties.
	 *
	 * <p>This method first attempts to extract the text node from the given {@code node} using
	 * {@code XmlUtil.getTextNode(node)}. If a text node is found, its value is retrieved and
	 * any variables present in the text are replaced using {@code replaceVariablesFromSystemProperties(text)}.
	 * If the node is {@code null} or contains no text, {@code null} is returned.
	 *
	 * @param node the XML {@link Node} from which to extract text content
	 * @return the processed text content with variables replaced, or {@code null} if no text is found
	 * @throws Exception if an error occurs during text extraction or variable replacement
	 */
	/* 
	 public static String getText(Node node) throws Exception {
		node = XmlUtil.getTextNode(node);
		String text = node == null ? null : node.getNodeValue();
		if (text != null && text.length() > 0) {
			text = replaceVariables(text);
		}
		return text;
	} */
	
	

	/**
	 * Traverse the node hierarchy to create a string representation of the
	 * Node hierarchy, bounded by the given upper bound Node object. The
	 * returned string is a "." separated node name string.
	 * @param node - the starting node (lower bound node).
	 * @param uBound - the upper bound node.
	 * @return the domain hierarchy as a "." separated string.
	 */
	/* 
	 public static String getNodeDomain(Node node, Node uBound) {
		StringBuffer domain = new StringBuffer();
		Node n = node.getParentNode();
		while (n != null && n != uBound) {
			String name = ((Element) n).getAttribute(Constants.C_NAME);
			if (name == null || name.length() == 0) {
				name = n.getNodeName();
			}
			if (domain.length() > 0) {
				domain.insert(0, ".");
			} 
			domain.insert(0, name);
			n = n.getParentNode();
		}
		return domain.toString();
	} */
	
	/* 
	private static ObjectDescriptor toObjectDescriptor(Element element) throws Exception {
		
		ObjectDescriptor map = new ObjectDescriptor();
		
		// process element attributes
		NamedNodeMap attributes = element.getAttributes();
		for (int i = 0; i < attributes.getLength(); i++) {
			Node attribute = attributes.item(i);
			map.put(attribute.getNodeName(), attribute.getNodeValue());
		}
		
		// process element children
		NodeList children = element.getChildNodes();
		
		for (int index = 0; index < children.getLength(); index++) {
			Node child = children.item(index);
			if (child.getNodeType() == Node.ELEMENT_NODE) {
				String childName = child.getNodeName();
				if (Constants.C_ARGUMENTS.equalsIgnoreCase(childName) || Constants.C_PROPERTIES.equalsIgnoreCase(childName)) {
					
					// arguments may contain multiple argument elements
					List<Argument> typedValueList = new LinkedList<Argument>();
							
					NodeList args = child.getChildNodes();
					
					for (int i = 0; i < args.getLength(); i++) {
						Node argNode = args.item(i);
						if (argNode.getNodeType() == Node.ELEMENT_NODE && 
							(Constants.C_ARGUMENT.equalsIgnoreCase(argNode.getNodeName()) || Constants.C_PROPERTY.equalsIgnoreCase(argNode.getNodeName()))) {
							
							// This is an argument element
							Element argElement = (Element) argNode;
							String name = argElement.getAttribute(Constants.C_NAME);
							String type = argElement.getAttribute(Constants.C_TYPE);
							if (type == null || type.length() == 0) {
								type = argElement.getAttribute(Constants.C_CLASS);
							}
							NodeList valueNodes = argElement.getChildNodes();
							List<String> stringValueList = new LinkedList<String>();
							for (int j = 0; j < valueNodes.getLength(); j++) {
								Node valueNode = valueNodes.item(j);
								String value = getText(valueNode);
								if (value != null && value.length() > 0) {
									stringValueList.add(value);
								}
							}
							typedValueList.add(new Argument(name, type, stringValueList));
							
						}
					}
					map.put(Constants.C_ARGUMENTS, 
								typedValueList.toArray(new Argument[typedValueList.size()]));
				}
				else if (Constants.C_DEPENDS.equalsIgnoreCase(childName) || Constants.C_PREREQUISITES.equalsIgnoreCase(childName)) {
					NodeList depends = child.getChildNodes();
					List<String> requiredServices = new LinkedList<String>();
					for (int i = 0; i < depends.getLength(); i++) {
						Node node = depends.item(i);
						if (node.getNodeType() == Node.ELEMENT_NODE && 
							(Constants.C_DEPEND.equalsIgnoreCase(node.getNodeName()) || Constants.C_PREREQUISITE.equalsIgnoreCase(node.getNodeName()))) {
							// This service depends upon other services
							String name = getText(node);
							if (name != null && name.length() > 0) {
								requiredServices.add(name);
							}
				
						}
					}
					map.put(Constants.C_PREREQUISITES, 
						requiredServices.toArray(new String[requiredServices.size()]));
				}
			}
		}
		
		return map;
	} */
	
	/**
	 * Retrieves all XML configuration files in the specified directory that match the given filename filter.
	 *
	 * <p>This method scans the provided directory for files that satisfy the given {@link FilenameFilter}.
	 * For each matching file, an {@link XmlConf} instance is created and added to the result array.
	 * If no files are found, an empty array is returned (never {@code null}).
	 *
	 * @param dir the directory to search for XML configuration files; must not be {@code null}, must exist, and must be a directory
	 * @param filter the filename filter to apply when searching for files
	 * @return an array of {@link XmlConf} objects representing the matching files; never {@code null}
	 * @throws IllegalArgumentException if {@code dir} is {@code null}, does not exist, or is not a directory
	 * @throws Exception if an error occurs while creating {@link XmlConf} instances
	 */
	/* 
	 public static XmlConf[] getXmlConfsInDirectory(
		File dir, FilenameFilter filter) throws Exception {

		if (dir == null || !dir.exists() || !dir.isDirectory()) {
			throw new IllegalArgumentException("Invalid directory: " + dir);
		}

		List<XmlConf> confs = new ArrayList<XmlConf>(); // never return null, always return empty array if no files found

		File[] files = dir.listFiles(filter);
		if (files != null && files.length > 0) {
			for (int i = 0; i < files.length; i++) {
				confs.add(new XmlConf(files[i]));
			}
		}
		
		return confs.toArray(new XmlConf[confs.size()]);

	} */
	
	// Recursively find all values for a given key in a (possibly nested) Map
	public static List<Object> findValuesByKey(Map<String, Object> map, String key) {
		List<Object> results = new ArrayList<>();
		findValuesByKeyHelper(map, key, results);
		return results;
	}

	private static void findValuesByKeyHelper(Map<?, ?> map, String key, List<Object> results) {
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			if (entry.getKey().equals(key)) {
				results.add(entry.getValue());
			}
			Object value = entry.getValue();
			if (value instanceof Map<?, ?>) {
				findValuesByKeyHelper((Map<?, ?>) value, key, results);
			} else if (value instanceof List) {
				for (Object item : (List<?>) value) {
					if (item instanceof Map<?, ?>) {
						findValuesByKeyHelper((Map<?, ?>) item, key, results);
					}
				}
			}
		}
	}

	public final class DataStoreConstants {
		
		public static final String ENTITY = "entity";
		public static final String ATTRIBUTE = "attribute";
		public static final String REFERENCE = "reference";
		public static final String REFERENCED_BY = "referenced-by";
		public static final String STORED_NAME = "stored-name";
		public static final String CHARSET = "charset";
		public static final String AUTO_VALUE = "auto-value";
		public static final String MUTABLE = "mutable";
		public static final String KEY = "key";
		public static final String TYPE = "type";
		public static final String LENGTH = "length";
		public static final String PRECISION = "precision";
		public static final String MULTI_VALUE = "multi-value";
		public static final String PATTERN = "pattern";
		public static final String ENCRYPTED = "encrypted";
		public static final String VALID_VALUES = "valid-values";
		public static final String ALLOW_NULL = "allow-null";
		public static final String DEFAULT_VALUE = "default-value";
	}
}
