package edu.gmu.swe.test.knarr;


import org.junit.Test;

import static org.junit.Assert.*;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;

import edu.gmu.swe.knarr.runtime.Symbolicator;

public class StackOpITCase {
	
	@Test
	public void testSimpleJump() throws Exception {
		int Z = Symbolicator.symbolic("Z",5);
//		int v = CheckpointRollbackAgent.getCurrentVersion();
//		CheckpointRollbackAgent.checkpointAllRoots(false);
		if(Z > 0)
			System.out.println("Z>0");
		else
			System.out.println("Z<=0");
		assertFalse(Symbolicator.dumpConstraints().isEmpty());
//		if(v + 1 == CheckpointRollbackAgent.getCurrentVersion())
//			CheckpointRollbackAgent.rollbackAllRoots(false);
	}
	@Test
	public void testInt() {
		int a;
		int b = 6; // Symbolicator.symbolic("b", 6);
		int c;
		int d;
		int res1, res2, res3, res4, res5, res6, res7, res8, res9, res10, res11;
		
		{
			a = Symbolicator.symbolic("a1", 5);
			c = a + b;
			int res = (res1 = 11);
			d = Symbolicator.symbolic("d1", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("a2", 5);
			c = a - b;
			int res = (res2 = -1);
			d = Symbolicator.symbolic("d2", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("a3", 5);
			c = a * b;
			int res = (res3 = 30);
			d = Symbolicator.symbolic("d3", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("a4", 5);
			c = a / b;
			int res = (res4 = 0);
			d = Symbolicator.symbolic("d4", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("a5", 5);
			c = a % b;
			int res = (res5 = 5);
			d = Symbolicator.symbolic("d5", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("a6", 5);
			c = a >> b;
			int res = (res6 = 0);
			d = Symbolicator.symbolic("d6", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("a7", 5);
			c = a << b;
			int res = (res7 = 320);
			d = Symbolicator.symbolic("d7", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("a8", 5);
			c = a >>> b;
			int res = (res8 = 0);
			d = Symbolicator.symbolic("d8", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("a9", 5);
			c = a | b;
			int res = (res9 = 7);
			d = Symbolicator.symbolic("d9", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("a10", 5);
			c = a & b;
			int res = (res10 = 4);
			d = Symbolicator.symbolic("d10", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("a11", 5);
			c = a ^ b;
			int res = (res11 = 3);
			d = Symbolicator.symbolic("d11", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		ArrayList<SimpleEntry<String, Object>> ret = Symbolicator.dumpConstraints();
		assertFalse(ret.isEmpty());
		for (SimpleEntry<String, Object> e : ret) {
			switch (e.getKey()) {
				case "d1":  assertEquals(e.getValue(), res1);  break;
				case "d2":  assertEquals(e.getValue(), res2);  break;
				case "d3":  assertEquals(e.getValue(), res3);  break;
				case "d4":  assertEquals(e.getValue(), res4);  break;
				case "d5":  assertEquals(e.getValue(), res5);  break;
				case "d6":  assertEquals(e.getValue(), res6);  break;
				case "d7":  assertEquals(e.getValue(), res7);  break;
				case "d8":  assertEquals(e.getValue(), res8);  break;
				case "d9":  assertEquals(e.getValue(), res9);  break;
				case "d10": assertEquals(e.getValue(), res10); break;
				case "d11": assertEquals(e.getValue(), res11); break;
				default:
					throw new Error();
			}
		}
	}

	@Test
	public void testLong() {
		long a = Symbolicator.symbolic("al",5L);
		long b = Symbolicator.symbolic("bl",6L);
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
		assertFalse(Symbolicator.dumpConstraints().isEmpty());

	}

	@Test
	public void testFloat() {
		float a = Symbolicator.symbolic("af", 5f);
		float b = Symbolicator.symbolic("bf", 6f);
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
		assertFalse(Symbolicator.dumpConstraints().isEmpty());

	}

	@Test
	public void testDouble() {
		double a = Symbolicator.symbolic("ad", 2d);
		double b = Symbolicator.symbolic("bd", 4d);
		double c = a + b;
		assertEquals(6, c, 0);
		c = a - b;
		assertEquals(-2, c, 0);
		c = a * b;
		assertEquals(8, c, 0);
		c = a / b;
		assertEquals(0.5, c, 0);
		c = a % b;
		assertEquals(2, c, 0);
		assertFalse(Symbolicator.dumpConstraints().isEmpty());

	}
}
