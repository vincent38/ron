export CP=.:./classes:./lib/amqp-client-5.8.0.jar:./lib/slf4j-api-1.7.30.jar:./lib/slf4j-simple-1.7.30.jar
export CHAT_SERVER_ADDR=localhost
java -cp $CP Node data/basictopology.conf 3 $CHAT_SERVER_ADDR