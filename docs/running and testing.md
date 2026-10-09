# Running and Testing the Country Integration Application

## 1. Prerequisites

Ensure the following tools are installed:

* Java 21
* Maven
* Docker Desktop
* Git
* Postman or curl
* Kubernetes and `kubectl` (for Kubernetes deployment testing)

Verify the installations:

```bash
java -version
mvn -version
docker --version
docker compose version
kubectl version --client
```

## 2. Configure the Application

Ensure the database configuration in `src/main/resources/application.yml` matches the local MySQL credentials.

The application uses the following default configuration:

* Database: `countrydb`
* Username: `countryapp`
* Password: `countrypass`
* Database URL: `jdbc:mysql://localhost:3306/countrydb?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC`
* Application port: `8080`

These values are for local development only. Use appropriate credentials for other environments.

## 3. Start the MySQL Database

From the project root, start MySQL using Docker Compose:

```bash
docker compose up -d mysql
```

Verify the container is running:

```bash
docker compose ps
```

Check the database logs if necessary:

```bash
docker compose logs mysql
```

Wait until MySQL is ready before starting the Spring Boot application.

## 4. Build and Run the Application

Build the application and execute its tests:

```bash
mvn clean verify
```

This command also runs the Maven build lifecycle, including SOAP client source generation if configured in the project.

If the build succeeds, start the application:

```bash
mvn spring-boot:run
```

Alternatively, package and run the JAR:

```bash
mvn clean package
java -jar target/country-integration-0.0.1-SNAPSHOT.jar
```

Confirm the startup logs show that Spring Boot has started successfully and that the database connection was established.

The application should be accessible at:

`http://localhost:8080`

## 5. Test the Health Endpoint

Use curl or Postman to check application health:

```bash
curl http://localhost:8080/actuator/health
```

Expected result when healthy:

```json
{
  "status": "UP"
}
```

The response may include additional component details depending on the Actuator configuration.

## 6. Test Country Creation and SOAP Integration

Send a POST request to the country creation endpoint.

Example using curl:

```bash
curl -i -X POST http://localhost:8080/api/v1/countries \
  -H "Content-Type: application/json" \
  -d '{"name":"Tanzania"}'
```

Expected behavior:

1. The application receives the country name.
2. The country name is normalized to sentence case.
3. The SOAP client calls `CountryISOCode`.
4. The returned ISO code is used to call `FullCountryInfo`.
5. The country details and languages are mapped to the application entities.
6. The country information is saved to MySQL.
7. The API returns the saved country information.

The exact HTTP status and response body depend on the controller implementation. A successful creation would commonly return `201 Created`.

## 7. Test CRUD Endpoints

Use the actual endpoint paths and response DTOs implemented in the project.

### Retrieve all countries

```bash
curl -i http://localhost:8080/api/v1/countries
```

Expected behavior: returns the stored countries.

### Retrieve a country by ID

```bash
curl -i http://localhost:8080/api/v1/countries/1
```

Expected behavior: returns the country matching the supplied database ID, or an appropriate `404 Not Found` response if it does not exist.

### Update a country

Use the update endpoint implemented by the controller. For example:

```bash
curl -i -X PUT http://localhost:8080/api/v1/countries/1 \
  -H "Content-Type: application/json" \
  -d '{"name":"Tanzania"}'
```

Adjust the JSON body to match the actual update request DTO.

Expected behavior: updates the specified country or returns an appropriate error if the country does not exist.

### Delete a country

```bash
curl -i -X DELETE http://localhost:8080/api/v1/countries/1
```

Expected behavior: deletes the specified country and its associated language records according to the entity relationship configuration.

## 8.Test caching
```bash
curl.exe "http://localhost:8080/actuator/metrics/cache.gets?tag=result:hit"
curl.exe "http://localhost:8080/actuator/metrics/cache.gets?tag=result:miss"
```

POST the same country twice. The first call logs two SOAP call ok lines (cache misses). The repeat logs none, and the hit count goes up by one.

## 9. Test Validation and Error Handling

Test a request with a missing country name:

```bash
curl -i -X POST http://localhost:8080/api/v1/countries \
  -H "Content-Type: application/json" \
  -d '{"name":""}'
```

Expected behavior: returns a validation error, normally `400 Bad Request`.

Test retrieval of a nonexistent country:

```bash
curl -i http://localhost:8080/api/v1/countries/999999
```

Expected behavior: returns `404 Not Found` if the requested country does not exist.

Also test malformed JSON, duplicate country records, and SOAP service failures. Confirm that errors are returned in a consistent response format without exposing stack traces or sensitive information.

## 10. Verify Database Persistence

Inspect the database using a MySQL client or execute a query inside the container:

```bash
docker compose exec mysql mysql \
  -u country_user -p country_db
```

Enter the configured database password when prompted.

Then run:

```sql
SHOW TABLES;
SELECT * FROM country_info;
SELECT * FROM languages;
```

Verify that successfully created countries and their language records have been stored.

## 11. Run Automated Tests

Run all tests:

```bash
mvn test
```

The test suite should cover relevant application behavior, including:

* Country name normalization and validation
* Country service business logic
* CRUD operations
* Error handling
* SOAP response mapping
* Repository persistence

Use mocked SOAP responses for unit tests so that these tests do not depend on the external SOAP service being available.

## 12. Run the Application on Kubernetes

Ensure the Docker image referenced in `k8s/app.yaml` has been built and is accessible to the Kubernetes cluster.

Apply the resources:

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/configmap.yaml
kubectl apply -f k8s/secret.yaml
kubectl apply -f k8s/mysql.yaml
```

Wait for MySQL:

```bash
kubectl rollout status deployment/mysql \
  -n country-integration --timeout=180s
```

Deploy the application:

```bash
kubectl apply -f k8s/app.yaml
```

Wait for the application:

```bash
kubectl rollout status deployment/country-integration \
  -n country-integration --timeout=180s
```

Check the deployment status:

```bash
kubectl get pods -n country-integration
kubectl get services -n country-integration
kubectl get pvc -n country-integration
```

The application Pods should become ready, and the MySQL persistent volume claim should be bound.

## 13. Test the Kubernetes Deployment

Forward the application Service to your local machine:

```bash
kubectl port-forward service/country-integration \
  8080:8080 -n country-integration
```

In another terminal, test health:

```bash
curl -i http://localhost:8080/actuator/health
```

Repeat the country creation, retrieval, update, deletion, and validation tests against `http://localhost:8080`.

Inspect application logs if a test fails:

```bash
kubectl logs deployment/country-integration \
  -n country-integration
```

## 14. Expected Results

The application is considered successfully tested when:

* The project builds successfully.
* The application starts and connects to MySQL.
* The health endpoint reports a healthy status.
* Country creation successfully integrates with the SOAP service.
* Country details and languages are persisted in MySQL.
* CRUD endpoints return the expected responses.
* Invalid requests produce appropriate HTTP error codes.
* Automated tests pass.
* Kubernetes resources become healthy and the application is accessible through port forwarding.

## 15. Troubleshooting

If the application fails to start, inspect the Spring Boot logs and confirm the database is ready.

If SOAP requests fail, verify the configured SOAP endpoint, outbound network access, timeout settings, and SOAP response mapping.

If Kubernetes Pods fail to start, inspect the Pod description, recent events, and container logs:

```bash
kubectl describe pod POD_NAME -n country-integration
kubectl logs POD_NAME -n country-integration
kubectl get events -n country-integration
```

Resolve the underlying issue and repeat the relevant tests.
