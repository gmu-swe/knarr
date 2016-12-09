import edu.gmu.swe.knarr.runtime.PathUtils;
import edu.gmu.swe.knarr.runtime.Symbolicator;

public class Foo {
	static void testInt(){
		int a = Symbolicator.symbolic(5);
		int b = Symbolicator.symbolic(6);
		int c = a + b;
		c = a - b;
		c = a * b;
		c = a / b;
		c = a%b;
		c = a>> b;
		c = a << b;
		c = a >>> b;
		System.out.println(c);
	}

	static void testLong() {
		long a = Symbolicator.symbolic(5);
		long b = Symbolicator.symbolic(6);
		long c = a + b;
		c = a - b;
		c = a * b;
		c = a / b;
		c = a % b;
		c = a >> b;
		c = a << b;
		c = a >>> b;
	}
	static void testFloat(){
		float a = Symbolicator.symbolic(5);
		float b = Symbolicator.symbolic(6);
		float c = a + b;
		c = a - b;
		c = a * b;
		c = a / b;
		c = a%b;
	}
	static void testDouble(){
		double a = Symbolicator.symbolic(5);
		double b = Symbolicator.symbolic(6);
		double c = a + b;
		c = a - b;
		c = a * b;
		c = a / b;
		c = a%b;
	}
	public static void main(String[] args) {
		int i = 0;
		i = Symbolicator.symbolic("myInt", 1);
		int j= i + 10;
		if(i > 0)
			System.out.println(i);
		if(j < 400)
			System.out.println("j<400");
		testInt();
		testDouble();
		testFloat();
		testLong();
		System.out.println(Symbolicator.getExpression(i));
		Symbolicator.dumpConstraints();
	}
}
