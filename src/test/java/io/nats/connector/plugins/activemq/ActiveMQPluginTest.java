/**
 * @author Charles Blais <charles.blais@canada.ca>
 */

package io.nats.connector.plugins.activemq;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.MessageProducer;
import javax.jms.Session;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.nats.client.AsyncSubscription;
import io.nats.client.ConnectionFactory;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.connector.Connector;


/**
 * Unit test for simple App.
 */
public class ActiveMQPluginTest 
{
    static final String ACTIVEMQ_PAYLOAD = "Hello from ActiveMQ!";
    static final String ACTIVEMQ_URI = "failover:(tcp://localhost:61616)";

    Logger logger = null;

    abstract class TestClient
    {
        Object readyLock = new Object();
        boolean isReady = false;

        String id = "";

        Object completeLock = new Object();
        boolean isComplete = false;

        protected int testCount = 0;

        int msgCount = 0;

        int tallyMessage()
        {
            return (++msgCount);
        }

        int getMessageCount()
        {
            return msgCount;
        }

        TestClient(String id, int testCount)
        {
            this.id = id;
            this.testCount = testCount;
        }

        void setReady()
        {
            logger.debug("Client ({}) is ready.", id);
            synchronized (readyLock)
            {
                if (isReady)
                    return;

                isReady = true;
                readyLock.notifyAll();
            }
        }

        void waitUntilReady()
        {
            synchronized (readyLock)
            {
                while (!isReady) {
                    try {
                        readyLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            logger.debug("Done waiting for Client ({}) to be ready.", id);
        }

        void setComplete()
        {
            logger.debug("Client ({}) has completed.", id);

            synchronized(completeLock)
            {
                if (isComplete)
                    return;

                isComplete = true;
                completeLock.notifyAll();
            }
        }

        void waitForCompletion()
        {
            synchronized (completeLock)
            {
                while (!isComplete)
                {
                    try {
                        completeLock.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
            logger.debug("Done waiting for Client ({}) to complete.", id);
        }
    }

    /**
     * Simulates a simple ActiveMQ publisher.
     */
    public class ActiveMQPublisher extends TestClient implements Runnable
    {
        String topic;

        ActiveMQPublisher(String id, String topic, int count)
        {
            super(id, count);
            logger.debug("Creating ActiveMQ Publisher ({})", id);
            this.topic = topic;
        }

        @Override
        public void run() {
            try {
                logger.debug("ActiveMQPublisher:  {}  Starting.", id);
                // Create a ConnectionFactory
                ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory(ACTIVEMQ_URI);

                // Create a Connection
                javax.jms.Connection connection = connectionFactory.createConnection();
                connection.start();

                logger.debug("ActiveMQ Publisher:  {}  connected.", id);
                // Create session
                Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);

                // Create the destination (Topic or Queue)
                Destination destination = session.createTopic(this.topic);

                // Create a MessageProducer from the Session to the Topic or Queue
                MessageProducer producer = session.createProducer(destination);
                producer.setDeliveryMode(DeliveryMode.NON_PERSISTENT);

                for (int i = 0; i < testCount; i++) {
                    producer.send(session.createTextMessage(ACTIVEMQ_PAYLOAD));
                    tallyMessage();
                }

                logger.debug("ActiveMQ Publisher ({}) :  Published {} messages", id, testCount);

                // Clean up
                session.close();
                connection.close();
            }
            catch (Exception ex) {
                ex.printStackTrace();
            }

            setComplete();
        }
    }

    /**
     * Simulates a simple NATS subscriber.
     */
    class NatsSubscriber extends TestClient implements Runnable, MessageHandler
    {
        String subject = null;
        boolean checkPayload = true;

        NatsSubscriber(String id, String subject, int count)
        {
            super(id, count);
            this.subject = subject;

            logger.debug("Creating NATS Subscriber ({})", id);
        }

        @Override
        public void run() {

            try {
                logger.trace("NATS Subscriber ({}):  Subscribing to subject: {}", id, subject);

                io.nats.client.Connection c = new ConnectionFactory().createConnection();

                AsyncSubscription s = c.subscribe(subject, this);

                setReady();

                logger.debug("NATS Subscriber ({}):  Subscribing to subject: {}", id, subject);

                waitForCompletion();

                s.unsubscribe();

                logger.debug("NATS Subscriber ({}):  Exiting.", id);
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
            }
        }

        @Override
        public void onMessage(Message message) {

            String value = new String (message.getData());

            logger.trace("NATS Subscriber ({}):  Received message: {}", id, value);

            if (checkPayload) {
                org.junit.Assert.assertTrue(ACTIVEMQ_PAYLOAD.equals(value));
            }

            if (tallyMessage() == testCount)
            {
                logger.debug("NATS Subscriber ({}) Received {} messages.  Completed.", id, testCount);
                setComplete();
            }
        }
    }


    @Before
    public void initialize()
    {
        System.setProperty(Connector.PLUGIN_CLASS, ActiveMQPlugin.class.getName());
        logger = LoggerFactory.getLogger(ActiveMQPluginTest.class);
    }

    @Test
    public void testActiveMQToNats() throws Exception {

        System.clearProperty(ActiveMQPlugin.CONFIG_URL);

        Connector c = new Connector();

        ExecutorService executor = Executors.newFixedThreadPool(6);

        ActiveMQPublisher  as = new ActiveMQPublisher("amq", "junit.nats.test",  5);
        NatsSubscriber  ns = new NatsSubscriber("nats", "junit.nats.test", 5);

        // start the connector
        executor.execute(c);

        // start the subsciber app
        executor.execute(ns);

        // wait for subscriber to be ready.
        ns.waitUntilReady();

        // let the connector start
        Thread.sleep(2000);

        // start the publisher
        executor.execute(as);

        // wait for the subscriber to complete.
        ns.waitForCompletion();

        Assert.assertTrue("Invalid count", ns.getMessageCount() == 5);

        c.shutdown();
    }
}
