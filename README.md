# EDC - EDPS Extension


To extend the EDC with the EDPS functionality, this extension provides the following features:
- Create an EDPS job to enhance an asset
- Retrieve the enhanced asset from EDPS
- Exposes endpoints to retrieve EDPS job information
- Create a result asset in EDC with the enhanced data
- Publish the result asset to Daseen


## Project Structure

The project consists of the following modules:
- `edc-edps-extension`: The actual EDC-EDP connector extension
- utils: 
  - `http-file-server`: contains the http file server to provide the demo data.csv
  - `edps_mock-server`: the mock server for the EDPS and Daseen Api

Note: *To switch between the mock server and the real EDPS and Daseen Api, the `application.properties` in the `edc-edps-extension` module has to be adjusted.
It is sufficient to change the `edc.edps.url` and `edc.daseen.url` properties to the real endpoint.*

## Documentation

The Api reference for the:
- Management Api can be found [here](https://github.com/eclipse-edc/Connector/blob/gh-pages/openapi/management-api/3.0.6/management-api.yaml).  
- This extension can be found in the resources folder of the edc-edps-extension.


## EDP Workflow

### 1. Start the EDC server

Build the connector:

```bash
./gradlew edc-edps-extension:connector:build
```

Run the connector:

```bash
java -Dedc.keystore=app-edc-edps-demo/resources/certs/cert.pfx -Dedc.keystore.password=123456 -Dedc.fs.config=-Dedc.fs.config=edc-edps-extension/connector/src/main/resources/application.properties -jar app-edc-edps-demo/connector/build/libs/connector.jar
```

Alternatively, start the `io/nexyo/edp/extensions/Runner.java` in the IDE.


### 2. Start HTTP server to providing demo data.csv

Start with the following command:

```bash
python ./util/http-file-server/server.py
```


### 1. Create Asset

Use the management Api to create an asset:

```bash
curl -d @edc-edps-extension/connector/src/main/resources/requests/create-asset.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/assets \
  -s | jq 
```

[Optional] Check if asset is created:

```bash 
curl  http://localhost:19193/management/v3/assets/assetId1
```


### 2. Create EDPS Job

```bash 
curl -X POST http://localhost:19191/api/edps/assetId1/jobs
```


### 3. Get EDPS Result


```bash
curl -X POST http://localhost:19191/api/edps/assetId1/jobs/{jobId}/result \
  -H 'content-type: application/json' \
  -d @edc-edps-extension/connector/src/main/resources/requests/fetch-edps-result.json 
````


### 4. Create Result Asset

```bash
curl -d @edc-edps-extension/connector/src/main/resources/requests/create-result-asset.json \
  -H 'content-type: application/json' http://localhost:19193/management/v3/assets \
  -s | jq 
```


### 5. Publish Result to Daseen

```bash
curl -H 'content-type: application/json' http://localhost:19193/api/daseen/resultAssetId1/publish \
  -s | jq 
```


## Limitations

- Only transfers of type `HttpData` are supported at the moment
- EDPS and Daseen Api are mocked as the content type of the requests are determined by the Dataplane
- No error handling for Dataplane operations



## ToDos:

- Proper error handling for Dataplane operations
- Persistence: store edps job information
- Javadoc

