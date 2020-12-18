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
     * @param query: A query for the newspapers to export metadata for. The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis A filter restricting the result to newspapers older than 100 years will be automatically applied
     * 
     * @param fields: The fields to export. * link: A hyperlink to the Mediestream page for the article * recordID: The unique ID of the article in the Mediestream system * pwa: Predicted Word Accuracy for the OCR text on a scale from 0 to 100 * text: The text content for the article
     * 
     * @param dryrun: Dry run: If true, only the count of the number of matching articles is returned
     * 
     * @param structure: The major parts of the delivery. * comments: Metadata for the export (query, export time...), prefixed with # * header: The export field names * content: The export content itself
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
    public javax.ws.rs.core.StreamingOutput exportFields(String query, List<String> fields, Boolean dryrun, List<String> structure) throws ServiceException {
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
     * @param query: A query for the newspaper articles. The query can also be tested at http://www2.statsbiblioteket.dk/mediestream/avis for a more interactive result. A filter restricting the result to newspapers older than 100 years will be automatically applied
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
            String response = "E2D4deaWn";
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
