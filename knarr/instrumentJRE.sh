#!/bin/sh
if [ -z "$INST_HOME" ]; then
	INST_HOME=$JAVA_HOME;
fi
if [ -z "$JAVA_HOME" ]; then
	echo "Error: Please set \$JAVA_HOME";
else
	echo "Ensuring instrumented JREs exist for tests... to refresh, do mvn clean\n";
	if [ ! -d "target/jre-inst" ]; then
		echo "Creating instrumented JRE\n";
		java -DspecialStrings -Xmx6g -jar target/Knarr-0.0.2-SNAPSHOT.jar $INST_HOME target/jre-inst;
		chmod +x target/jre-inst/jre/bin/*;
		chmod +x target/jre-inst/jre/lib/*;
		chmod +x target/jre-inst/bin/*;
		chmod +x target/jre-inst/lib/*;
	else
		echo "Not regenerating JRE\n";
	fi
fi
