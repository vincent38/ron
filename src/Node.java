//Imports
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Scanner;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

public class Node {

    /**
     * The main class for the ring topology over a physical network
     */

    // Global variables for the node
    static int physicalId;
    static int virtualId;
    static ArrayList<Neighbor> neighbors;
    static Channel channel;

    // Variables for ring initialization
    static boolean hang;
    static int token;
    static int fromOrigin;
    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Please provide the network configuration, the physical id of this node as well as the RabbitMQ server address to join in order to start the program.");
            System.out.println("Usage : node [network.conf] [node_physical_id]");
            System.exit(1);
        }

        // Define the ids
        physicalId = Integer.parseInt(args[1]);
        virtualId = -1;

        System.out.println("HELLO, MY PHYSICAL ID IS "+Integer.toString(physicalId));

        // Define the neighbors list
        neighbors = new ArrayList<Neighbor>();

        // Usual RabbitMQ setup
        String host = args[2];
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);

        Connection connection;

        // Read the file. We expect the following format (example) :
        /**
         * i j
         * 0 0 0 0
         * 0 1 0 1
         * 1 0 1 0
         * 0 1 1 0
         */
        try {
            connection = factory.newConnection();
            channel = connection.createChannel();

            Scanner netConf = new Scanner(new File(args[0]));

            int i = netConf.nextInt();
            int j = netConf.nextInt();
            int[][] netTopology = new int[i][j];
            System.out.println("Will read a "+Integer.toString(i)+" * "+Integer.toString(j)+" matrix from file.");

            while (netConf.hasNextLine()) {
                for (int m = 0; m < i; m++) {
                    for (int n = 0; n < j; n++) {
                        try {
                            netTopology[m][n] = netConf.nextInt();

                            if ((m == physicalId || n == physicalId) && netTopology[m][n] == 1 && m <= n) {
                                // Can open two queues back and forth between our node and the remote one
                                System.out.print("Can open a queue between our node and remote one with ids ");
                                System.out.print(m);
                                System.out.print(" and ");
                                System.out.println(n);

                                // Determine remote and append to neighbors list
                                int remoteNode = (m == physicalId ? n : m);

                                Neighbor ne = new Neighbor(remoteNode, false);
                                if (!neighbors.contains(ne)) {
                                    System.out.println("Adding "+Integer.toString(remoteNode)+" to neighbors list.");
                                    neighbors.add(ne);
                                }

                                // Open from us to the other - WRITE
                                String fromUsToOther = Integer.toString(physicalId) + "to" + Integer.toString(remoteNode);
                                channel.queueDeclare(fromUsToOther, false, false, false, null);

                                // Example
                                //channel.basicPublish("", fromUsToOther, null, ("Hello from node "+Integer.toString(physicalId)).getBytes("UTF-8"));

                                // Open from other to us - READ
                                String fromOtherToUs = Integer.toString(remoteNode) + "to" + Integer.toString(physicalId) ;
                                channel.queueDeclare(fromOtherToUs, false, false, false, null);

                                // Example 
                                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                                    String message = new String(delivery.getBody(), "UTF-8");
                                    //System.out.println(physicalId+" got message on queue "+fromOtherToUs+" -> "+message);

                                    String[] broker = message.split(" ");

                                    if (broker[0].equals("!RINGINITREQ")) {
                                        // Received token from above
                                        token = Integer.parseInt(broker[2]);
                                        fromOrigin = Integer.parseInt(broker[1]);
                                        // Set origin sender
                                        for (Neighbor nei : neighbors) {
                                            if (nei.remoteId == fromOrigin) {
                                                nei.setInitSender(true);
                                            }
                                        }
                                        // If not yet set, set it
                                        if (virtualId == -1) {
                                            token = token + 1;
                                            virtualId = token;
                                            System.out.println(physicalId+" got id "+virtualId+" on ring.");
                                            ringInitNext();
                                        }
                                    } else if (broker[0].equals("!RINGINITCONF")) {
                                        // Received confirmation that a neighbor has treated everything
                                        token = Integer.parseInt(broker[2]);
                                        for (Neighbor nei : neighbors) {
                                            if (nei.remoteId == Integer.parseInt(broker[1])) {
                                                nei.visited = true;
                                            }
                                        }
                                        ringInitNext();
                                        // Swap to next neighbor
                                        System.out.println("Got reply from a neighbor that could initialize up to token "+token+". End of hang.");
                                    } else {
                                        System.out.println("Got something else: "+message);
                                    }
                                };
                                channel.basicConsume(fromOtherToUs, true, deliverCallback, consumerTag -> { });
                            }
                        } catch (Exception e) {
                            System.err.println("Error on the configuration file. Terminating...");
                            System.exit(3);
                        }
                    }
                }
            }

            Scanner input = new Scanner(System.in);
            String cmd = "";

            while (!cmd.equals("/close")) {
                cmd = input.nextLine();

                if (cmd.equals("/create")) {
                    // Creates the ring by sending commands to the other nodes
                    // This node becomes the first one
                    virtualId = 0;

                    // No hanging on first iteration, send directly cmd to first neighbor
                    hang = false;

                    // We are node 0, send the token to next one (will take token + 1)
                    token = 0;

                    fromOrigin = -1;
                    // No need to set origin sender on init

                    ringInitNext();
                }
            }

            input.close();

            // Command handler will be there

            /* create_ring -> Creates the ring from the current node, with the discovery method. Doesn't work if node has already a 
            virtual id

                destroy_ring -> Just send a destroy command that resets all the virtual ids to -1, with propagation to all neighbours

                send to left/right neighbour -> Only if part of a ring

            */
        } catch (FileNotFoundException e) {
            System.err.println("The file "+args[0]+" was not found. This node will terminate.");
            System.exit(2);
        } catch (Exception e) {
            System.err.println("An error occured. This node will terminate.");
            System.exit(4);
        }

    }

    static void ringInitNext() {
        // Send command to neighbors, be careful to wait for confirmation from the neighbor before proceeding to next one
        boolean allPass = true;

        try {
            for (Neighbor n : neighbors) {
    
                // Send command to neighbor if not yet visited AND not original sender
                if (!n.visited && !n.initSender) {
                    allPass = false;
                    String queueName = Integer.toString(physicalId) + "to" + Integer.toString(n.remoteId);
                    // !RINGINITREQ ORIGINAL_SENDER TOKEN_TO_USE
                    channel.basicPublish("", queueName, null, ("!RINGINITREQ "+Integer.toString(physicalId)+" "+Integer.toString(token)).getBytes("UTF-8"));
                    //hang = true;
                    //n.setHangThere(true);
                    System.out.println("Sent token to neighbor...");
                    System.out.print("Hanging on neighbor "+Integer.toString(n.remoteId)+"... ");
                    System.out.println("Proceed.");
                    // Quit loop for now 
                    break;
                } else {
                    System.out.println("Passing neighbor...");
                }
            }

            // Over, send back to initial sender if exists (-1 means none) AND ALL PASSED (all got visited)
            if (fromOrigin != -1 && allPass) {
                // Send back the current token to initial sender
                String queueName = Integer.toString(physicalId) + "to" + Integer.toString(fromOrigin);
                channel.basicPublish("", queueName, null, ("!RINGINITCONF "+Integer.toString(physicalId)+" "+Integer.toString(token)).getBytes("UTF-8"));
                System.out.println("Finished ring creation.");
            }

            System.out.println("Finished call.");
        } catch (Exception e) {
            System.err.println("Left while processing an initialization. Everything is ruined.");
            System.err.println(e.getMessage());
            System.err.println(e);
            System.exit(10);
        }
    }
}
