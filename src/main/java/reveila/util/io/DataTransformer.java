/*
 * Created on Jun 22, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package reveila.util.io;

/**
 * @author Charles Lee
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public interface DataTransformer {

	public Object[] pack(Object[] args) throws Exception;
	public Object[] unpack(Object[] args) throws Exception;

}
