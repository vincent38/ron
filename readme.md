# Ring over Network

A small ring network simulator over a (even more simulated) physical network.
Authors : Vincent AUBRIOT and Folajimi OLANIYAN

## Compile the project

Use the `./compile.sh` script to compile the application.

## Clean the project

Use the `./clean.sh` script to clean the application.

## Run the project

Use the following command: 
```
./run_node.sh id [topology] [rabbitmq_server]
```

The id parameter gives a physical identifier of your choice to the node.
Please use a unique identifier between 0 and the number of nodes in the chosen topology.

As of now, there is no id verification to check for duplicates.
Please avoid giving the same identifier to two nodes, as this may cause problems regarding the routing of the messages.

The topologies and the address of the RabbitMQ server may also be provided when starting.
By default, the node loads a basic 5-node topology with few physical connections, and uses a local RabbitMQ server.

A few topologies are provided in the data folder. You can use them to test the application.
