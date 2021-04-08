//Imports
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
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

    // Variables for the ring operation
    static ArrayList<Integer> leftRoute; 
    static ArrayList<Integer> rightRoute; 
    static boolean pathGot;
    static ArrayList<Integer> trace;

    public static void pathToNeighbor(int[][] network, int s, int d) {
        boolean[] visited = new boolean[network[0].length];
        ArrayList<Integer> pathList = new ArrayList<Integer>();
        pathList.add(s);
        pathGot = false;

        possiblePaths(s, d, visited, pathList, network);

        pathGot = false;

    }

    private static void possiblePaths(int c, int d, boolean[] visited, ArrayList<Integer> localPaths, int[][] network) {
        if (pathGot) {
            return;
        }

        if (c == d) {
            trace = new ArrayList<>(localPaths);
            pathGot = true;
            return;
        }

        visited[c] = true;

        for(int j = 0; j < network[c].length; j++) {
            if (network[c][j] == 1) {
                // Adjacent
                localPaths.add(j);
                possiblePaths(j, d, visited, localPaths, network);
                localPaths.remove(Integer.valueOf(j));
            }
        }

        visited[c] = false;
    }

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Please provide the network configuration, the physical id of this node as well as the RabbitMQ server address to join in order to start the program.");
            System.out.println("Usage : node [network.conf] [node_physical_id]");
            System.exit(1);
        }

        // Define the ids
        physicalId = Integer.parseInt(args[1]);
        virtualId = Integer.parseInt(args[1]);

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

                                    if(broker[0].equals("!PHYSICALROUTE")) {
                                        // Check what is the next node to send the message
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

            // Take node id on left (-1) and on right (+1)

            int leftNode = (physicalId == 0) ? i - 1 : physicalId - 1;

            int rightNode = (physicalId == i-1) ? 0 : physicalId + 1;

            System.out.println("left is "+leftNode+", right is "+rightNode);

            pathToNeighbor(netTopology, physicalId, rightNode);
            rightRoute = new ArrayList<>(trace);
            System.out.println("Right route is "+rightRoute.toString());

            pathToNeighbor(netTopology, physicalId, leftNode);
            leftRoute = new ArrayList<>(trace);
            System.out.println("Left route is "+leftRoute.toString());

            Scanner input = new Scanner(System.in);
            String cmd = "";

            while (!cmd.equals("/close")) {
                cmd = input.nextLine();

                if (cmd.equals("/sendleft")) {
                    // Send to the left
                } else if (cmd.equals("/sendright")) {
                    // Send to the right
                }
            }

            input.close();

            // Command handler will be there

            /*
                send to left/right neighbour -> Only if part of a ring

            */
        } catch (FileNotFoundException e) {
            System.err.println("The file "+args[0]+" was not found. This node will terminate.");
            System.exit(2);
        } catch (Exception e) {
            System.err.println("An error occured. This node will terminate.");
            for (StackTraceElement string : e.getStackTrace()) {
                System.err.println(string);
            }
            System.exit(4);
        }

    }
}
