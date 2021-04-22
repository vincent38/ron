#! /bin/bash
# Compiles the whole project

source ./clean.sh

mkdir -p "classes"

export CP=.:./classes:./lib/amqp-client-5.8.0.jar:./lib/slf4j-api-1.7.30.jar:./lib/slf4j-simple-1.7.30.jar

javac -d classes -cp $CP src/Node.java