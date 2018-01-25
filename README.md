# what is polarizer?

polarizer is the second generation of polarize. For those not aware of what polarize is, polarize essentially did the
following:

- Associate in the source code all the metadata about a TestCase
- From the annotated metadata, generate an XML definition file compatible with the Polarion TestCase Importer
- Generate a mapping.json file which maps method to unique Polarion ID
- Java classes to make importer requests (both TestCase and XUnit)
- A Message Bus library to listen for reply messages for the import requests
- When java projects get compiled the XML test definitions and mapping.json files gets updated

Most of the above is still true for polarizer.  However, polarizer is actually a set of related projects aiming to do
the following:

| Job             | Project         | Description                                                                                    |
|-----------------|-----------------|------------------------------------------------------------------------------------------------|
| Metadata        | metadata        | Java annotations and python decorators to describe things like TestCase or other test metadata |
| Xunit Generator | reporter        | Java classes that will generate xunit reports compliant with Polarion                          |
| Message Bus     | polarizer-umb   | Java classes to both publish and send to the required Topic on the UMB                         |
| XML Definitions | polarizer       | Given a jar file and a mapping file, create the XML TestCase files for new/updated tests       |
| TestCase Import | polarizer-vertx | Web Service: Given the XML TestCase xml file, upload to Polarion                               |
| Xunit Import    | polarizer-vertx | Web Service: Given the xunit result file, upload to Polarion                                   |
| Map Generator   | polarizer-vertx | Web Service: Given a jar and map file, determine if TC import needed, and return new map file  |
| XUnit Generator | polarizer-vertx | Web Service: Given an xunit file and a mapfile, return xunit compatible with Polarion          |

So the new setup no longer assumes that polarize will be run locally at compile.  This means that it no longer 
automatically generates the XML definition files, and instead of keeping the map file in synch and possibly making 
TestCase import requests at compile time, this is now delegated to runtime.

The bigger news is that polarizer is more modular (with eventual plans to use Java 9 modules) by splitting out several
tasks to their own projects.  For example, the metadata is split out into the [metatdata][-metadata] project, the 
xunit generation into the [reporter][-reporter] project, the web services into the [polarizer-vertx][-pvertx] 
project, and the Unified Message Bus library in the []

## Why a new version?

While polarize got the job done, it had a lot of disadvantages:

- It was just too complex and wieldy, especially the parser to edit the config.xml file
- It was very tightly married to the java platform which has hindered its adoption
- For clojure, its testcases had to be generated at runtime anyway
- The new Unified Message Bus added some difficulties due to it requiring TLS certificates 
  - There was not a plugin like redhat-ci-plugin to send/listen for the TestCase imports 
  
So, now polarizer runs as a web service, meaning that it's functionality is done at runtime now.  This will allow non
Java teams to be able to user polarizer, once a few other things are in place (such as python decorators which are the
functional equivalent of the Java annotations, and an import load hook to find the decorations).  Also, as long as a 
team has a similar mapping file, they can now generate a Polarion-compliant Xunit xml file.  The advantages are many:

- polarizer will run as a web service
  - Clients just need to make REST calls (but also looking at websockets and GraphQL)
  - Allows non-Java teams to be able to use it
  - The bash scripts in the Build Steps of the jobs have been drastically simplified
- Simple API
  - Create Mapping: given a jar file and a mapping file, return a new mapping file
  - TestCase Import: given a TestCase XML file and map file, make request to Polarion returning a new mapping file
  - Xunit Import: given an xunit file, make request to Polarion and return response
  - Create Xunit: given a mapping file and a regular xunit file, return an xunit file compatible with /import/xunit
  
Future APIs on the roadmap:

- Create TestDefinition: 
  - For java: Given a jar file, a map file, and a json request, return TestCase XML definitions
  - For non-java: Given source code, a map file, and a json request, return TestCase XML definitions
- All the above APIs but for projects using a metadata file instead of a jar file
  - Allows projects in other languages to only need to supply a JSON metadata form

The one thing polarizer was meant to do was to get away from the mentality of a stand-alone program.  Instead, polarizer
is just a service that other clients can make requests for.

## Polarizer infrastructure

Now that polarizer as a tool is a web service, this implies setting up the infrastructure for it (ie, provisioning a 
server for polarizer to run on).  The actual project which is the web service is called polarizer-vertx (since it uses
the vertx framework), and there is an ansible playbook called polarizer.yml to setup a polarizer-vertx server.

Due to some internal servers, this playbook is not hosted on github, although eventually this should be possible.

## polarizer goodies

A brief primer on libraries other java teams can make use of

### Unified Message Bus library for Java

While the redhat-ci-jenkins plugin is definitely useful, it's also not flexible.  Ideally, a higher level library
should have been created and the jenkins plugin would have been written to use this.  In other words, the plugin would
have just been another client making use of the library.  Had this strategy been followed, other teams with other 
requirements (for example, not testing on jenkins or with other workflows) could have made use of it too. 

So onn top of the above API, polarizer also comes with a Unified Message Bus client.  Currently, if another application
wants to make use of this library, they would need to follow the directions for setting up their own TLS cert and 
getting the appropriate permissions.  This is not needed if they only wish to make use of listening for messages on the
UMB that are published from the following queue Destination:

```
VirtualTopic.qe.ci.>
```

### TestNG xunit generator 

