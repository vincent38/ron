//Imports

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;
import java.util.stream.IntStream;

public class Node {

    /**
     * The main class for the ring topology over a physical network
     */

    // Global variables for the node
    static int physicalId;
    static int virtualId;
    static Channel channel;
    static int netSize;

    // Variables for ring initialization
    static boolean hang;
    static int token;
    static int fromOrigin;

    // Variables for the ring operation
    static ArrayList<Integer> leftRoute;
    static ArrayList<Integer> rightRoute;
    static int[] lRoute;
    static int[] rRoute;
    static boolean pathGot;
    static ArrayList<Integer> trace;

    public static void main(String[] args) {
        if (args.length != 3) {
            System.out.println("Please provide the network configuration, the physical id of this node as well as " +
                    "the RabbitMQ server address to join in order to start the program.");
            System.out.println("Usage : node [network.conf] [node_physical_id]");
            System.exit(1);
        }

        // Define the ids
        physicalId = Integer.parseInt(args[1]);
        virtualId = Integer.parseInt(args[1]);

        System.out.println("[NETWORK] Hello world ! Physical ID: "+Integer.toString(physicalId)+" - Virtual ID: "+Integer.toString(virtualId));

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

            netSize = netConf.nextInt();
            int[][] netTopology = new int[netSize][netSize];
            System.out.println("[NETWORK] Will read a " + netSize + " * " + netSize + " matrix from file.");

            createQueuesBetweenNodes(netConf, netTopology);
            computePathsBetweenNodes(netTopology);

            Scanner input = new Scanner(System.in);
            String cmd = "";

            while (!cmd.equals("/close")) {
                cmd = input.nextLine();
                handleCommand(cmd);
            }

            input.close();

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

    private static void createQueuesBetweenNodes(Scanner netConf, int[][] netTopology) {
        while (netConf.hasNextLine()) {
            for (int m = 0; m < netSize; m++) {
                for (int n = 0; n < netSize; n++) {
                    try {
                        netTopology[m][n] = netConf.nextInt();
                        if ((m == physicalId || n == physicalId) && netTopology[m][n] == 1 && m <= n) {
                            // Can open two queues back and forth between our node and the remote one
                            System.out.println("[NETWORK] Can open a queue between our node and remote one with ids " + m + " and " + n);

                            // Determine remote and append to neighbors list
                            int remoteNode = (m == physicalId ? n : m);

                            // Open from us to the other - WRITE
                            String fromUsToOther = Integer.toString(physicalId) + "to" + Integer.toString(remoteNode);
                            channel.queueDeclare(fromUsToOther, false, false, false, null);

                            // Open from other to us - READ
                            String fromOtherToUs = Integer.toString(remoteNode) + "to" + Integer.toString(physicalId) ;
                            channel.queueDeclare(fromOtherToUs, false, false, false, null);

                            // Example
                            DeliverCallback deliverCallback = (consumerTag, delivery) -> {
                                String message = new String(delivery.getBody(), "UTF-8");
                                routeMessage(message);
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
    }

    private static void computePathsBetweenNodes(int[][] netTopology) {
        int leftNode = (physicalId == 0) ? netSize - 1 : physicalId - 1;
        int rightNode = (physicalId == netSize-1) ? 0 : physicalId + 1;
        System.out.println("[RING] Left ID is " + leftNode + ", Right ID is " + rightNode);

        rightRoute = getShortestPath(physicalId, rightNode, netTopology);
        leftRoute = getShortestPath(physicalId, leftNode, netTopology);

        // Better for our message builder
        rRoute = rightRoute.stream().mapToInt(Integer::intValue).toArray();
        lRoute = leftRoute.stream().mapToInt(Integer::intValue).toArray();
        rRoute = Arrays.copyOfRange(rRoute, 1, rRoute.length);
        lRoute = Arrays.copyOfRange(lRoute, 1, lRoute.length);
    }

    private static void handleCommand(String cmd) {
        String[] cmdBroker = cmd.split(" ");
        String msg = "Please enter a valid command. Either '/sendleft <message>' or '/sendright <message>'";
        if (cmdBroker.length == 1) {
            System.out.println(msg);
        } else if (cmdBroker.length > 1 && !(cmdBroker[1] == null) && !cmdBroker[1].isEmpty()) {
            if (cmdBroker[0].equals("/sendleft")) {
                // Send to the left with buildSend
                buildSendMessage(lRoute, cmd.replaceFirst("/sendleft ", Integer.toString(virtualId)+" "));
            } else if (cmdBroker[0].equals("/sendright")) {
                // Send to the right with buildSend
                buildSendMessage(rRoute, cmd.replaceFirst("/sendright ", Integer.toString(virtualId)+" "));
            } else {
                System.out.println(msg);
            }
        }
    }

    /**
     * Route message to next node, if necessary
     * @param message
     */
    private static void routeMessage(String message) {
        String[] broker = message.split(" ");

        if(broker[0].equals("!PHYSICALROUTE")) {
            // Check what is the next node to send the message

            // Structure of command : cmd array,of,nodes message
            String content = message.replaceFirst(broker[0]+" "+broker[1]+" ", "");

            if (broker[1].equals("NULL")) {
                // Arrived at the end. Clean the content to discard the original sender id
                System.out.println("/!\\ Received message from node "+broker[2]+": "+content.replaceFirst(broker[2]+" ", ""));
            } else {
                // Get the route, the next router, and the new route
                int[] route = Arrays.stream(broker[1].split(",")).map(String::trim).mapToInt(Integer::parseInt).toArray();

                // Hand over to the buildSend procedure
                buildSendMessage(route, content);
            }
        }
    }

    /**
     * This function builds the shortest path between the source and the destination over a physical network
     */
    private static ArrayList<Integer> getShortestPath(int src, int dest, int[][] network) {
        Queue<Integer> queue = new ArrayDeque<>();

        int[] predList = new int[netSize];
        int[] dist = new int[netSize];
        boolean[] visited = new boolean[netSize];

        for (int i = 0; i < netSize; i++) {
            dist[i] = 10000000;
            visited[i] = false;
        }

        queue.add(src);

        visited[src] = true;
        dist[src] = 0;
        predList[src] = -1;

        mainLoop:
        while (!queue.isEmpty()) {
            int s = queue.remove();
            for (int i = 0; i < network[s].length; i++) {
                if (network[s][i] == 1 && !visited[i]) {
                    visited[i] = true;
                    dist[i] = dist[s] + 1;
                    predList[i] = s;
                    queue.add(i);

                    if (i == dest) {
                        break mainLoop;
                    }
                }
            }
        }

        Deque<Integer> route = new LinkedList<>();
        int trace = dest;
        route.add(trace);

        while (predList[trace] != -1) {
            route.addFirst(predList[trace]);
            trace = predList[trace];
        }
        return new ArrayList<>(route);
    }

    private static void buildSendMessage(int[] route, String content){
        // Take next sender directly
        int dest = route[0];
        int[] newRoute = Arrays.copyOfRange(route, 1, route.length);

        // Message will be sent to dest
        String routeTo = physicalId + "to" + dest;

        // First element of the message
        String msg = "!PHYSICALROUTE ";

        // Second element of the message
        if (newRoute.length == 0) {
            // We were the last router
            msg += "NULL";
        } else {
            // There is at least still one router
            msg += Arrays.stream(newRoute).mapToObj(String::valueOf).reduce((a, b) -> a.concat(",").concat(b)).get();
        }

        // Third element of the message
        msg += " "+content;

        StringJoiner path = new StringJoiner(" -> ");
        path.add(String.valueOf(physicalId));
        IntStream.of(route).forEach(x -> path.add(String.valueOf(x)));

        System.out.println("[PHYSICAL TRACEROUTE] Message route: "+ path.toString());

        try {
            channel.basicPublish("", routeTo, null, msg.getBytes("UTF-8"));
        } catch (Exception e) {
            System.err.println("An error occured while trying to route a message. Node exiting...");
            System.exit(-2);
        }
    }
}
