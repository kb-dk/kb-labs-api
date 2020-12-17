# kb-labs-api
   
Experimental API for accessing Danish cultural heritage data from labs.kb.dk

This project cannot run outside of the firewalls of the Royal Danish Library.
The code has been made open source for inspiration only and no support is provided.

## Requirements

 * Java 11
 * Maven 3
 * Access to backing services at the Royal Danish Library (not a chance if you are not an employee)

## Using the project

After a fresh checkout or after the `openapi.yaml` specification has changes, the `api` and the `model` files 
must be generated. This is done by calling 
```
mvn package
```

Jetty is enabled, so testing the webservice can be done by running
Start a Jetty web server with the application:
```
mvn jetty:run
```

The default port is 8080 and the default Hello World service can be accessed at
<http://localhost:8080/java-webapp/api/hello>
where "java-webapp" is your artifactID from above.

The Swagger-UI is available at <http://localhost:8080/java-webapp/api/api-docs?url=openapi.json>
which is the location that <http://localhost:8080/java-webapp/api/> will redirect to.


## About OpenAPI 1.3

[OpenAPI 1.3](https://swagger.io/specification/) generates interfaces and skeleton code for webservices.
It also generates online documentation, which includes sample calls and easy testing of the endpoints.

Everything is defined centrally in the file [openapi.yaml](src/main/openapi/openapi.yaml).

The interfaces and models generated from the OpenAPI definition are stored in `target/generated-sources/`.
They are recreated on each `mvn package`.

Skeleton classes are added to `/src/main/java/${project.package}/api/impl/` if they are not already present (there is no overwriting).
A reference to the classes must be added manually to `/src/main/java/${project.package}/webservice/Application` or its equivalent.

**Note:** The classes in `/src/main/java/${project.package}api/impl/` will be instantiated for each REST-call. Persistence between calls must be handled as statics of outside of the classes.

### OpenAPI and exceptions

When an API end point shall return anything else than the default response (HTTP response code 200),
this is done by throwing an exception.

See how we map exceptions to responsecodes in [ServiceExceptionMapper](./src/main/java/dk/kb/webservice/ServiceExceptionMapper.java) 

See [ServiceException](./src/main/java/dk/kb/webservice/exception/ServiceException.java) and its specializations for samples.

