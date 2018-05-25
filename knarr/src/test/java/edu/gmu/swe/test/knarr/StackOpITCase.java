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
				case "d1":  assertEquals(res1 , e.getValue());  break;
				case "d2":  assertEquals(res2 , e.getValue());  break;
				case "d3":  assertEquals(res3 , e.getValue());  break;
				case "d4":  assertEquals(res4 , e.getValue());  break;
				case "d5":  assertEquals(res5 , e.getValue());  break;
				case "d6":  assertEquals(res6 , e.getValue());  break;
				case "d7":  assertEquals(res7 , e.getValue());  break;
				case "d8":  assertEquals(res8 , e.getValue());  break;
				case "d9":  assertEquals(res9 , e.getValue());  break;
				case "d10": assertEquals(res10, e.getValue()); break;
				case "d11": assertEquals(res11, e.getValue()); break;
				default:
					// Do nothing
					break;
			}
		}
	}

	@Test
	public void testLong() {
		long a;
		long b = 6;
		long c;
		long d;
		long res1, res2, res3, res4, res5, res6, res7, res8, res9, res10, res11;
		
		{
			a = Symbolicator.symbolic("al1", 5);
			c = a + b;
			long res = (res1 = 11);
			d = Symbolicator.symbolic("dl1", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("al2", 5);
			c = a - b;
			long res = (res2 = -1);
			d = Symbolicator.symbolic("dl2", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("al3", 5);
			c = a * b;
			long res = (res3 = 30);
			d = Symbolicator.symbolic("dl3", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("al4", 5);
			c = a / b;
			long res = (res4 = 0);
			d = Symbolicator.symbolic("dl4", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("al5", 5);
			c = a % b;
			long res = (res5 = 5);
			d = Symbolicator.symbolic("dl5", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("al6", 5);
			c = a >> b;
			long res = (res6 = 0);
			d = Symbolicator.symbolic("dl6", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("al7", 5);
			c = a << b;
			long res = (res7 = 320);
			d = Symbolicator.symbolic("dl7", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("al8", 5);
			c = a >>> b;
			long res = (res8 = 0);
			d = Symbolicator.symbolic("dl8", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("al9", 5);
			c = a | b;
			long res = (res9 = 7);
			d = Symbolicator.symbolic("dl9", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("al10", 5);
			c = a & b;
			long res = (res10 = 4);
			d = Symbolicator.symbolic("dl10", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		{
			a = Symbolicator.symbolic("al11", 5);
			c = a ^ b;
			long res = (res11 = 3);
			d = Symbolicator.symbolic("dl11", res);
			assertEquals(c, res);
			assertEquals(c, d);
		}
		ArrayList<SimpleEntry<String, Object>> ret = Symbolicator.dumpConstraints();
		assertFalse(ret.isEmpty());
		for (SimpleEntry<String, Object> e : ret) {
			switch (e.getKey()) {
				case "dl1":  assertEquals(res1 , e.getValue());  break;
				case "dl2":  assertEquals(res2 , e.getValue());  break;
				case "dl3":  assertEquals(res3 , e.getValue());  break;
				case "dl4":  assertEquals(res4 , e.getValue());  break;
				case "dl5":  assertEquals(res5 , e.getValue());  break;
				case "dl6":  assertEquals(res6 , e.getValue());  break;
				case "dl7":  assertEquals(res7 , e.getValue());  break;
				case "dl8":  assertEquals(res8 , e.getValue());  break;
				case "dl9":  assertEquals(res9 , e.getValue());  break;
				case "dl10": assertEquals(res10, e.getValue()); break;
				case "dl11": assertEquals(res11, e.getValue()); break;
				default:
					// Do nothing
					break;
			}
		}

	}

	@Test
	public void testFloat() {
		float a;
		float b = 6.0f;
		float c;
		float d;
		float res1, res2, res3, res4, res5;
		
		{
			a = Symbolicator.symbolic("af1", 5.0f);
			c = a + b;
			float res = (res1 = 11.0f);
			d = Symbolicator.symbolic("df1", res);
			assertEquals(c, res, 0);
			assertEquals(c, d, 0);
		}
		{
			a = Symbolicator.symbolic("af2", 5.0f);
			c = a - b;
			float res = (res2 = -1.0f);
			d = Symbolicator.symbolic("df2", res);
			assertEquals(c, res, 0);
			assertEquals(c, d, 0);
		}
		{
			a = Symbolicator.symbolic("af3", 5.0f);
			c = a * b;
			float res = (res3 = 30.0f);
			d = Symbolicator.symbolic("df3", res);
			assertEquals(c, res, 0);
			assertEquals(c, d, 0);
		}
		{
			a = Symbolicator.symbolic("af4", 5.0f);
			c = a / b;
			float res = (res4 = 0.8333333f);
			d = Symbolicator.symbolic("df4", res);
			assertEquals(c, res, 0.0001);
			assertEquals(c, d, 0.0001);
		}
		{
			a = Symbolicator.symbolic("af5", 5.0f);
			c = a % b;
			float res = (res5 = 5.0f);
			d = Symbolicator.symbolic("df5", res);
			assertEquals(c, res, 0);
			assertEquals(c, d, 0);
		}
		ArrayList<SimpleEntry<String, Object>> ret = Symbolicator.dumpConstraints();
		assertFalse(ret.isEmpty());
		for (SimpleEntry<String, Object> e : ret) {
			switch (e.getKey()) {
				case "df1":  assertEquals((double)res1 , e.getValue());  break;
				case "df2":  assertEquals((double)res2 , e.getValue());  break;
				case "df3":  assertEquals((double)res3 , e.getValue());  break;
				case "df4":  assertEquals((double)res4 , e.getValue());  break;
				case "df5":  assertEquals((double)res5 , e.getValue());  break;
				default:
					// Do nothing
					break;
			}
		}
	}

	@Test
	public void testDouble() {
		double a;
		double b = 4.0d;
		double c;
		double d;
		double res1, res2, res3, res4, res5;
		
		{
			a = Symbolicator.symbolic("ad1", 2.0d);
			c = a + b;
			double res = (res1 = 6.0d);
			d = Symbolicator.symbolic("dd1", res);
			assertEquals(c, res, 0);
			assertEquals(c, d, 0);
		}
		{
			a = Symbolicator.symbolic("ad2", 2.0d);
			c = a - b;
			double res = (res2 = -2.0d);
			d = Symbolicator.symbolic("dd2", res);
			assertEquals(c, res, 0);
			assertEquals(c, d, 0);
		}
		{
			a = Symbolicator.symbolic("ad3", 2.0d);
			c = a * b;
			double res = (res3 = 8.0d);
			d = Symbolicator.symbolic("dd3", res);
			assertEquals(c, res, 0);
			assertEquals(c, d, 0);
		}
		{
			a = Symbolicator.symbolic("ad4", 2.0d);
			c = a / b;
			double res = (res4 = 0.5d);
			d = Symbolicator.symbolic("dd4", res);
			assertEquals(c, res, 0);
			assertEquals(c, d, 0);
		}
		{
			a = Symbolicator.symbolic("ad5", 2.0d);
			c = a % b;
			double res = (res5 = 2.0d);
			d = Symbolicator.symbolic("dd5", res);
			assertEquals(c, res, 0);
			assertEquals(c, d, 0);
		}
		ArrayList<SimpleEntry<String, Object>> ret = Symbolicator.dumpConstraints();
		assertFalse(ret.isEmpty());
		for (SimpleEntry<String, Object> e : ret) {
			switch (e.getKey()) {
				case "dd1":  assertEquals((double)res1 , e.getValue());  break;
				case "dd2":  assertEquals((double)res2 , e.getValue());  break;
				case "dd3":  assertEquals((double)res3 , e.getValue());  break;
				case "dd4":  assertEquals((double)res4 , e.getValue());  break;
				case "dd5":  assertEquals((double)res5 , e.getValue());  break;
				default:
					// Do nothing
					break;
			}
		}
	}
}
