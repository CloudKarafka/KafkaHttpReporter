# Kafka Http Reporter by CloudKarafka

A metrics reporter to be installed on your kafka brokers

It exposes an HTTP API that is used by https://github.com/CloudKarafka/cloudkarafka-manager to get metrics and broker information. 

## Usage

* Download the latest version from the releases
* Put the jar file in the `libs/` folder of your kafka deployment
* Add this line to `server.properties`: `metric.reporters=cloudkarafka.kafka_http_reporter`
* (Re)start the broker

## Development

* `lein uberjar` produces a standalone jar file in `target/`
* Follow the same procedure as above to install it

## Release

* Update the version number in `project.clj`
* Commit the change and tag it with the same version 
* Push to github, travis CI will generate the release artifact

## Versioning

We use [SemVer](http://semver.org/) for versioning. For the versions available, see the [tags on this repository](https://github.com/CloudKarafka/KafkaHttpReporter/tags). 

## Authors

* **Magnus Landerblom** - *Initial work* - [snichme](https://github.com/snichme)


