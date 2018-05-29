package edu.gmu.swe.test.knarr;

import static org.junit.Assert.*;

import org.junit.Test;

import edu.gmu.swe.knarr.runtime.Symbolicator;

public class ArrayOpITCase {

	@Test
	public void testArrayWrite() throws Exception {
		byte tainted[] = new byte[1];

		tainted[0] = Symbolicator.symbolic("a", (byte)'a');
		
		if (tainted[0] == 'a')
			tainted[0] = 'b';

		assertNotEquals(tainted[0], 'a');

		assertFalse(Symbolicator.dumpConstraints().isEmpty());
	}
//	@Test
	public void testArrayWriteTaintedIdx() throws Exception {
		byte arr[] = new byte[2];

		arr[0] = 'a';
		arr[1] = 'b';
		
		int taintedIdx = Symbolicator.symbolic("idx", 0);
		
		if (arr[taintedIdx] == 'a')
			arr[taintedIdx] = 'b';

		assertNotEquals(arr[taintedIdx], 'a');

		assertFalse(Symbolicator.dumpConstraints().isEmpty());
	}
	@Test
	public void testArrayIndex(){
		int idx = Symbolicator.symbolic("Aindex",5);
		int[] ar = new int[100];
		ar[5] = 10;
		assertEquals(10,ar[idx]);
		assertFalse(Symbolicator.dumpConstraints().isEmpty());

	}
	
	public static void main(String[] args) throws Exception {
		new ArrayOpITCase().testArrayWrite();
	}
}
