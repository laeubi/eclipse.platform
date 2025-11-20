# org.eclipse.unittest.ui.ota4j

Integration bundle for Open Test Reporting (OTA4J) format with Eclipse Unit Test view.

## Overview

This bundle provides a bridge between test frameworks that output results in the Open Test Reporting format and the Eclipse Unit Test view. It uses a StAX-based streaming approach to efficiently process test events with minimal memory footprint.

## Features

- **StAX-based XML parsing**: Efficient streaming XML processing for large test result files
- **Low memory footprint**: Processes events as they arrive without loading the entire document into memory
- **Full OTA4J support**: Implements OTA4J core 0.2.0 and events 0.2.0 specifications
- **Vendor-neutral**: Works with any test framework that outputs OTA4J format

## API

### OpenTestReportingReader

The main class for reading OTA4J event streams:

```java
ITestRunSession session = ...; // Your test run session
Reader eventReader = new FileReader("test-results.xml");

OpenTestReportingReader reader = new OpenTestReportingReader(session);
reader.readEvents(eventReader);
```

### OpenTestReportingClient

An `ITestRunnerClient` implementation for continuous monitoring:

```java
ITestRunSession session = ...; // Your test run session
Reader eventReader = ...; // Stream of OTA4J events

OpenTestReportingClient client = new OpenTestReportingClient(session, eventReader);
client.startMonitoring();
```

## OTA4J Event Mapping

The following OTA4J events are mapped to Unit Test API calls:

| OTA4J Event | Unit Test API |
|-------------|---------------|
| `started` (with parentId) | `newTestCase()` or `newTestSuite()` + `notifyTestStarted()` |
| `finished` with SUCCESSFUL | `notifyTestEnded(ignored=false)` |
| `finished` with SKIPPED | `notifyTestEnded(ignored=true)` |
| `finished` with FAILED | `notifyTestFailed(FAILURE)` + `notifyTestEnded()` |
| `finished` with ERRORED | `notifyTestFailed(ERROR)` + `notifyTestEnded()` |
| `finished` with ABORTED | `notifyTestFailed(ERROR)` + `notifyTestEnded()` |

## Examples

See the `examples/` directory for sample OTA4J event files.

## OTA4J Specification

The Open Test Reporting format is defined by the [OTA4J project](https://github.com/ota4j-team/open-test-reporting).

This bundle supports:
- OTA4J Core Schema 0.2.0
- OTA4J Events Schema 0.2.0

The XSD schema files are included in the `schema/` directory for reference.

## Requirements

- Java 17 or later
- Eclipse Platform Runtime 3.29.0 or later
- Eclipse Unit Test UI 1.1.0 or later

## License

This bundle is licensed under the Eclipse Public License 2.0 (EPL-2.0).