If you're a TestNG using team, and wondering how to get all your parameterized tests into the xunit file, then look no
further.  All you need to do is supply three things:

- a config.yml or config.json file
- a mapping.json file
- Add the following to your invocation of org.testng.TestNG
  - -reporter com.github.redhatqe.polarizer.importer.XUnitService
  
polarizer comes with the XUnitReporter class which supplies a Reporter interface, so that as your test runs, it will 
generate a Polarion XUnit Importer compatible file.

### Parameterized tests

polarizer's XUnitReporter generates an xunit file supplying all the parameterized data for a TestRecord.  This will 
work for other teams too, as long as their mapping.json file contains parameters for their test case like this:

```
  "rhsm.cli.tests.ActivationKeyTests.testAttemptActivationKeyCreationInDuplicate" : {
    "RHEL6" : {
      "id" : "RHEL6-21787",
      "parameters" : [ ]
    },
    "RedHatEnterpriseLinux7" : {
      "id" : "RHEL7-51604",
      "parameters" : [ ]
    }
  },
  "rhsm.cli.tests.ActivationKeyTests.testAttemptActivationKeyCreationWithBadNameData" : {
    "RHEL6" : {
      "id" : "RHEL6-21788",
      "parameters" : [ "blockedByBug", "badName" ]
    },
    "RedHatEnterpriseLinux7" : {
      "id" : "RHEL7-51605",
      "parameters" : [ "blockedByBug", "badName" ]
    }
  },
```

Notice the inherent schema:  a fully qualified (and unique) method name, which has a sub mapping.  This sub mapping 
is from Project ID to a data structure containing the unique Polarion ID, and the names of the parameters of the test
if any.

## Future goodies

As mentioned in the future roadmap, there are several ideas in mind.  The two biggest are enabling non-java teams, and
having real-time reporting

### Non Java teams

One of the problems with polarize was that since it all centered around compile-time processing, this precluded being
able to work in a language agnostic manner.  Indeed, the original version was tightly coupled to linking one of the 
classes so that it would be picked up by the JVM when javac was run.

On further reflection though, all I really needed was 3 things:

- Access to the metadata from the source annotations
- Generating or updating the XML testcase definition files
- Generating or updating the mapping.json file

There was really no need to tie down any of those 3 things to compile time.  Morever, what I really needed was the meta
data, and it just happened that I needed to use java compilation (or reflection) to obtain this data.  If the program
could be supplied this data in another form...say for example in JSON or YAML, it could be applied universally.  All it
would need to do is access this JSON formatted data, and serialize into the POJO it was already working with.

So the trick now is to create a sort of schema so that other languages know how they should supply metadata for their
source code.  But how would other languages like python or javascript do this?

One answer is through decorators.  Every python function or method which runs a testcase could be supplied with a 
decorator such that another program could find and analyze all of these.  While javascript does not (yet until es2018)
decorators, decorators are really just syntactic sugar for functional closures.

I already have some ideas on how to implement this as shown in the README for polarize

### Real time results

One of the problems with sending import requests to Polarion is that it is asynchronous.  The Ops team just recently
made a queue browser that allows a client to look at messages still in the queue.  While this is a useful feature, it 
should be used sparingly.

By creating websocket clients, as opposed to (or in addition to) simple REST ones, it opens up some new possibilities.
Where REST services are stateless and "pull only", websockets can be stateful and are bidirectional.  If you use a curl
client to make an import request, you will be blind to the result, and if you look at the queue browser, you will wind
up effectively polling for whether it's there or not.


## What does a team need to use polarizer?

Currently, polarizer is still being worked on, and as a priority, it is targeting java based teams.  For now, a team
needs to do the following:

- At a bare minimum, provide a mapping.json file for their tests

Over time, teams should start annotating their methods so that the mapping.json file can be updated more easily, as 
well as being able to generate XML definition files.  As teams start annotating their methods, for java teams, ensure
that the annotations have a Retention Policy readable at runtime.

In the future, instead of supplying a jar file, teams can provide a metadata.json file

## Hacking

If you want to hack on polarizer, the directory structure tries to separate concerns (indeed, I want to eventually make
this a Java 9 modular system soon).  The package structure is as follows:

- com.github.redhatqe.polarizer
  - configuration:  Holds most of the data classes that can be (de)serialized to/from yaml and json
    - data: Need to consolidate only classes used as data exchange, and not as actual config files
    - api: Interfaces declaring behavior for data
  - data: Classes that implement the annotation interfaces to allow serialization of the annotations to json/yaml
  - exceptions: general exception classes go here (TODO: move specific exceptions to a related package)
  - http: The vertx verticles handling the web services and routes
  - importer: Includes the functionality to perform XUnit Imports and generate xunit report xml files
    - testcase: JAXB generated files for TestCase importer
    - xunit: JAXB generated files for XUnit importer
  - jaxb: helpers to (de)serialize to/from xml and POJOs
  - messagebus: ActiveMQ client library
    - config: data classes that can be (de)serialized to/from yaml and json
  - processor: classes that manipulate and process the metadata and help updating the mapping file
  - reflector: classes that use reflection on the jar to get the metadata from annotations and handle testcase imports
  - 

[-reporter]: https://github.com/rarebreed/reporter
[-metadata]: https://github.com/RedHatQE/metadata
[-pvertx]: https://github.com/Polarizer-Projects/polarizer-vertx
