# what is polarizer?

polarizer is the second generation of polarize. For those not aware of what polarize is, polarizer is an ongoing 
improvement aimed to do the following:

- Associate in the source code all the metadata about a TestCase
- From the annotated metadata, generate an XML definition file compatible with the Polarion TestCase Importer
- Generate a mapping.json file which maps method to unique Polarion ID
- Java classes to make importer requests (both TestCase and XUnit)
- A Message Bus library to listen for reply messages for the import requests
- When java projects get compiled the XML test definitions and mapping.json files gets updated

Most of the above is still true for polarizer.  However, it no longer automatically generates the XML definition files,
and instead of keeping the map file in synch and possibly making TestCase import requests at compile time, this is now
delegated to runtime.

## Why a new version?

While polarize got the job done, it was just too complex and wieldy, so I looked at several things to see what I could
do to make things easier:

- polarizer will run as a web service instead of as a standalone project running at compile time
  - Currently targeting REST, but also looking at websockets and GraphQL
- Simple API
  - createMapping: given a jar file and a mapping file, return a new mapping file
  - createTestDefinition: given a jar file, a map file, and a json request, return TestCase XML definitions
  - testCaseImport: given a TestCase XML file and map file, make request to Polarion returning a new mapping file
  - xunitImport: given an xunit file, make request to Polarion and return response
  
Future APIs on the roadmap:

- createXunit: given a mapping file and a regular xunit file, return an xunit file compatible with /import/xunit
- All the above APIs but for projects using a metadata file instead of a jar file
  - Allows projects in other languages to only need to supply a JSON metadata form

The one thing polarizer was meant to do was to get away from the mentality of a stand-alone program.  Instead, polarizer
is just a service that other clients can make requests for.

## polarizer goodies

A brief primer on libraries other java teams can make use of

### Unified Message Bus library

On top of the above API, polarizer also comes with a Unified Message Bus client.  Currently, if another application
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
  - -reporter com.github.redhatqe.polarizer.importer.XUnitReporter
  
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
