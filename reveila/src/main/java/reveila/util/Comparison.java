/*
 * Created on Jun 11, 2003
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package reveila.util;

/**
 * @author Charles Lee
 *
 * This abstract class defines the basic properties and operations of a comparison
 * object, which provides a standard way to compare two objects for "satisfaction",
 * inluding but not limited to equalization.
 */
public abstract class Comparison {
	
	/**
	 * Constant value representing comparison rule "equal".
	 */
	public static final int EQ = 1;
	
	/**
	 * Constant value representing comparison rule "not equal".
	 */
	public static final int NE = 2;
	
	/**
	 * Constant value representing comparison rule "greater than or equal to".
	 */
	public static final int GE = 3;
	
	/**
	 * Constant value representing comparison rule "less than or equal to".
	 */
	public static final int LE = 4;
	
	/**
	 * Constant value representing comparison rule "greater than".
	 */
	public static final int GT = 5;
	
	/**
	 * Constant value representing comparison rule "less than".
	 */
	public static final int LT = 6;
	
	/**
	 * Constant value representing comparison rule "contain".
	 */
	public static final int CT = 7;
	
	/**
	 * Constant value representing comparison rule "begin with".
	 */
	public static final int BW = 8;
	/**
	 * Constant value representing comparison rule "end with".
	 */
	public static final int EW = 9;
	
	/**
	 * Array containing all the valid constant values defined for comparison rules.
	 */
	public static final int[] validRules = {EQ, NE, GE, LE, GT, LT, CT, BW, EW};
	
	/**
	 * Protected field indicating the comparison rule of this Comparison instance.
	 */
	protected int rule = EQ;
	
	/**
	 * Compares two objects for satisfaction.
	 * @param target the object to be evaluated
	 * @param sample the object used for comparison
	 * @return true if satisfactory based on the comparison rule, otherwise false
	 */
	public abstract boolean compare(Object target, Object sample);
	
	/**
	 * Returns the constant int value of the comparison rule.
	 * @return comparison rule
	 */
	public int getRule() {
		return rule;
	}
	
	/**
	 * Sets the comparison rule.
	 * @param newRule new rule value
	 */
	public void setRule(int newRule) {
		rule = newRule;
	}

}
