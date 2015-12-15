SAMI Cloud Connector SDK and examples
==============================

Background
-----------------------

A device can send their data directly to SAMI via [API calls](https://developer.samsungsami.io/sami/sami-documentation/sending-and-receiving-data.html). However, some devices already send data to a third-party cloud. In this case, SAMI can use the device's cloud, rather than the device, as the data source. You can build what we call a Cloud Connector to bridge SAMI to the third-party cloud. SAMI can then retrieve the device's data from that cloud.

This repository hosts Cloud Connector SDK. You write Cloud Connector Groovy code using this SDK.

 * [Using Cloud Connectors](https://developer.samsungsami.io/sami/sami-documentation/using-cloud-connectors.html) gives the overview of the Cloud Connector concept.
 * [Your first Cloud Connector](https://developer.samsungsami.io/sami/demos-tools/your-first-cloud-connector.html) explains the developer workflow and Cloud Connector code using Moves as an example.
 * [CloudConnector API Doc](http://samsungsamiio.github.io/sami-cloudconnector-sdk/apidoc/), which lists functions and structures, and explains goals and usages.

What are included in this repository?
-----------------------

 1. libs: SDK libraries
 2. apidoc: Cloud Connector SDK API documenation
 3. template: a template project. You can build and test your own Cloud Connector code based on it.
 4. sample-xxxx: Cloud Connector examples. The examples have been tested and work in production.

sample-xxxx has the same structures as template folder. You can compile and test sample Cloud Connector by following the instructions in [template/README.md](template/README.md).