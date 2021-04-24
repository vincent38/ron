#!/bin/bash
# Runs the node application with the passed parameters
export CP=.:./classes:./lib/amqp-client-5.8.0.jar:./lib/slf4j-api-1.7.30.jar:./lib/slf4j-simple-1.7.30.jar

topology=data/basictopology.conf
id=0
server=localhost

if [ $# -lt 1 ]; then
    echo "Usage : ./run_node.sh id [topology] [rabbitmq_server]"
    exit 1
fi

if [ $1 ]; then
    id=$1
fi

if [ $2 ]; then
    topology=$2
fi

if [ $3 ]; then
    server=$3
fi

export NODE_PHYSICAL_NET_TOPOLOGY=$topology
export NODE_ID=$id
export PHYSICAL_NET_RABBIT_HANDLER=$server

java -cp $CP Node $NODE_PHYSICAL_NET_TOPOLOGY $NODE_ID $PHYSICAL_NET_RABBIT_HANDLER