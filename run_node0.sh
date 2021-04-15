export CP=.:./classes:./lib/amqp-client-5.8.0.jar:./lib/slf4j-api-1.7.30.jar:./lib/slf4j-simple-1.7.30.jar
server=localhost
if [ $1 ]; then
    server=$1
fi
export CHAT_SERVER_ADDR=$server
java -cp $CP Node data/basictopology.conf 0 $CHAT_SERVER_ADDR