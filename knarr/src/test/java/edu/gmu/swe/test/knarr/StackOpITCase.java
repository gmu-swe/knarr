package edu.gmu.swe.test.knarr;


import org.junit.Test;

import static org.junit.Assert.*;
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
		Symbolicator.dumpConstraints();
//		if(v + 1 == CheckpointRollbackAgent.getCurrentVersion())
//			CheckpointRollbackAgent.rollbackAllRoots(false);
	}
	@Test
	public void testInt() {
		int a = Symbolicator.symbolic("a", 5);
		int b = Symbolicator.symbolic("b", 6);
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
		Symbolicator.dumpConstraints();
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
		Symbolicator.dumpConstraints();

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
		Symbolicator.dumpConstraints();

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
		Symbolicator.dumpConstraints();

	}
	@Test
	public void testArrayIndex(){
		int idx = Symbolicator.symbolic("Aindex",5);
		int[] ar = new int[100];
		ar[5] = 10;
		assertEquals(10,ar[idx]);
		Symbolicator.dumpConstraints();

	}
}
