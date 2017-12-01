# Testing polarizer

Since the polarizer tests are mostly integration tests using a live service (the polarion-devel and UMB buses), the data
sent in the request needs to be known ahead of time.  Most of the API calls in polarizer pass in a json request.

**TODO**: Might be nice to use swagger to document the API automatically.  However, polarizer might wind up using 
GraphQL, in which case we need to use the GraphQL DSL.

## Broker config

Default:  ~/.polarizer/broker-config.yml

This file is used to configure the broker settings, which normally is the Unified Message Bus.  Because of this, the 
polarizer application needs to know about some crucial pieces of information which normally comes from a config file.
Generally, you will not pass broker configuration information as part of an API request.  The config file should be 
accessible when starting up the main verticle.  The data formats can either be in either yaml or json:

**YAML**

```yaml
brokers:
  ci:
    url: "failover:(ssl://broker01.com:12345,ssl://broker02.wcom:12345)"
    user: username
    password: password
    messages:
      timeout: 300000      # timeout in milliseconds
      maxMsgs: 2           # default number of messages to listen for
    tls:
      keystore-path: "/path/to/polarize-keystore.jks"  # Path to the .jks keystore
      truststore-path: "/path/to/truststore.jks"       # Path to the .jks truststore
      keystorekey-pw: "pvt-key-pw"                     # Password of the private key (from the .p12 file)
      keystore-pw: "keystore-pw"                       # Password of the keystore file (jks)
      truststore-pw: "truststore-pw"                   # Password of the truststore file
defaultBroker: ci
```

**JSON**

```json
{
  "brokers": {
    "ci": {
      "url": "failover:(ssl://your.broker1:12345,ssl://your.broker2:12345)",
      "user": "foo",
      "password": "blah",
      "messages": {
        "timeout": 300000,
        "maxMsgs": 2
      },
      "tls": {
        "keystore-path": "/path/to/your/keystore.jks",
        "truststore-path": "/path/to/your/truststore.jks",
        "keystorekey-pw": "pw-of-pvtkey-.p12",
        "keystore-pw": "pw-of-keystore" ,
        "truststore-pw": "pw-of-truststore",
      }
    }
  }
}
```

## TestCase data

For some API calls, it is necessary to pass along information about the TestCase setup to the method.  The format of 
this data is like so:

```yaml
project: PLATTP
author: stoner
packages:
  - rhsm.cli.tests
  - rhsm.gui.tests
servers:
  polarion:
    url: https://polarion.server/endpoint
    user: your-team
    password: your-password
testcase:
  endpoint: /endpoint/testcases
  timeout: 300000
  enabled: true
  selector:
    name: rhsm_qe
    value: testcase_importer
  title:
    prefix: "RHSM-TC: "
    suffix: ""
```

```json
{
  "project": "PLATTP",
  "author": "stoner",
  "packages": [
    "rhsm.cli.tests",
    "rhsm.gui.tests"
  ],
  "servers": {
    "polarion": {
      "url": "https://polarion.server/endpoint",
      "user": "polarion-user",
      "password": "polarion-password",
    },
    "polarion-devel": {
      "url": "https://polarion-devel.server/endpoint",
      "user": "polarion-devel-user",
      "password": "polarion-devel-password",
    },
    "polarion-stage": {
      "url": "https://polarion-stage.server/endpoint",
      "user": "polarion-stage-user",
      "password": "polarion-stage-password",
    },
  },
  "testcase": {
    "endpoint": "/endpoint/testcases",
    "timeout": 300000,
    "enabled": true,
    "selector": {
      "name": "rhsm_qe",
      "value": "testcase_importer"
    },
    "title": {
      "prefix": "String to add before title: ",
      "suffix": " String to add after title"
    }
  }
}
```

## XUnit data

Similar to the TestCase data, the XUnit data provides information necessary for some of the API calls

```yaml
project: PLATTP
mapping: /path/to/mapping.json
servers:          # Optional:  This is already the default
  polarion:
    url: https://polarion.server.com/endpoint
    user: your-team
    password: your-password
xunit:            # settings for the xunit importer
  testrun:
    id: ""        # optional unique id for testrun. Defaults to a timestamp (uniqueness by client)
    title: "Sean Toner Polarize TestRun"
    template-id: "sean toner test template"
  custom:         # Sets the custom fields in the xml
    test-suite:   # A list of key-value pairs.  The response properties
      dry-run: false
      set-testrun-finished: true
      include-skipped: false
    properties:           # a list of key value pairs where they key is a custom field
      variant: ""         # The template id to use for test runs
      arch: ""            #
      plannedin: ""       # The plannedin phase
      jenkinsjobs: ""     # Path to the jenkins job
      notes: ""           # arbitrary field
  endpoint: /endpoint/for/xunit
  selector:       # the JMS selector <name>='<value>'
    name: rhsm_qe
    value: xunit_importer
  timeout: 300000         # time in milliseconds to wait for reply message
  enabled: true
```

```json
{
  "project": "PLATTP",
  "mapping": "/path/to/mapping.json",
  "servers": {
    "polarion": {
      "url": "https://polarion.server.com/endpoint",
      "user": "your-team",
      "password": "your-password"
    }
  },
  "xunit": {
    "testrun": {
      "id": "",
      "title": "Sean Toner Polarize TestRun",
      "template-id": "sean toner test template",
    },
    "custom": {
      "test-suite": {
        "dry-run": false,
        "set-testrun-finished": true,
        "include-skipped": false,
      },
      "properties": {
        "variant": "The template id to use for test runs",
        "arch": "x86_64",
        "plannedin": "",
        "jenkinsjobs": "Path to the jenkins job",
        "notes": "arbitrary field for notes"
      }
    },
    "endpoint": "/endpoint/for/xunit",
    "selector": {
      "name": "rhsm_qe",
      "value": "xunit_importer"
    },
    "timeout": 300000,
    "enabled": true
  }
}
```

## The mapping.json format

Most of the API calls requires passing in a mapping.json file.  The format is relatively simple and looks like this:

```json
  "rhsm.cli.tests.ActivationKeyTests.testActivationKeyCreationDeletion" : {
    "RHEL6" : {
      "id" : "RHEL6-21786",
      "parameters" : [ "username", "password", "org" ]
    },
    "RedHatEnterpriseLinux7" : {
      "id" : "RHEL7-51603",
      "parameters" : [ "username", "password", "org" ]
    }
  },
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
```

The mapping.json file maps a unique method name to an ID (so there can't be overloaded method names, but there can be 
methods that are parameterized).  The one bit that may need explanation is what the "parameters" key-value pair is for.
As shown in the example, parameters is a list of strings.  These strings happen to be the names of the parameters that
the test method can take.  So for example, the rhsm.cli.tests.ActivationKeyTests.testActivationKeyCreationDeletion 
method has 3 parameters named username, password and org respectively.  Having this key-val pair in the mapping.json
allows us to have parameterized runs using polarizer.

