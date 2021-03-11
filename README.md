# NATS ActiveMQ Subscribe Connector

A pluggable Java based service to bridge NATS messaging system with ActiveMQ

## Summary

The ActiveMQ subscribe connector is provided to facilitate the bridge of NATS and ActiveMQ (topic only) broker.  The connector only supports subscription to the ActiveMQ and does not write to it.  The reason for this decision is to improve security and simplify coding.  This is only meant for bridging ShakeAlert messages to NATS and not vice-versa.

See [The NATS Connector Framework](https://github.com/nats-io/nats-connector-framework) for more information.

The code has been inspired by the example plugin for redis.  Documentation can be found [here](http://nats-io.github.io/nats-connector-redis).

## Installation

The code has been inspired by the example plugin for redis.  Documentation can be found [here](http://nats-io.github.io/nats-connector-redis).  Some additional details follow.

A simplified way to build/install from source is simply to execute maven with assembly.

```bash
mvn clean compile assembly:single
```

And to run it, simply call the build jar with all dependencies

```bash
java -jar target/nats-connector-activemq-1.0-SNAPSHOT-jar-with-dependencies.jar -configURL file://`pwd`/config.json
```

Where config.json is your custom configuration file.

Important note, the testing requires a local NATS and ActiveMQ server for testing.  With docker, this can be easily achieved by running the following two containers:

```bash
# Run a test activemq broker
docker run -p 61616:61616 -p 8161:8161 -d --name activemq-test rmohr/activemq
# Run a test nats broker
docker run -p 4222:4222 -p 6222:6222 -p 8222:8222 -d --name nats-main nats
```

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
