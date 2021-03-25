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
        if (args.length != 2) {
            System.out.println("Please provide the network configuration and the physical id of this node in order to start the program.");
            System.out.println("Usage : node [network.conf] [node_physical_id]");
            System.exit(1);
        }

        // Define the ids
        physicalId = Integer.parseInt(args[1]);
        virtualId = -1;

        // Read the file. We expect the following format (example) :
        /**
         * i j
         * 0 0 0 0
         * 0 1 0 1
         * 1 0 1 0
         * 0 1 1 0
         */
        try {
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

                            if ((m == physicalId || n == physicalId) && netTopology[m][n] == 1) {
                                // Can open a queue between our node and the remote one, depends on which is where
                                // Shall we actually open two queues, one to each direction ?
                                System.out.print("Can open a queue between our node and remote one with ids ");
                                System.out.print(m);
                                System.out.print(" and ");
                                System.out.println(n);
                            }
                        } catch (Exception e) {
                            System.err.println("Error on the configuration file. Terminating...");
                            System.exit(3);
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            System.err.println("The file "+args[0]+" was not found. This node will terminate.");
            System.exit(2);
        }

    }
}
