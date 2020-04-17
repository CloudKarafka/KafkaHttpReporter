# Kafka Http Reporter by CloudKarafka

A metrics reporter to be installed on your kafka brokers

It exposes an HTTP API and a TCP server that is used by for example https://github.com/CloudKarafka/cloudkarafka-manager to get metrics and broker information.

## TCP actions

```
v kafka
kafka=2.3.1

jmx java.lang:type=Memory
HeapMemoryUsage.init=1073741824;;NonHeapMemoryUsage.used=96265256;;NonHeapMemoryUsage.max=-1;;HeapMemoryUsage.max=1073741824;;NonHeapMemoryUsage.committed=101580800;;Verbose=true;;NonHeapMemoryUsage.init=7667712;;ObjectPendingFinalizationCount=0;;type=Memory;;HeapMemoryUsage.used=468633592;;HeapMemoryUsage.committed=1073741824

non-existing-command

groups
group=mange1;;topic=pacman1;;partition=0;;current_offset=4003;;clientid=rdkafka;;consumerid=rdkafka-fb4bde72-24ff-4bd3-a2bc-d99267340e00;;host=/192.168.1.42
group=mange2;;topic=pacman2;;partition=0;;current_offset=4003;;clientid=rdkafka;;consumerid=rdkafka-fb4bde72-24ff-4bd3-a2bc-d99267340e00;;host=/192.168.1.42

groups mange2
group=mange2;;topic=pacman2;;partition=0;;current_offset=4003;;clientid=rdkafka;;consumerid=rdkafka-fb4bde72-24ff-4bd3-a2bc-d99267340e00;;host=/192.168.1.42

```

Since a command can give muliple results, it will always end with a empty row,
so for each command you give, make sure to read until empty row.

If you give a command that doesn't exists or a JMX query that gives no results
it will just print a `\n`

## Usage

- Download the latest version from the releases
- Put the jar file in the `libs/` folder of your kafka deployment
- Add this line to `server.properties`: `metric.reporters=cloudkarafka.kafka_http_reporter`
- (Re)start the broker

## Development

- `lein uberjar` produces a standalone jar file in `target/`
- Follow the same procedure as above to install it

## Release

- Update the version number in `project.clj`
- Commit the change and tag it with the same version
- Push to github, travis CI will generate the release artifact

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/CloudKarafka/KafkaHttpReporter/tags).

## Authors

- **Magnus Landerblom** - _Initial work_ - [snichme](https://github.com/snichme)
