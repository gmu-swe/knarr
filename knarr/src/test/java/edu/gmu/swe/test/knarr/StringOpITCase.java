package edu.gmu.swe.test.knarr;

import static org.junit.Assert.*;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;

import org.junit.Test;

import edu.gmu.swe.knarr.runtime.Symbolicator;

public class StringOpITCase {

	@Test
	public void testStartsWith() throws Exception {
		String test = "This is a test";
		char tainted[] = new char[test.length()];
		
		for (int i = 0 ; i < tainted.length ; i++)
			tainted[i] = Symbolicator.symbolic("startsWithVar_" + i, test.charAt(i));
		
		String taintedString = new String(tainted, 0, tainted.length);
		
		assertTrue(taintedString.startsWith("Th"));
		assertFalse(taintedString.startsWith("That"));
		assertFalse(taintedString.startsWith("Those"));

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints();
		System.out.println(solution);
		
		assertTrue(solution != null && !solution.isEmpty());
		
		for (SimpleEntry<String, Object> e : solution) {
			switch (e.getKey()) {
				case "startsWithVar_0": assertEquals('T', (char) (int) e.getValue()); break;
				case "startsWithVar_1": assertEquals('h', (char) (int) e.getValue()); break;
				case "startsWithVar_2": assertNotEquals('i', (char) (int) e.getValue()); break;
				case "startsWithVar_3": assertNotEquals('s', (char) (int) e.getValue()); break;
				case "startsWithVar_4": assertNotEquals(' ', (char) (int) e.getValue()); break;
			}
		}
	}

//	@Test
	public void testStringOps() throws Exception {
		String MyStr = Symbolicator.symbolicString("MyStr", new String("MyStr"));
		assertEquals("MyStr", MyStr);
		assertEquals("MyStrZ", MyStr.concat("Z"));
		assertEquals('M', MyStr.charAt(0));
		Symbolicator.dumpConstraints();
	}
	
	@Test
	public void testReferenceEq() throws Exception {
		
	}
	public static void main(String[] args) throws Exception {
		new StringOpITCase().testStringOps();
	}
}
