package edu.gmu.swe.test.knarr;

import static org.junit.Assert.*;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import edu.gmu.swe.knarr.runtime.StringUtils;
import edu.gmu.swe.knarr.runtime.Symbolicator;

@Ignore
public class StringOpITCase {
	
	private static boolean isStringUtilsEnabled;
	
	@BeforeClass
	public static void setup() {
		isStringUtilsEnabled = StringUtils.enabled;
		StringUtils.enabled = true;
	}
	
	@AfterClass
	public static void teardown() {
		StringUtils.enabled = isStringUtilsEnabled;
	}
	
	@Test
	public void testLength() throws Exception {
		String test = "This is a test";
		char tainted[] = new char[test.length()];
		
		for (int i = 0 ; i < tainted.length ; i++)
			tainted[i] = Symbolicator.symbolic("length_" + i, test.charAt(i));

		String taintedString = new String(tainted, 0, tainted.length);
		
		for (int i = 0 ; i < taintedString.length() ; i++) {
			assertNotEquals(20, i);
		}

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints("lol");
		
		assertTrue(solution != null && !solution.isEmpty());
	}

	public void testEndsWidth() throws Exception {
	}

	public void testSubstring() throws Exception {
	}
	
	@Test
	public void testCharAt() throws Exception {
		String test = "This is a test";
		char tainted[] = new char[test.length()];
		
		for (int i = 0 ; i < tainted.length ; i++)
			tainted[i] = Symbolicator.symbolic("charAt_" + i, test.charAt(i));

		String taintedString = new String(tainted, 0, tainted.length);
		
		char c0 = taintedString.charAt(0);
		char cN = taintedString.charAt(4);

		assertTrue( (c0 <= 'Z' && c0 >= 'A') || (c0 <= 'z' && c0 >= 'a'));
		assertFalse((cN <= 'Z' && cN >= 'A') || (cN <= 'z' && cN >= 'a'));

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints();
		
		assertTrue(solution != null && !solution.isEmpty());
		
		for (SimpleEntry<String, Object> e : solution) {
			char c = (char) (int) e.getValue();
			switch (e.getKey()) {
				case "charAt_0": 
					assertTrue( (c <= 'Z' && c >= 'A') || (c <= 'z' && c >= 'a'));
					break;
				case "charAt_4": 
					assertFalse( (c <= 'Z' && c >= 'A') || (c <= 'z' && c >= 'a'));
					break;
			}
		}
	}

	@Test
	public void testTaintedEquals() throws Exception {
		String test = "This is a test";
		char tainted[] = new char[test.length()];
		
		for (int i = 0 ; i < tainted.length ; i++)
			tainted[i] = Symbolicator.symbolic("equals_" + i, test.charAt(i));

		String taintedString = new String(tainted, 0, tainted.length);
		
		String same    = new String(test);
		String notSame = "This is not a test";

		assertEquals(taintedString, same);
		assertNotEquals(taintedString, notSame);

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints();
		
		assertTrue(solution != null && !solution.isEmpty());
		
		Pattern p = Pattern.compile("equals_([0-9][0-9]*)");

		for (SimpleEntry<String, Object> e : solution) {
			Matcher m = p.matcher(e.getKey());
			if (m.matches()) {
				int i = Integer.parseInt(m.group(1));
				assertEquals(test.charAt(i), (char) (int) e.getValue());
			}
		}
	}

	@Test
	public void testEqualsTainted() throws Exception {
		String test = "This is a test";
		char tainted[] = new char[test.length()];
		
		for (int i = 0 ; i < tainted.length ; i++)
			tainted[i] = Symbolicator.symbolic("equalsT_" + i, test.charAt(i));

		String taintedString = new String(tainted, 0, tainted.length);
		
		String same    = new String(test);
		String notSame = "This is not a test";

		assertEquals(same, taintedString);
		assertNotEquals(notSame, taintedString);

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints();
		
		assertTrue(solution != null && !solution.isEmpty());
		
		Pattern p = Pattern.compile("equalsT_([0-9][0-9]*)");

		for (SimpleEntry<String, Object> e : solution) {
			Matcher m = p.matcher(e.getKey());
			if (m.matches()) {
				int i = Integer.parseInt(m.group(1));
				assertEquals(test.charAt(i), (char) (int) e.getValue());
			}
		}
	}

	@Test
	public void testToLowerUpper() throws Exception {
		String test = "This is a test";
		char tainted[] = new char[test.length()];
		
		for (int i = 0 ; i < tainted.length ; i++)
			tainted[i] = Symbolicator.symbolic("toUpperLowerVar_" + i, test.charAt(i));

		String taintedString = new String(tainted, 0, tainted.length);
		
		String upper = taintedString.toUpperCase();
		String lower = taintedString.toLowerCase();
		
		assertEquals(test.toUpperCase(), upper);
		assertEquals(test.toLowerCase(), lower);

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints();
		System.out.println(solution);

		assertTrue(solution != null && !solution.isEmpty());

		Pattern p = Pattern.compile("toUpperLowerVar_([0-9][0-9]*)");

		for (SimpleEntry<String, Object> e : solution) {
			Matcher m = p.matcher(e.getKey());
			if (m.matches()) {
				int i = Integer.parseInt(m.group(1));
				assertEquals(test.toLowerCase().charAt(i), Character.toLowerCase((char) (int) e.getValue()));
			}
		}
	}
		

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
	
//	@Test
	public void testReferenceEq() throws Exception {
		
	}
	public static void main(String[] args) throws Exception {
		new StringOpITCase().testStringOps();
	}
}
