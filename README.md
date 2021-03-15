# NATS ActiveMQ Subscribe Connector

A pluggable Java based service to bridge NATS messaging system with ActiveMQ

## Summary

The ActiveMQ subscribe connector is provided to facilitate the bridge of NATS and ActiveMQ (topic only) broker.  The connector only supports subscription to the ActiveMQ and does not write to it.  The reason for this decision is to improve security and simplify coding.  This is only meant for bridging ShakeAlert messages to NATS and not vice-versa.

See [The NATS Connector Framework](https://github.com/nats-io/nats-connector-framework) for more information.

The code has been inspired by the example plugin for redis.  Documentation can be found [here](http://nats-io.github.io/nats-connector-redis).

The plugin will copy the exact ActiveMQ topic to NATS subject.  Optional configuration can be defined to set pre and post subject text.

## Installation

Although the code has been inspired by the example plugin for redis, its been designed to fix some of the TODO items identified on that project.  The main "Connector" has been deisgned to connect with all options offered by jnats clients.

A simplified way to build/install from source is simply to execute maven with assembly.  This will build a single jar files with all dependencies.

```bash
mvn clean compile assembly:single
```

To run it, simply call the build jar with all dependencies

```bash
java -jar target/nats-connector-activemq-1.0-SNAPSHOT-jar-with-dependencies.jar -config application.properties
```

where -config is an optional properties file as described below.  The "-config" is the same configuration file defined in the jnats NATSConnector so it must not change.

Important note, the testing requires a local NATS and ActiveMQ server for testing.  With docker, this can be easily achieved by running the following two containers:

```bash
# Run a test activemq broker
docker run -p 61616:61616 -p 8161:8161 -d --name activemq-test rmohr/activemq
# Run a test nats broker
docker run -p 4222:4222 -p 6222:6222 -p 8222:8222 -d --name nats-main nats
```

Testing can be disabled by simply editing the pom.xml file of this project.

```xml
<!-- maven testing conditions skip -->
<maven.test.skip>true</maven.test.skip>
<maven.test.failure.ignore>false</maven.test.failure.ignore>
```

### Package name

The package structure is similar to redis but replace "redis" with "activemq".

```bash
io.nats.connector.plugins.activemq
```

#### Configuration

NATS configuration is set through the jnats client library properties and can be passed into the jvm, or specified in a configuration file. The properties are described [here](https://javadoc.io/doc/io.nats/jnats/2.1.2/io/nats/client/Options.html).

The "-config" flag, as described earlier, should be used if "io.nats.client" are defined.  Otherwise, the configuration can also be passed using the property with the file path.

```bash
-Dio.nats.connector.plugins.activemq.properties="application.properties"
```

or in code:

```java
System.setProperty(ActiveMQPlugin.PROPERTY_FILE, "application.properties");
```

The following is an example configuration file:

```properties
# NATS Client Properties
# ----------------------
# For a complete list of properties accepted by io.nats.connector.Connector
# see online documentation at:
#
#   https://javadoc.io/doc/io.nats/jnats/2.1.2/io/nats/client/Options.html
#
io.nats.client.servers=nats://localhost:4222

# ActiveMQ Client Properties
# -------------------------- 
io.nats.connector.plugins.activemq.uri=failover:(tcp://eew-cn-int1.seismo.nrcan.gc.ca:61616)
io.nats.connector.plugins.activemq.username=username
io.nats.connector.plugins.activemq.password=password
io.nats.connector.plugins.activemq.timeout=2000
io.nats.connector.plugins.activemq.topic=>
#io.nats.connector.plugins.activemq.nats.topic.pre=
#io.nats.connector.plugins.activemq.nats.topic.post=
```

* uri is the ActiveMQ connection URI
* username is the account username
* password is the account password
* timeout is the ActiveMQ message listen timeout
* topic is the ActiveMQ topic (can be wildcard)
* nats.topic.pre is pre-subject string added to the topic
* nats.topic.post is pre-subject string added to the topic

Additional properties can be added for the NATS Client.  These are defined under [here](https://javadoc.io/doc/io.nats/jnats/2.1.2/io/nats/client/Options.html).  For example, the define a NATS cluster:

```properties
io.nats.client.servers=nats://localhost:4222
```

All properties can also contain String substitution patterns as defined by apache common-text.

[http://commons.apache.org/proper/commons-text/apidocs/org/apache/commons/text/StringSubstitutor.html]

For example, the localhost name can be used as post NATS topic element as:

```properties
io.nats.connector.plugins.activemq.nats.topic.post=${localhost:canonical-name}
```

or

```properties
io.nats.connector.plugins.activemq.nats.topic.post=${env:HOSTNAME}
```

## Logging

To increase logging verbosity, the nats-connector-framework uses slf4j.

To set overall logging level:

```bash
-Dorg.slf4j.simpleLogger.defaultLogLevel=debug
```

or for the plugin only:

```bash
-Dorg.slf4j.simpleLogger.log.io.nats.connector.plugins.activemq.ActiveMQPlugin=debug
```
