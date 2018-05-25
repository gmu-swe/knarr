#!/bin/sh

if [ -z "$JAVA_HOME" ]; then
    echo "Error: Please set \$JAVA_HOME";
    exit 1
fi

echo $1
echo $2

KNARR_DIR=$1
KNARR_SERVER_JAR=`find $KNARR_DIR/knarr-server/target -maxdepth 1 -type f -name "Knarr-Server-*-SNAPSHOT.jar"`
Z3_DIR=$2

export LD_LIBRARY_PATH=$Z3_DIR/bin
exec $JAVA_HOME/bin/java -Djava.library.path=$Z3_DIR/bin -Xmx10G -Xss1G -jar $KNARR_SERVER_JAR
