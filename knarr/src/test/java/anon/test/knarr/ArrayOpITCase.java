package anon.test.knarr;

import static org.junit.Assert.*;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import anon.knarr.runtime.Symbolicator;

public class ArrayOpITCase {

	@Test
	public void testArrayCopy() throws Exception {
		byte tainted[] = new byte[1];
		byte source[]  = new byte[1];

		tainted[0] = Symbolicator.symbolic("a2", (byte)'a');
		source[0]  = 'b';

		assertEquals(tainted[0], 'a');

		System.arraycopy(source, 0, tainted, 0, 1);

		assertNotEquals(tainted[0], 'a');

		assertFalse(Symbolicator.dumpConstraints().isEmpty());
	}
	@Test
	public void testArrayWriteOnConstantIndex() throws Exception {
		byte tainted[] = new byte[1];

		tainted[0] = Symbolicator.symbolic("a", (byte)'a');

		if (tainted[0] == 'a')
			tainted[0] = 'b';

		assertNotEquals(tainted[0], 'a');

		assertFalse(Symbolicator.dumpConstraints().isEmpty());
	}
	@Test
	public void testArrayWriteOnVariableIndex() throws Exception {
		byte tainted[] = new byte[1];

		tainted[0] = Symbolicator.symbolic("a1", (byte)'a');

		for (int i = 0 ; i < tainted.length ; i++)
			tainted[i] = (byte) (((int)tainted[i]) ^ ((int)'b'));

		if (tainted[0] != (((int)'a') ^ ((int)'b')))
			throw new Error();

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints();
		assertFalse(solution.isEmpty());
		for (SimpleEntry<String, Object> e : solution) {
			switch (e.getKey()) {
				case "a1":
					assertNotEquals('a', e.getValue());
					assertNotEquals('b', e.getValue());
					break;
				default:
					// Do nothing
					break;
			}
		}
	}
	@Test
	public void testArrayWriteTaintedIdx() throws Exception {
		byte arr[] = new byte[2];

		arr[0] = 'a';
		arr[1] = 'b';

		int taintedIdx = Symbolicator.symbolic("idx", 0);

		if (arr[taintedIdx] == 'a')
			arr[taintedIdx] = 'b';

		assertNotEquals(arr[taintedIdx], 'a');

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints();
		assertFalse(solution.isEmpty());
		for (SimpleEntry<String, Object> e : solution) {
			switch (e.getKey()) {
				case "index":  assertEquals((int)0 , e.getValue());  break;
				default:
					// Do nothing
					break;
			}
		}
	}

	//TODO save negative char,byte,short in array and read it

	@Test
	public void testArrayIndex(){
		int idx = Symbolicator.symbolic("Aindex",5);
		int[] ar = new int[100];
		ar[5] = 10;
		assertEquals(10,ar[idx]);
		assertFalse(Symbolicator.dumpConstraints().isEmpty());

	}

	@Test
	public void testArrayReadSwitch() {
		String test = "<b>\"test&go\"</b>";

		byte[] tainted = new byte[test.length()];

		for (int i = 0 ; i < tainted.length ; i++)
			tainted[i] = Symbolicator.symbolic("switch_" + i, (byte)test.charAt(i));

		StringBuffer result = new StringBuffer();

		for (int i = 0 ; i < tainted.length ; i++) {
			switch (tainted[i]) {
			case '<':
				result.append("&lt;");
				break;
			case '>':
				result.append("&gt;");
				break;
			case '&':
				result.append("&amp;");
				break;
			case '"':
				result.append("&quot;");
				break;
			default:
				result.append(tainted[i]);
			}
		}

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints();

		assertNotNull(solution);
		assertFalse(solution.isEmpty());

		Pattern p = Pattern.compile("switch_(\\d+)");

		for (SimpleEntry<String, Object> e : solution) {
			Matcher m = p.matcher(e.getKey());

			if (!m.matches())
				continue;

			int i = Integer.parseInt(m.group(1));

			char c = test.charAt(i);
			if (!Character.isLetter(c) && c != '/')
				assertEquals(c, (char) (int) e.getValue());
		}
	}

	@Test
	public void testArrayReadTableSwitch() {
		int[] tainted = new int[20];

		for (int i = 0 ; i < tainted.length ; i++)
			tainted[i] = Symbolicator.symbolic("tableSwitch_" + i, 1000 + i);

		StringBuffer result = new StringBuffer();

		for (int i = 0 ; i < tainted.length ; i++) {
			switch (tainted[i]) {
			case 1000:
			case 1001:
			case 1002:
			case 1003:
				result.append(0);
				break;
			case 1004:
			case 1005:
			case 1006:
			case 1007:
				result.append(8);
				break;
			default:
				result.append(tainted[i]);
				break;
			}
		}

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints();

		assertNotNull(solution);
		assertTrue(solution.size() >= tainted.length);

		Pattern p = Pattern.compile("tableSwitch_(\\d+)");

		for (SimpleEntry<String, Object> e : solution) {
			Matcher m = p.matcher(e.getKey());

			if (!m.matches())
				continue;

			int i = Integer.parseInt(m.group(1));

			if (i < 8) {
				assertEquals(i + 1000, (int) e.getValue());
			} else {
				int val = (int) e.getValue();
				assertFalse(val > 1000 && val < 1008);
			}
		}
	}

	public void testNullArray() {
		SimpleEntry<String, String> untaintedTable[] = new SimpleEntry[10];
		untaintedTable[4] = new SimpleEntry<>("one", "two");

		int taintedHash = Symbolicator.symbolic("taintedHash", (int)3);

		SimpleEntry<String, String> node = untaintedTable[taintedHash];

		String value;
		if (node == null)
			value = null;
		else
			value = node.getValue();

		assertNotEquals("two", value);

		ArrayList<SimpleEntry<String, Object>> solution = Symbolicator.dumpConstraints();

		assertNotNull(solution);
	}

	public static void main(String[] args) throws Exception {
		new ArrayOpITCase().testArrayWriteOnConstantIndex();
	}
}
