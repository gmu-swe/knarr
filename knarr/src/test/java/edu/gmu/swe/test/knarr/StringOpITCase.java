package edu.gmu.swe.test.knarr;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.gmu.swe.knarr.runtime.Symbolicator;

public class StringOpITCase {

	@Test
	public void testStringOps() throws Exception {
		String MyStr = Symbolicator.symbolicString("MyStr", new String("MyStr"));
		assertEquals("MyStr", MyStr);
		assertEquals("MyStrZ", MyStr.concat("Z"));
		Symbolicator.dumpConstraints();
	}
	
	@Test
	public void testReferenceEq() throws Exception {
		
	}
	public static void main(String[] args) throws Exception {
		new StringOpITCase().testStringOps();
	}
}
