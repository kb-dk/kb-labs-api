package dk.kb.labsapi.api.impl;

import dk.kb.labsapi.api.*;
import dk.kb.labsapi.model.ErrorDto;
import java.io.File;
import dk.kb.labsapi.model.HitsDto;
import dk.kb.labsapi.model.TimelineEntryDto;

import dk.kb.webservice.exception.ServiceException;
import dk.kb.webservice.exception.InternalServiceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
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
 * <p>Experimental API for publicly available data and metadata at the Royal Danish Library.  If you have any ideas or experience any issues please report them at the projects github issues [page](https://github.com/kb-dk/kb-labs-api/issues). 
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
     * @param filter: Optional filter for the timeline statistics. Filter restricts the result set, just as query does, with the differences that filter is always qualified, e.g. &#x60;lplace:København&#x60; and that filter is also used when calculating the percentage.  The match-all filter &#x60;*:*&#x60; mimicks the behaviour of [Smurf](http://labs.statsbiblioteket.dk/smurf/) while the filter &#x60;recordBase:doms_aviser&#x60; restricts to newspaper articles, as opposed to both articles (which contains fulltext) and pages (which only contains other metadata). Specifying an empty filter causes &#x60;recordBase:doms_aviser&#x60; to be used. 
     * 
     * @param granularity: The granularity of the timeline. The finer the granularity, the longer the processing time.
     * 
     * @param startTime: The starting point of the timeline (inclusive), expressed as YYYY or YYYY-MM. This cannot be earlier than 1666. 
     * 
     * @param endTime: The ending point of the timeline (inclusive), expressed as YYYY or YYYY-MM. If blank, the current point in time is used.  Note: As of 2021, Mediestream does not contain newspapers later than 2013. 
     * 
     * @param elements: The elements for the timeline. The element &#39;unique_titles&#39; is special as it, as the name signals, the number of unique titles and not the sum of instances. 
     * 
     * @param structure: |The major parts of the delivery.| | |---|---| |comments|Metadata for the timeline (query, export time...), prefixed with # in CSV.| |header|The export field names. Only relevant for CSV as it is implicit in JSON.| |content|The export content itself.| 
     * 
     * @param format: |The delivery format.| | |---|---| |CSV|Comma separated, missing values represented with nothing, strings encapsulated in quotes.| |JSON|Valid JSON in the form of a single array of TimelineEntrys.| 
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
    
        
    
        return null;
    }

    /**
     * Export data from old newspapers at http://mediestream.dk/
     * 
     * @param query: A query for the newspapers to export metadata for.  The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis  A filter restricting the result to newspapers older than 140 years will be automatically applied. This means that the API returns material from 1880 and before.  Even though a query like &#39;*cykel AND lplace:København*&#39; might return more and newer results in a [Mediestream search](https://www2.statsbiblioteket.dk/mediestream/avis/search/cykel%20AND%20lplace%3AK%C3%B8benhavn) the response from the API is limited to material at least 140 years old. 
     * 
     * @param fields: |The fields to export.|Description.| |--- |---| |Link|A hyperlink to the Mediestream page for the article.| |recordID|The unique ID of the article in the Mediestream system.| |timestamp|The publication date for the article in ISO format YYYY-MM-DDTHH:MM:SS| |pwa|Predicted Word Accuracy for the OCR text on a scale from 0 to 100, where 100 is perfect.| |cer|Character Error Rate (estimated) of the OCR on a scale from 0 to 1, where 0 is perfect| |fulltext_org|The original OCR text for the article.| |pageUUID|The ID for the page that the article appears on.| |editionUUID|The ID for the edition that the page with the article belongs to.| |editionId|Human readable version of the edition.| |titleUUID|The ID for the title of the newspaper, which the article is from.| |familyId|The general name of the newspaper. The name of newspapers can change over time, this familyId will always be the same even though the title of the newspaper changes a little.| |newspaper_page|The page number of the edition that the article appears on.| |newspaper_edition|The edition of the newspaper. Newspapers can change during the day, this data tells if the edition has changed.| |lplace|Place of publication. Where the paper was published.| |location_name|Location names extracted from the text (low quality entity recognition).| |location_coordinates |Coordinates for places from location_name.| 
     * 
     * @param max: The maximum number of articles to return, -1 to return all articles.  Setting this to more than 20 ,when using the Swagger-UI, will present a download link instead of directly showing the result. 
     * 
     * @param structure: |The major parts of the delivery| | |---|---| |comments|Metadata for the export (query, export time...), prefixed with # in CSV, encapsulated in &lt;--!XML comment--&gt; in XML  and not shown in JSON.| |header|The export field names. Only relevant for CSV.| |content|The export content itself.| 
     * 
     * @param format: |The delivery format.| | |---|---| |CSV|Comma separated, missing values represented with nothing, strings encapsulated in quotes.| |JSON|Valid JSON in the form of a single array of Documents.| |JSONL|Newline separated single-line JSON representations of Documents.| |TXT|Plain text output. UTF-8 Encoded.| |XML|XML output. UTF-8 Encoded. &lt;br/&gt;This output format is [Voyant](https://voyant-tools.org/docs/#!/guide/about) compliant and makes it possible to export newspaper data directly to Voyant.| 
     * 
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   <li>code = 400, message = "Invalid Argument", response = String.class</li>
      *   <li>code = 406, message = "Not Acceptable", response = ErrorDto.class</li>
      *   <li>code = 500, message = "Internal Error", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * Retrieve metadata fields from articles in the newspaper collection at http://mediestream.dk/ (a part of the [Royal Danish Library](https://kb.dk)). The export is restricted to newspapers older than 140 years and will be sorted by publication date. 
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public javax.ws.rs.core.StreamingOutput exportFields(String query, List<String> fields, Long max, List<String> structure, String format) throws ServiceException {
        // TODO: Implement...
    
        
    
        return null;
    }

    /**
     * Export images from newspapers
     * 
     * @param query: A query for the newspapers to export metadata for.  The query can be tested at http://www2.statsbiblioteket.dk/mediestream/avis  A filter restricting the result to newspapers older than 140 years will be automatically applied. This means that the API returns material from 1880 and before.  Even though a query like &#39;*cykel AND lplace:København*&#39; might return more and newer results in a [Mediestream search](https://www2.statsbiblioteket.dk/mediestream/avis/search/cykel%20AND%20lplace%3AK%C3%B8benhavn) the response from the API is limited to material at least 140 years old. 
     * 
     * @param max: Number of max results to return. For all results use -1. 
     * 
     * @param startTime: The starting year of the query (inclusive), expressed as YYYY, YYYY-MM or YYYY-MM-DD. This cannot be earlier than 1666 as we do not have material from before 1666. 
     * 
     * @param endTime: The ending point of the query (inclusive), expressed as YYYY, YYYY-MM or YYYY-MM-DD. If blank, the year 1880 is used. The API does not expose data from after 1880. 
     * 
     * @return <ul>
      *   <li>code = 200, message = "OK", response = File.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * Export images from pages of newspapers that contains the given query. 
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public javax.ws.rs.core.StreamingOutput exportImages(String query, Integer max, Integer startTime, Integer endTime) throws ServiceException {
        // TODO: Implement...
    
        
    
        return null;
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
     * @param field: |The field to facet.|Note that it is case sensitive.| |---|---| |familyId|The general name of the newspaper. The name of newspapers can change over time, this familyId will always be the same even though the title of the newspaper changes a little.| |lvx|The specific name of the newspaper.| |lplace|Place of publication. Where the paper was published.| |py|Publication year.| 
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
    
        
    
        return null;
    }

    /**
     * Deliver ALTO XML for a single page from http://mediestream.dk/
     * 
     * @param id: The ID for the ALTO to retrieve. This can be  * a [Mediestream URL](https://www2.statsbiblioteket.dk/mediestream/avis/record/doms_aviser_page:uuid:a9990f12-e9f0-4b1e-becc-e0d4bf514586/query/heste) to a single page * an &#x60;UUID&#x60;  such as &#x60;a9990f12-e9f0-4b1e-becc-e0d4bf514586&#x60;. &#x60;UUID&#x60;s can be extracted from the Mediestream URL directly or from &#x60;recordID&#x60;s or &#x60;pageUUID&#x60;s from field exports.   * a &#x60;recordID&#x60; for an article such as &#x60;doms_newspaperCollection:uuid:1620bf3b-7801-4a34-b2b9-fd8db9611b76-segment_19&#x60;. &#x60;recordID&#x60;s can be retrieved as part of the field export endpoint. 
     * 
     * @return <ul>
      *   <li>code = 200, message = "OK", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * This endpoint delivers [ALTO XML](https://www.loc.gov/standards/alto/) for newspaper material that is 140+ years old.  [ALTO XML](https://www.loc.gov/standards/alto/) contains OCR text with bounding boxes from sections  down to single word granularity. Where possible, sections are connected through attributes to form articles, which are the atomic documents discovered through [Mediestream](https://mediestream.dk/).  **Warning:** ALTO XML can be quite large. If the ALTO is requested through the OpenAPI GUI,  the browser might hang for a minute before showing the result. 
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public String getALTO(String id) throws ServiceException {
        // TODO: Implement...
    
        
    
        return null;
    }

    /**
     * Perform a search with the given query, returning only the number of hits, divided into publicly available data (&gt; 140 years) and restricted data. Typically used to get an estimate for the result size for an export
     * 
     * @param query: A query for the newspaper articles.  The query can also be tested at http://www2.statsbiblioteket.dk/mediestream/avis for a more interactive result. 
     * 
     * @return <ul>
      *   <li>code = 200, message = "OK", response = HitsDto.class</li>
      *   <li>code = 500, message = "Internal Error", response = String.class</li>
      *   </ul>
      * @throws ServiceException when other http codes should be returned
      *
      * @implNote return will always produce a HTTP 200 code. Throw ServiceException if you need to return other codes
     */
    @Override
    public HitsDto hitCount(String query) throws ServiceException {
        // TODO: Implement...
    
        
    
        return null;
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
    
        
    
        return null;
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
