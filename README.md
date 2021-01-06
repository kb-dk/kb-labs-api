# kb-labs-api
   
Experimental API for accessing Danish cultural heritage data from labs.kb.dk

This project cannot run outside of the firewalls of the Royal Danish Library.
The code has been made open source for inspiration only and no support is provided.

## What it does

The current experimental API acts as a filtering proxy for 140+ year old Danish newspaper data,
also available through the human oriented discovery interface at [Mediestream](http://mediestream.dk/).
It allows for export of the OCR text fron newspaper articles as well as selected metadata fields,
based on a query from the user.

It is not currently (2021-01-06) publicly available, but hopefully this will change within a week or two.

In the future, the API is expected to be extended to provide access to more open data.


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

The Swagger driven UI for the labsapi is available at [http://localhost:8080/labsapi/api/](http://localhost:8080/labsapi/api/).

