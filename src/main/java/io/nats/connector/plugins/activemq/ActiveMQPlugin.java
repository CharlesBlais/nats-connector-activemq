/**
 * @author Charles Blais <charles.blais@canada.ca>
 */

package io.nats.connector.plugins.activemq;

import io.nats.client.ConnectionFactory;
import io.nats.client.Message;
import io.nats.connector.plugin.NATSConnector;
import io.nats.connector.plugin.NATSConnectorPlugin;
import io.nats.connector.plugin.NATSEvent;
import io.nats.connector.plugin.NATSUtilities;

import java.io.IOException;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jms.Destination;
import javax.jms.ExceptionListener;
import javax.jms.JMSException;
import javax.jms.MessageConsumer;
import javax.jms.Session;
import javax.jms.TextMessage;

import org.apache.activemq.ActiveMQConnection;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.activemq.transport.TransportListener;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.slf4j.Logger;

/**
 * A ActiveMQ consumer only plugin.
 * 
 * It reads a configuration file from a provided url to direct
 * the connector to bridge NATS and ActiveMQ.
 * 
 * The file is JSON formatted with the following structure:
 * 
 * {
 *   "uri": "failover:(tcp://localhost:61616)",
 *   "username": "",
 *   "password": "",
 *   "timeout": 2000,
 *   "topic": ">"
 * }
 * 
 */
public class ActiveMQPlugin implements NATSConnectorPlugin
{
    /**
     * The property location to specify a configuration URL.
     */
    static public final String CONFIG_URL = "nats.io.connector.plugins.activemq.configurl";

    /**
     * Default activemq host with JMS port
     */
    static public final String DEFAULT_ACTIVEMQ_URI = "failover:(tcp://localhost:61616)";

    /**
     * Default activemq username
     */
    static public final String DEFAULT_ACTIVEMQ_USERNAME = "";

    /**
     * Default activemq passowrd
     */
    static public final String DEFAULT_ACTIVEMQ_PASSWORD = "";

    /**
     * Default activemq timeout
     */
    static public final int DEFAULT_ACTIVEMQ_TIMEOUT = 2000;

    /**
     * Default activemq topic
     */
    static public final String DEFAULT_ACTIVEMQ_TOPIC = ">";


    NATSConnector connector = null;
    Logger logger = null;

    boolean trace = false;

    String uri = DEFAULT_ACTIVEMQ_URI;
    String username = DEFAULT_ACTIVEMQ_USERNAME;
    String password = DEFAULT_ACTIVEMQ_PASSWORD;
    int timeout = DEFAULT_ACTIVEMQ_TIMEOUT;
    String topic = DEFAULT_ACTIVEMQ_TOPIC;

    String configUrl = null;

    ActiveMQListener listener = null;

    String defaultConfiguration =
        "{" +
            "\"uri\" : \"" + DEFAULT_ACTIVEMQ_URI + "\"," +
            "\"username\" : \"" + DEFAULT_ACTIVEMQ_USERNAME + "\"," +
            "\"password\" : \"" + DEFAULT_ACTIVEMQ_PASSWORD + "\"," +
            "\"timeout\" : \"" + DEFAULT_ACTIVEMQ_TIMEOUT + "\"," +
            "\"topic\" : \"" + DEFAULT_ACTIVEMQ_TOPIC + "\"" +
        "}";


    /**
     * Get the configuration URL from the properties (if set)
     */
    private void loadProperties()
    {
        Properties p = System.getProperties();
        configUrl = p.getProperty(CONFIG_URL);
    }


    /**
     * Gets the default configuration.
     * @return default configuration as a JSON string.
     */
    String getDefaultConfiguration()
    {
        return defaultConfiguration;
    }


