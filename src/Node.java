//Imports
import java.io.File;
import java.io.FileNotFoundException;
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
    public static void main(String[] args) {
        System.out.println(args.length);
        if (args.length != 3) {
            System.out.println("Please provide the network configuration, the physical id of this node as well as the RabbitMQ server address to join in order to start the program.");
            System.out.println("Usage : node [network.conf] [node_physical_id]");
            System.exit(1);
        }

        // Define the ids
        physicalId = Integer.parseInt(args[1]);
        virtualId = -1;

        // Usual RabbitMQ setup
        String host = args[2];
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(host);

        Connection connection;
        Channel channel;

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

                                // Open from us to the other - WRITE
                                String fromUsToOther = Integer.toString(physicalId) + "to" + (m == physicalId ? Integer.toString(n) : Integer.toString(m));
                                channel.queueDeclare(fromUsToOther, false, false, false, null);

                                // Example
                                channel.basicPublish("", fromUsToOther, null, ("Hello from node "+Integer.toString(physicalId)).getBytes("UTF-8"));

                                // Open from other to us - READ
                                String fromOtherToUs = (m == physicalId ? Integer.toString(n) : Integer.toString(m)) + "to" + Integer.toString(physicalId) ;
                                channel.queueDeclare(fromOtherToUs, false, false, false, null);

                                // Example 
                                DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                                    String message = new String(delivery.getBody(), "UTF-8");
                                    System.out.println(physicalId+" got message on queue "+fromOtherToUs+" -> "+message);
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
}
