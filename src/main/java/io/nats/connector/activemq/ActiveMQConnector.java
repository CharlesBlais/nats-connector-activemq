/**
 * @author Charles Blais <charles.blais@canada.ca>
 */
package io.nats.connector.activemq;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nats.connector.Connector;
import io.nats.connector.plugins.activemq.ActiveMQPlugin;


/**
 * A Utility class to launch the Redis Connector from the command line.
 */
public class ActiveMQConnector {

    static final Logger logger = LoggerFactory.getLogger(ActiveMQConnector.class);

    /**
     * Usage informaiton, same as io.nats.connector.Connector
     */
    static private void usage()
    {
        System.out.printf("java {} -config <properties file>\n", Connector.class.toString());
        System.exit(-1);
    }

    /**
     * Usage informaiton, same as io.nats.connector.Connector
     */
    static private void parseArgs(String args[])
    {
        if (args == null)
            return;

        if (args.length == 0)
            return;

        if (args.length < 2)
            usage();

        // only one arg, so keep it simple
        if ("-config".equalsIgnoreCase(args[0]))
            System.setProperty(ActiveMQPlugin.PROPERTY_FILE, args[1]);
        else
            usage();
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
            new Connector(args).run();
        }
        catch (Exception e)
        {
            System.err.println(e);
        }
    }
}