     /**
     * Parse content of string JSON configuration file
     *
     * @param jsonConfig - json configuration in a string.
     * @throws Exception - an error occurred parsing the configuration.
     */
    public void parseConfiguration(String jsonConfig) throws Exception
    {
        JSONObject rootConfig = new JSONObject(new JSONTokener(jsonConfig));

        uri = rootConfig.optString("uri", DEFAULT_ACTIVEMQ_URI);
        username = rootConfig.optString("username", DEFAULT_ACTIVEMQ_USERNAME);
        password = rootConfig.optString("password", DEFAULT_ACTIVEMQ_PASSWORD);
        timeout = rootConfig.optInt("timeout", DEFAULT_ACTIVEMQ_TIMEOUT);
        topic = rootConfig.optString("topic", DEFAULT_ACTIVEMQ_TOPIC);
    }


    /**
     * Load configuration from default and overwrite in URL (for cloud)
     * is used.
     */
    private void loadConfig() throws Exception
    {
        String configStr = getDefaultConfiguration();
        if (configUrl != null) {
            configStr = NATSUtilities.readFromUrl(configUrl);
        }
        parseConfiguration(configStr);
    }


    /**
     * ActiveMQ listener
     */
    private class ActiveMQListener implements Runnable, ExceptionListener, TransportListener
    {
        private MessageConsumer consumer;


        /**
         * Send a message NATS.  Note the following assumes that the connector to the
         * NATS server has been established.
         * 
         * @param topic - NATS topic to send to
         * @param message - message to send
         */
        private void sendNatsMessage(String topic, String message)
        {
            byte[] payload = message.getBytes();
            Message natsMessage = new Message();
            natsMessage.setData(payload, 0, payload.length);
            natsMessage.setSubject(topic);
            connector.publish(natsMessage);
        }


        /**
         * Parse topic by removing xxx:// prepended in front of ActiveMQ
         * messages.
         * 
         * @param topic
         * @return parse topic
         */
        private String parseTopic(String topic)
        {
            return topic.split("//", 2)[1];
        }


        /**
         * Thread process.  Loops until thread is interrupted. Important
         * to note that messages that aren't TextMessage are ignored.
         */
        public void run()
        {
            javax.jms.Message message;

            try {
                this.connect();
                while (!Thread.currentThread().isInterrupted()) {
                    // Wait for a message
				    message = this.consumer.receive(timeout);
                    if (message == null) {
                        logger.debug("Timeout reached");
                    } else if (message instanceof TextMessage) {
                        TextMessage textMessage = (TextMessage) message;
                        String amqTopic = textMessage.getJMSDestination().toString();
                        String natsTopic = parseTopic(amqTopic);
                        String content = textMessage.getText();
                        logger.debug(
                            "Send ActiveMQ ({}) -> NATS ({}):\n{}",
                            amqTopic, natsTopic, content);
                        sendNatsMessage(natsTopic, content);
                    } else {
                        logger.debug("Received (ignored):\n{}", message);
                    }
                }
            } catch (JMSException e) {
                logger.error("Problem with ActiveMQ broker", e);
			    Thread.currentThread().interrupt();
            }
            logger.info("ActiveMQ listener thread finished");
        }


        /**
         * Initiate connection to ActiveMQ broker.  The connection is establised and the
         * object is self referenced.
         * 
         * @throws JMSException 
         */
        private void connect() throws JMSException
        {		
            logger.info("Setting ActiveMQ connection to {}", uri);
            ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(uri);
            
            javax.jms.Connection connection;
            if( username != "" && password != "") {
                logger.info("Connecting with username {} (password hidden) ", username);
                connection = connectionFactory.createConnection(username, password);
            } else {
                logger.info("Connecting with no credentials");
                connection = connectionFactory.createConnection();
            }
            ((ActiveMQConnection) connection).addTransportListener(this);
            connection.setExceptionListener(this);

            logger.info("Start connection");
		    connection.start();

            // Create a Session
            Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
            
            // Connect to topic
            logger.info("Subscribe to topic {}", topic);
            Destination destination = session.createTopic(topic);
            
            this.consumer = session.createConsumer(destination);
        }

        @Override
        public synchronized void onException(JMSException err) {
            logger.error("JMS Exception", err);
            Thread.currentThread().interrupt();
        }
        
