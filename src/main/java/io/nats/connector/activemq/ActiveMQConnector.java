/**
 * @author Charles Blais <charles.blais@canada.ca>
 */
package io.nats.connector.activemq;

import io.nats.connector.Connector;
import io.nats.connector.plugins.activemq.ActiveMQPlugin;


/**
 * A Utility class to launch the Redis Connector from the command line.
 */
public class ActiveMQConnector {

    /**
     * Usage.
     */
    static void usage() {
        System.out.printf("java %s\n", ActiveMQConnector.class.getCanonicalName());
        System.out.printf("    -configURL <URL of the ActiveMQ Connector Configuration>\n" +
                          "    -debug\n");
        System.exit(-1);
    }

    static void parseArgs(String[] args)
    {
        if(args == null)
            return;

        if (args.length == 0)
            return;

        if(args.length < 2) {
            ActiveMQConnector.usage();
        }

        for (int i = 0; i < args.length; i++)
        {
            if("-configURL".equalsIgnoreCase(args[i]))
            {
                i++;
                if (i >= args.length) {
                    usage();
                }
                System.setProperty(ActiveMQPlugin.CONFIG_URL, args[i]);
            }
            else if ("-debug".equalsIgnoreCase(args[i]))
            {
                System.setProperty("org.slf4j.simpleLogger.log.io.nats.connector.plugins.activemq.ActiveMQPlugin", "trace");
            }
            else
            {
                ActiveMQConnector.usage();
            }
        }
    }

    /***
     * Entry point to launch the NATS ActiveMQ Connector
     * @param args - arguments.  See usage for more information.
     */
    public static void main(String[] args)
    {
        try
        {
            parseArgs(args);

            System.setProperty(Connector.PLUGIN_CLASS, ActiveMQPlugin.class.getName());
            new Connector(null).run();
        }
        catch (Exception e)
        {
            System.err.println(e);
        }
    }
}
