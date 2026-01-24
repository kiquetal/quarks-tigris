# quarkus-tigris

This project uses Quarkus, the Supersonic Subatomic Java Framework, to create a backend service that handles file uploads to Amazon S3. It also includes an Angular frontend for user interaction.

## Technologies Used

*   **Quarkus**: A full-stack, Kubernetes-native Java framework tailored for GraalVM and OpenJDK HotSpot, crafted from the best of breed Java libraries and standards.
*   **Angular**: A platform for building mobile and desktop web applications.
*   **Quinoa**: A Quarkus extension that simplifies the integration of a web UI, like one built with Angular, into a Quarkus application.
*   **Amazon S3**: A scalable object storage service used to store the uploaded MP3 files.

## Project Structure

The project is organized as follows:

-   `src/main/java`: Contains the Java source code for the Quarkus backend.
-   `src/main/resources`: Contains the configuration files for the Quarkus application, such as `application.properties`.
-   `src/main/webui`: Contains the Angular frontend application.

## Running the application in dev mode

You can run your application in dev mode that enables live coding using:

```shell script
./mvnw quarkus:dev
```

This command will start both the Quarkus backend and the Angular frontend in development mode. 

**Important**: Access the application at `http://localhost:8080` (not port 4200). 

### URL Structure

Quinoa automatically routes requests based on the URL path:

- **Frontend Routes** (Angular SPA):
  - `http://localhost:8080/` → Angular app (redirects to `/passphrase`)
  - `http://localhost:8080/passphrase` → Passphrase page
  - `http://localhost:8080/upload` → Upload page

- **Backend API Routes** (Quarkus REST):
  - `http://localhost:8080/api/validate-passphrase` → Passphrase validation endpoint
  - `http://localhost:8080/api/upload` → File upload endpoint

- **OpenAPI/Swagger Documentation**:
  - `http://localhost:8080/swagger` → OpenAPI specification (JSON)
  - `http://localhost:8080/swagger-ui` → Swagger UI (interactive API documentation)

Quinoa intelligently proxies the Angular dev server through Quarkus, so:
- Any path starting with `/api` is handled by your Quarkus backend
- All other paths are served by the Angular application
- No additional proxy configuration is needed!

> **_NOTE:_** Quarkus now ships with a Dev UI, which is available in dev mode only at `http://localhost:8080/q/dev/`.

## Packaging and running the application

The application can be packaged using:

```shell script
./mvnw package
```

It produces the `quarkus-run.jar` file in the `target/quarkus-app/` directory.
Be aware that it’s not an _über-jar_ as the dependencies are copied into the `target/quarkus-app/lib/` directory.

The application is now runnable using `java -jar target/quarkus-app/quarkus-run.jar`.

If you want to build an _über-jar_, execute the following command:

```shell script
./mvnw package -Dquarkus.package.jar.type=uber-jar
```

The application, packaged as an _über-jar_, is now runnable using `java -jar target/*-runner.jar`.

## Creating a native executable

You can create a native executable using:

```shell script
./mvnw package -Dnative
```

Or, if you don't have GraalVM installed, you can run the native executable build in a container using:

```shell script
./mvnw package -Dnative -Dquarkus.native.container-build=true
```

You can then execute your native executable with: `./target/quarkus-tigris-1.0.0-SNAPSHOT-runner`

If you want to learn more about building native executables, please consult <https://quarkus.io/guides/maven-tooling>.

## Related Guides

- REST ([guide](https://quarkus.io/guides/rest)): A Jakarta REST implementation utilizing build time processing and Vert.x. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it.
- REST Jackson ([guide](https://quarkus.io/guides/rest#json-serialisation)): Jackson serialization support for Quarkus REST. This extension is not compatible with the quarkus-resteasy extension, or any of the extensions that depend on it
- Reactive Messaging Nats Jetstream ([guide](https://docs.quarkiverse.io/quarkus-reactive-messaging-nats-jetstream/dev/)): Easily integrate to nats.io JetStream.
- Amazon S3 ([guide](https://docs.quarkiverse.io/quarkus-amazon-services/dev/amazon-s3.html)): Connect to Amazon S3 cloud storage

## Provided Code

### REST

Easily start your REST Web Services

[Related guide section...](https://quarkus.io/guides/getting-started-reactive#reactive-jax-rs-resources)
