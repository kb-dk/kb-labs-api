package dk.kb.labsapi.api.impl;

import dk.kb.labsapi.api.*;
import java.util.ArrayList;
import dk.kb.labsapi.model.ErrorDto;
import java.util.List;
import java.util.Map;
import dk.kb.labsapi.model.TimelineEntryDto;

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
 * <p>Experimental API for publicly available data and metadata at the Royal Danish Library 
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
     * Extract statistics for the newspaper corpus at http://mediestream.dk/
     * 
     * @param query: Optional query for the timeline statistics. If no query is given, all data are selected. The output will be a number of timeslices with the given granularity, followed by a summary.  The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis for a more interactive result.  Note: Queries other than &#39;*:*&#39; will cause the numbers for pages and editions to be approximate. 
     * 
     * @param filter: Optional filter for the timeline statistics. Filter restricts the result set, just as query does, with the differences that filters are always qualified, e.g. &#x60;lplace:København&#x60; and that filter is also used when calculating the percentage.  The filter &#x60;*:*&#x60; mimicks the behaviour at [Smurf](http://labs.statsbiblioteket.dk/smurf/) while the filter &#x60;recordBase:doms_aviser&#x60; restricts to newspaper articles, as opposed to both articles (which contains fulltext) and pages (which only contains other metadata). 
     * 
     * @param granularity: The granularity of the timeline. The finer the granularity, the longer the processing time.
     * 
     * @param startTime: The starting point of the timeline (inclusive), expressed as YYYY or YYYY-MM. This cannot be earlier than 1666. 
     * 
     * @param endTime: The ending point of the timeline (inclusive), expressed as YYYY or YYYY-MM. If blank, the current point in time is used.  Note: As of 2021, Mediestream does not contain newspapers later than 2013. 
     * 
     * @param elements: The elements for the timeline. The element &#39;unique_titles&#39; is special as it, as the name signals, the number of unique titles and not the sum of instances. 
     * 
     * @param structure: The major parts of the delivery.  * comments: Metadata for the timeline (query, export time...), prefixed with # in CSV * header: The export field names. Only relevant for CSV as it is implicit in JSON * content: The export content itself 
     * 
     * @param format: The delivery format.  * CSV: Comma separated, missing values represented with nothing, strings encapsulated in quotes * JSON: Valid JSON in the form of a single array of TimelineEntrys 
     * 
     * @return <ul>
      *   <li>code = 200, message = "OK", response = TimelineEntryDto.class, responseContainer = "List"</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * Extracts a timeline of statistical elements, optionally based on a query.  The data are from articles in the newspaper collection at http://mediestream.dk/ (a part of the [Royal Danish Library](https://kb.dk)).  Note: Depending on query and granularity, the timeline stats can take up to a few minutes to extract. Patience is adviced. 
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public javax.ws.rs.core.StreamingOutput aviserStatsTimeline(String query, String filter, String granularity, String startTime, String endTime, List<String> elements, List<String> structure, String format) throws ServiceException {
        // TODO: Implement...
    
        
        try{ 
            httpServletResponse.setHeader("Content-Disposition", "inline; filename=\"filename.ext\"");
            return output -> output.write("Magic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e){
            throw handleException(e);
        }
    
    }

    /**
     * Export data from old newspapers at http://mediestream.dk/
     * 
     * @param query: A query for the newspapers to export metadata for.  The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis  A filter restricting the result to newspapers older than 140 years will be automatically applied 
     * 
     * @param fields: The fields to export.  * link: A hyperlink to the Mediestream page for the article * recordID: The unique ID of the article in the Mediestream system * timestamp: The publication date for the article in ISO format YYYY-MM-DDTHH:MM:SS * pwa: Predicted Word Accuracy for the OCR text on a scale from 0 to 100 * cer: * fulltext_org: The original OCR text for the article * pageUUID: The ID for the page that the article appears on * editionUUID: The ID for the edition that the page with the article belongs to * editionId: Human readable version of the edition * titleUUID: TODO: Explain this * familyId: TODO: Explain this * newspaper_page: The page number of the addition that the article appears on * newspaper_edition: TODO: Explain this * lplace: TODO: Explain this * location_name: Location names extracted from the text (low quality entity recognition) * location_coordinates: Coordinates for places from location_name 
     * 
     * @param max: The maximum number of articles to return, -1 to return all articles.  **WARNING** setting this to more than 50 when using the Swagger-UI to test will probably result in the browser locking up 
     * 
     * @param structure: The major parts of the delivery.  * comments: Metadata for the export (query, export time...), prefixed with # in CSV, not shown in JSON * header: The export field names. Only relevant for CSV * content: The export content itself 
     * 
     * @param format: The delivery format.  * CSV: Comma separated, missing values represented with nothing, strings encapsulated in quotes * JSON: Valid JSON in the form of a single array of Documents * JSONL: Newline separated single-line JSON representations of Documents 
     * 
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   <li>code = 400, message = "Invalid Argument", response = String.class</li>
      *   <li>code = 406, message = "Not Acceptable", response = ErrorDto.class</li>
      *   <li>code = 500, message = "Internal Error", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * Retrieve metadata fields from articles in the newspaper collection at http://mediestream.dk/ (a part of the [Royal Danish Library](https://kb.dk)). The export is restricted to newspapers older than 140 years and will be sorted by publication date.&#39; 
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public javax.ws.rs.core.StreamingOutput exportFields(String query, List<String> fields, Long max, List<String> structure, String format) throws ServiceException {
        // TODO: Implement...
    
        
        try{ 
            httpServletResponse.setHeader("Content-Disposition", "inline; filename=\"filename.ext\"");
            return output -> output.write("Magic".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e){
            throw handleException(e);
        }
    
    }

    /**
     * Facet on a field for newspapers data from http://mediestream.dk/
     * 
     * @param query: A query for the newspapers to export aggregates facet statistics for.  The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis  A filter restricting the result to newspapers older than 140 years will be automatically applied 
     * 
     * @param startTime: The starting point of the timeline (inclusive), expressed as YYYY, YYYY-MM or YYYY-MM-DD. This cannot be earlier than 1666. 
     * 
     * @param endTime: The ending point of the timeline (inclusive), expressed as YYYY, YYYY-MM or YYYY-MM-DD. If blank, the current point in time is used.  Note: As of 2021, Mediestream does not contain newspapers later than 2013. 
     * 
     * @param field: The field to facet. Note that it is case sensitive.  * familyId: The general name of the newspaper * lvx: The specific name of the newspaper * lplace: \&quot;Udgivelsessted\&quot; / publication location. Where the paper was published : py: Publication year 
     * 
     * @param sort: The sort order of the facet content.
     * 
     * @param limit: The maximum number of entries to return for a facet field.
     * 
     * @param format: The delivery format.  * CSV: Comma separated, strings encapsulated in quotes 
     * 
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * Faceting aggregates statistics for a given field based on a query. E.g. faceting on &#x60;familyID&#x60; delivers a list of all unique general newspaper titles for all the articles matching the query.  The data are from articles in the newspaper collection at http://mediestream.dk/ (a part of the [Royal Danish Library](https://kb.dk)). The data are restricted to newspapers older than 140 years and will be sorted by publication date.&#39; 
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public javax.ws.rs.core.StreamingOutput facet(String query, String startTime, String endTime, String field, String sort, Integer limit, String format) throws ServiceException {
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
     * @param query: A query for the newspaper articles.  The query can also be tested at http://www2.statsbiblioteket.dk/mediestream/avis for a more interactive result.  A filter restricting the result to newspapers older than 140 years will be automatically applied&#39; 
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
            String response = "S0Nej3";
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