        @Override
        public synchronized void onException(IOException err) {
            logger.warn("IOException received", err);
        }
    
        @Override
        public synchronized void transportInterupted() {
            logger.info("Transport interrupted");
        }
    
        @Override
        public synchronized void transportResumed() {
            logger.info("Transport resumed");
        }

        @Override
        public void onCommand(Object command) {}

        /**
         * Shutdown current thread
         */
        public void shutdown()
        {
            logger.info("ActiveMQ listener shutdown");
            Thread.currentThread().interrupt();
        }
    }


    /**
     * Initialize activemq thread object but do not run.  We will
     * let NATS ExecutorService to handle the trhread.
     */
    private void initActiveMQ()
    {
        listener = new ActiveMQListener();
    }


    /**
     * Shutdown the thread
     */
    private void teardownActiveMQ()
    {
        if (listener != null) {
            listener.shutdown();
        }
    }

    /**
     * Invoked when the connector is started up, before a connection
     * to the NATS cluster is made.  The NATS connection factory is
     * valid at this time, providing an opportunity to alter
     * NATS connection parameters based on other plugin variables.
     *
     * @param logger - logger for the NATS connector process.
     * @param factory - the NATS connection factory.
     * @return - true if the connector should continue, false otherwise.
     */
    @Override
    public boolean onStartup(Logger logger, ConnectionFactory factory) {
        this.logger = logger;

        try {
            loadProperties();
            loadConfig();
            initActiveMQ();
        }
        catch (Exception e) {
            logger.error("Unable to initialize", e);
            teardownActiveMQ();
            return false;
        }

        return true;
    }


    /**
     * Invoked after startup, when the NATS plug-in has connectivity to the
     * NATS cluster, and is ready to start sending and
     * and receiving messages.  This is the place to create NATS subscriptions.
     *
     * @param connector interface to the NATS connector
     *
     * @return true if the plugin can continue.
     */
    @Override
    public boolean onNatsInitialized(NATSConnector connector)
    {
        this.connector = connector;

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(new ActiveMQListener());
        return true;
    }

    /**
     * Invoked when the Plugin is shutting down.  This is typically where
     * plugin resources are cleaned up.
     */
    @Override
    public void onShutdown()
    {
        teardownActiveMQ();
    }

    /**
     * Invoked anytime a NATS message is received to be processed.
     * 
     * @param msg - NATS message received.
     */
    @Override
    public void onNATSMessage(Message msg)
    {
        logger.debug("Received NATS ({}), plugin not desgined for ActiveMQ publishing", msg.getSubject());
    }

    /**
     * Invoked when the Plugin is shutting down.  This is typically where
     * plugin resources are cleaned up.
     */
    @Override
    public void onNATSEvent(NATSEvent event, String message)
    {
        // When a connection has been disconnected unexpectedly, NATS will
        // try to reconnect.  Messages published during the reconnect will
        // be buffered and resent, so there may be no need to do anything.
        // Connection disconnected - close JEDIS, buffer messages?
        // Reconnected - reconnect to JEDIS.
        // Closed:  should handle elsewhere.
        // Async error.  Notify, let admins handle these.
        switch (event)
        {
            case ASYNC_ERROR:
                logger.error("NATS Asynchronous error: " + message);
                break;
            case RECONNECTED:
                logger.info("Reconnected to the NATS cluster: " + message);
                // At this point, we may not have to do much.  Buffered NATS messages
                // may be flushed. and we'll buffer and flush the Redis messages.
                // Revisit this later if we need more buffering.
                break;
            case DISCONNECTED:
                logger.info("Disconnected from the NATS cluster: " + message);
                break;
            case CLOSED:
                logger.debug("NATS Event Connection Closed: " + message);
                // shudown - if this is a result of shutdown elsewhere,
                // there will be no effect.
                connector.shutdown();
                break;
            default:
                logger.warn("Unknown NATS Event: " + message);
        }
    }

}
