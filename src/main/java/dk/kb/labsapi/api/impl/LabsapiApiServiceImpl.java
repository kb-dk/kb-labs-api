package dk.kb.labsapi.api.impl;

import dk.kb.labsapi.api.*;
import java.util.ArrayList;
import dk.kb.labsapi.model.ErrorDto;
import java.util.List;
import java.util.Map;

import dk.kb.webservice.exception.ServiceException;
import dk.kb.webservice.exception.InternalServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.io.File;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import javax.validation.constraints.NotNull;
import javax.ws.rs.*;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.ext.ContextResolver;
import javax.ws.rs.ext.Providers;
import javax.ws.rs.core.MediaType;
import org.apache.cxf.jaxrs.model.wadl.Description;
import org.apache.cxf.jaxrs.model.wadl.DocTarget;
import org.apache.cxf.jaxrs.ext.MessageContext;
import org.apache.cxf.jaxrs.ext.multipart.*;

import io.swagger.annotations.Api;

/**
 * labsapi
 *
 * <p>This pom can be inherited by projects wishing to integrate to the SBForge development platform. 
 *
 */
public class LabsapiApiServiceImpl implements LabsapiApi {
    private Logger log = LoggerFactory.getLogger(this.toString());



    /* How to access the various web contexts. See https://cxf.apache.org/docs/jax-rs-basics.html#JAX-RSBasics-Contextannotations */

    @Context
    private transient UriInfo uriInfo;

    @Context
    private transient SecurityContext securityContext;

    @Context
    private transient HttpHeaders httpHeaders;

    @Context
    private transient Providers providers;

    @Context
    private transient Request request;

    @Context
    private transient ContextResolver contextResolver;

    @Context
    private transient HttpServletRequest httpServletRequest;

    @Context
    private transient HttpServletResponse httpServletResponse;

    @Context
    private transient ServletContext servletContext;

    @Context
    private transient ServletConfig servletConfig;

    @Context
    private transient MessageContext messageContext;


    /**
     * Retrieve metadata fields from articles in the newspaper collection at http://mediestream.dk/ (a part of the Royal Danish Library). The export is restricted to newspapers older than 100 years and will be sorted by publication date.
     * 
     * @param query: A query for the newspapers to export metadata for.\\n The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis\\n A filter restricting the result to newspapers older than 100 years will be automatically applied
     * 
     * @param fields: The fields to export.\\n * link: A hyperlink to the Mediestream page for the article\\n * recordID: The unique ID of the article in the Mediestream system\\n * timestamp: The publication date for the article in ISO format YYYY-MM-DDTHH:MM:SS\\n * pwa: Predicted Word Accuracy for the OCR text on a scale from 0 to 100\\n * cer: \\n * fulltext_org: The original OCR text for the article\\n * pageUUID: The ID for the page that the article appears on\\n * editionUUID: The ID for the edition that the page with the article belongs to\\n * editionId: Human readable version of the edition\\n * titleUUID: TODO: Explain this\\n * familyId: TODO: Explain this\\n * newspaper_page: The page number of the addition that the article appears on\\n * newspaper_edition: TODO: Explain this\\n * lplace: TODO: Explain this\\n * location_name: Location names extracted from the text (low quality entity recognition)\\n * location_coordinates: Coordinates for places from location_name
     * 
     * @param max: The maximum number of articles to return, -1 to return all articles. *WARNING* setting this to more than 50 when using the Swagger-UI to test will probably result in the browser locking up
     * 
     * @param structure: The major parts of the delivery.\\n * comments: Metadata for the export (query, export time...), prefixed with #\\n * header: The export field names\\n * content: The export content itself
     * 
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   <li>code = 400, message = "Invalid Argument", response = String.class</li>
      *   <li>code = 406, message = "Not Acceptable", response = ErrorDto.class</li>
      *   <li>code = 500, message = "Internal Error", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public javax.ws.rs.core.StreamingOutput exportFields(String query, List<String> fields, Long max, List<String> structure) throws ServiceException {
        // TODO: Implement...
    
        
        try{ 
            httpServletResponse.setHeader("Content-Disposition", "inline; filename=\"filename.ext\"");
            return output -> output.write("Magic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e){
            throw handleException(e);
        }
    
    }

    /**
     * Perform a search with the given query, returning only the number of hits. Typically used to get an estimate for the result size for an export
     * 
     * @param query: A query for the newspaper articles.\\n The query can also be tested at http://www2.statsbiblioteket.dk/mediestream/avis for a more interactive result.\\n A filter restricting the result to newspapers older than 100 years will be automatically applied
     * 
     * @return <ul>
      *   <li>code = 200, message = "OK", response = Long.class</li>
      *   <li>code = 500, message = "Internal Error", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public javax.ws.rs.core.StreamingOutput hitCount(String query) throws ServiceException {
        // TODO: Implement...
    
        
        try{ 
            httpServletResponse.setHeader("Content-Disposition", "inline; filename=\"filename.ext\"");
            return output -> output.write("Magic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e){
            throw handleException(e);
        }
    
    }

    /**
     * Ping the server to check if the server is reachable.
     * 
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   <li>code = 406, message = "Not Acceptable", response = ErrorDto.class</li>
      *   <li>code = 500, message = "Internal Error", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public String ping() throws ServiceException {
        // TODO: Implement...
    
        
        try{ 
            String response = "bUnk13";
        return response;
        } catch (Exception e){
            throw handleException(e);
        }
    
    }


    /**
    * This method simply converts any Exception into a Service exception
    * @param e: Any kind of exception
    * @return A ServiceException
    * @see dk.kb.webservice.ServiceExceptionMapper
    */
    private ServiceException handleException(Exception e) {
        if (e instanceof ServiceException) {
            return (ServiceException) e; // Do nothing - this is a declared ServiceException from within module.
        } else {// Unforseen exception (should not happen). Wrap in internal service exception
            log.error("ServiceException(HTTP 500):", e); //You probably want to log this.
            return new InternalServiceException(e.getMessage());
        }
    }

}
