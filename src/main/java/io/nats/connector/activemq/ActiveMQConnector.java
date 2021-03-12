/**
 * @author Charles Blais <charles.blais@canada.ca>
 */
package io.nats.connector.activemq;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
                          "    -logLevel {trace, debug, info, warn, error}\n" +
                          "    -config <properties file as defined by NATS Connector>\n");
        System.exit(-1);
    }

    /**
     * Parse the arguments as specified in usage.  Note that if there are any
     * io.nats.connector.Connector usage parameters, those are returned to be sent
     * to its thread.  Those are the properities required for the NATS connection factory.
     * 
     * https://javadoc.io/static/io.nats/jnats/1.0/io/nats/client/ConnectionFactory.html
     * 
     * Best way to get the list of options is the simply look at jnats options section with constants.
     * 
     * https://javadoc.io/doc/io.nats/jnats/2.1.2/io/nats/client/Options.html
     * 
     * @param args
     * @return String[] of arguments for io.nats.connector.Connector
     */
    static String[] parseArgs(String[] args)
    {
        List<String> natsArgs = new ArrayList<String>();

        if(args == null)
            return new String[0];

        if (args.length == 0)
            return new String[0];

        if (args.length < 2)
            usage();

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
            else if ("-logLevel".equalsIgnoreCase(args[i]))
            {
                i++;
                String[] values = {"trace", "debug", "info", "warn", "error"};
                if(!Arrays.asList(values).contains(args[i])){
                    usage();
                }
                System.setProperty("org.slf4j.simpleLogger.log.io.nats.connector.plugins.activemq.ActiveMQPlugin", args[i]);
            }
            else if("-config".equalsIgnoreCase(args[i]))
            {

                natsArgs.add(args[i++]);
                natsArgs.add(args[i]);
            }
            else
            {
                ActiveMQConnector.usage();
            }
        }

        if (natsArgs.size() == 0)
        {
            return new String[0];
        } else {
            // convert to array
            String[] strArray = new String[ natsArgs.size() ];
            natsArgs.toArray(strArray);
            return strArray;
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
            String[] connectorArgs = parseArgs(args);

            System.setProperty(Connector.PLUGIN_CLASS, ActiveMQPlugin.class.getName());
            new Connector(connectorArgs).run();
        }
        catch (Exception e)
        {
            System.err.println(e);
        }
    }
}
