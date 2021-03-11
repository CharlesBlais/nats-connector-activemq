# NATS ActiveMQ Subscribe Connector

A pluggable Java based service to bridge NATS messaging system with ActiveMQ

## Summary

The ActiveMQ subscribe connector is provided to facilitate the bridge of NATS and ActiveMQ (topic only) broker.  The connector only supports subscription to the ActiveMQ and does not write to it.  The reason for this decision is to improve security and simplify coding.  This is only meant for bridging ShakeAlert messages to NATS and not vice-versa.

See [The NATS Connector Framework](https://github.com/nats-io/nats-connector-framework) for more information.

The code has been inspired by the example plugin for redis.  Documentation can be found [here](http://nats-io.github.io/nats-connector-redis).

## Installation

The code has been inspired by the example plugin for redis.  Documentation can be found [here](http://nats-io.github.io/nats-connector-redis).  Some additional details follow.

### Package name

The package structure is similar to redis but replace "redis" with "activemq".

```bash
io.nats.connector.plugins.activemq
```

#### Configuration

NATS configuration is set through the jnats client library properties and can be passed into the jvm, or specified in a configuration file. The properties are described [here](http://nats-io.github.io/jnats/io/nats/client/Constants.html).

The NATS ActiveMQ connector is configured by specifying a url that returns JSON file as a system property.  In this example,
the url specifies a local file.  It can be any location that meets the URI standard.

```bash
-Dnats.io.connector.plugins.activemq.configurl="file://...path.json"
```

in code:

```java
System.setProperty(ActiveMQPlugin.CONFIG_URL, "file://...path.json");
```

The ActiveMQ plugin configuration file read at the URI must have the following format:

```json
{
    "uri":  "failover:(tcp://localhost:61616)",
    "username":  "username",
    "password": "password",
    "timeout" : 2000,
    "topic" : ">"
}
```

* URI is the ActiveMQ connection URI
* username is the account username
* password is the account password
* timeout is the ActiveMQ message listen timeout
* topic is the ActiveMQ topic (can be wildcard)

The program will copy the exact topic and forward it to NATS.

## Running the NATS ActiveMQ connector

There are two ways to launch the NATS ActiveMQ connector - invoking the connector as an application or programatically from your own application.

To invoke the connector from the command line:

```bash
java -classpath <your classpath> io.nats.connector.activemq.ActiveMQConnector <args>
```

The arguments are optional:

```bash
    -configURL <URL of the ActiveMQ Connector Configuration>
    -debug
```

To invoke the connector from an application:

```java
System.setProperty(Connector.PLUGIN_CLASS, "com.io.nats.connector.plugins.activemq.ActiveMQPlugin");
new Connector().run();
```

or finally, use the framework itself to run the redis connector:

```bash
java -Dio.nats.connector.plugin=com.io.nats.connector.plugins.activemq.ActiveMQPlugin io.nats.connector.Connector
```
