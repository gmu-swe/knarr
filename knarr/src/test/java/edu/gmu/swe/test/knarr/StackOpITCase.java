package edu.gmu.swe.test.knarr;

import org.junit.Test;
import static org.junit.Assert.*;
import edu.gmu.swe.knarr.runtime.Symbolicator;

public class StackOpITCase {
	@Test
	public void testInt() {
		int a = Symbolicator.symbolic(5);
		int b = Symbolicator.symbolic(6);
		int c = a + b;
		assertEquals(11, c);
		c = a - b;
		assertEquals(-1, c);
		c = a * b;
		assertEquals(30, c);
		c = a / b;
		assertEquals(0, c);
		c = a % b;
		assertEquals(5, c);
		c = a >> b;
		assertEquals(0, c);
		c = a << b;
		assertEquals(320, c);
		c = a >>> b;
		assertEquals(0, c);
		c = a | b;
		assertEquals(7, c);
		c = a & b;
		assertEquals(4, c);
		c = a ^ b;
		assertEquals(3, c);
	}
public static void main(String[] args) {
	System.out.println(5d/6d);
}
	@Test
	public void testLong() {
		long a = Symbolicator.symbolic(5);
		long b = Symbolicator.symbolic(6);
		long c = a + b;
		assertEquals(11, c);
		c = a - b;
		assertEquals(-1, c);
		c = a * b;
		assertEquals(30, c);
		c = a / b;
		assertEquals(0, c);
		c = a % b;
		assertEquals(5, c);
		c = a >> b;
		assertEquals(0, c);
		c = a << b;
		assertEquals(320, c);
		c = a >>> b;
		assertEquals(0, c);
		c = a | b;
		assertEquals(7, c);
		c = a & b;
		assertEquals(4, c);
		c = a ^ b;
		assertEquals(3, c);
	}

	@Test
	public void testFloat() {
		float a = Symbolicator.symbolic(5);
		float b = Symbolicator.symbolic(6);
		float c = a + b;
		assertEquals(11, c, 0);
		c = a - b;
		assertEquals(-1, c, 0);
		c = a * b;
		assertEquals(30, c, 0);
		c = a / b;
		assertEquals(0.8333333, c, 0.0001);
		c = a % b;
		assertEquals(5, c, 0);
	}

	@Test
	public void testDouble() {
		double a = Symbolicator.symbolic(5);
		double b = Symbolicator.symbolic(6);
		double c = a + b;
		assertEquals(11, c, 0);
		c = a - b;
		assertEquals(-1, c, 0);
		c = a * b;
		assertEquals(30, c, 0);
		c = a / b;
		assertEquals(0.8333333333333334, c, 0.0001);
		c = a % b;
		assertEquals(5, c, 0);
	}
}
