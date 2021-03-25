# Chat with RabbitMQ

## How to use the chat

A few tools are provided to help compiling, cleaning and program running.

You can compile the project with ./compile.sh and clean the classes with ./clean.sh
Pease use ./run\_client.sh (and its counterpart run\_server) to run the programs.

You can launch as many clients as you want, and you can run between 0 and 1 server. The server is not mandatory if you don't plan to use the enhanced features.
Please note that running the server after the clients tried to execute commands will process all of the outputs in order.

## Included commands
### Chat client

/history [nbMessages] - Gets the last nbMessages sent
/history all - Gets all the messages sent since the beginning
/users - Returns the list of logged in users
/close - Closes the client (accessible even if the back-end isn't running)

### Chat Server

/say [message] - Broadcasts message to all clients
/kick [user] [reason] - Kicks the user with a reason
/kickall [reason] - Kicks all users with a reason
/close - Kicks all users and closes the server
