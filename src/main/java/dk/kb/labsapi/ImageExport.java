package dk.kb.labsapi;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.util.webservice.exception.InternalServiceException;
import dk.kb.util.yaml.YAML;
import org.apache.commons.io.IOUtils;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.GroupParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Exports images from text queries to Solr below mediestream.
 * Images are defined by alto boxes.
 */
public class ImageExport {
    private static final Logger log = LoggerFactory.getLogger(ImageExport.class);
    public static int pageSize;
    private static ImageExport instance;
    private final String ImageExportService;
    private static int startYear;
    private static int endYear;

    public static ImageExport getInstance() {
        instance = new ImageExport();
        return instance;
    }

    private ImageExport() {
        YAML conf;
        try {
            conf = ServiceConfig.getConfig().getSubMap(".labsapi.aviser");
        } catch (Exception e) {
            log.error("The configuration sub map '.labsapi.aviser' was not defined");
            ImageExportService = null;
            return;
        }
        pageSize = conf.getInteger(".solr.pagesize", 500);
        ImageExportService = conf.getString(".imageserver.url") + (conf.getString(".imageserver.url").endsWith("/") ? "" : "/");
        startYear = conf.getInteger(".imageserver.minYear");
        endYear = conf.getInteger(".imageserver.maxYear");
        log.info("Created ImageExport that exports images from this server: '{}'", ImageExportService);
    }

    /**
     * Get link to image from newspaper page with given query present in text.
     * @param query     to search for.
     * @param startTime is the earliest boundary for the query.
     * @param endTime   is the latest boundary for the query.
     * @param max       number of documents to query for illustrations.
     * @param output    to write images to as one combined zip file.
     */
    public void getImageFromTextQueryAsStream(String query, Integer startTime, Integer endTime, Integer max, OutputStream output) throws IOException {
        if (instance.ImageExportService == null) {
            throw new InternalServiceException("Illustration delivery service has not been configured, sorry");
        }
        // Query Solr
        QueryResponse response = illustrationSolrCall(query, startTime, endTime, max);
        // TODO: create metadata file, that has to be added to output zip
        Map<String, Object> metadataMap = makeMetadataMap(query, startTime, endTime);
        // Get illustration metadata
        List<IllustrationMetadata> illustrationMetadata = getMetadataForIllustrations(response);
        // TODO: Add minimum size for images to extract
        // Get illustration URLS
        List<URL> illustrationUrls = createLinkForAllIllustrations(illustrationMetadata);
        // Streams illustration from URL to zip file with all illustrations
        illustrationURLSToStream(illustrationUrls, illustrationMetadata, output, metadataMap);
    }

    /**
     * Create map of metadata for query
     * @param query used to query solr
     * @param startTime for the given query
     * @param endTime for query
     * @return a map of metadata used to provide a metadata file
     */
    public Map<String, Object> makeMetadataMap(String query, Integer startTime, Integer endTime) {
        Date date = new Date();

        Map<String, Object> metadataMap = new HashMap<>();
        metadataMap.put("title", "Metadata for image extraction from the Danish Royal Librarys newspaper API.");
        metadataMap.put("extraction_time", date.toString());
        metadataMap.put("query", query);
        metadataMap.put("query_start_year", startTime);
        metadataMap.put("query_end_year", endTime);
        metadataMap.put("license", "Creative Commons Public Domain Mark 1.0 License.");

        return metadataMap;
    }


    /**
     * Call Solr for input query and return fields needed to extract images from pages in the query.
     * @param query     to query Solr with.
     * @param startTime startTime is the earliest boundary for the query.
     * @param endTime   endTime is the latest boundary for the query.
     * @param max       number of results to return
     * @return a response containing specific metadata used to locate illustration on pages. The fields returned are the following: <em>pageUUID, illustration, page_width, page_height</em>
     */
     public QueryResponse illustrationSolrCall(String query, Integer startTime, Integer endTime, int max) throws IOException{
         // Check start and end times
         int usableStartTime = setUsableStartTime(startTime);
         int usableEndTime = setUsableEndTime(endTime);
         log.info("Usable start time is: " + usableStartTime);
         log.info("usable end time is: " + usableEndTime);
         if (usableStartTime > usableEndTime){
             log.error("The variable startTime is greater than endTime, which is not allowed. Please make startTime less than endTime.");
             throw new IOException("The variable startTime is greater than endTime, which is not allowed. Please make startTime less than endTime.");
         }

        // Construct solr query with filter
        String filter = "recordBase:doms_aviser AND py:[" + usableStartTime + " TO "+ usableEndTime + "]";
        log.info("The query gets filtered with the following filter: " + filter);
        SolrQuery solrQuery = new SolrQuery();
        solrQuery.setQuery(query);
        solrQuery.setFilterQueries(filter);
        solrQuery.setFields("pageUUID, illustration, page_width, page_height");
        solrQuery.setRows( Math.min(max == -1 ? Integer.MAX_VALUE : max, pageSize));
        solrQuery.setFacet(false);
        solrQuery.setHighlight(false);
        solrQuery.set(GroupParams.GROUP, false);

        QueryResponse response;
        try {
            response = SolrExport.getInstance().callSolr(solrQuery, true);
        } catch (Exception e) {
            String message = "Error calling Solr for query: " + query;
            log.warn(message, e);
            throw new RuntimeException(message);
        }
        return response;
    }

    /**
     * Evaluate that startTime is allowed in configuration of service.
     * @param startTime the chosen year for start of query.
     * @return startTime if allowed else return default start year.
     */
    public int setUsableStartTime(int startTime){
         if (startTime < startYear){
             log.info("Using startYear " + startYear);
             return startYear;
         } else {
             log.info("using startTime " + startTime);
             return startTime;
         }
    }

    /**
     * Evaluate that endTime is allowed in configuration of service.
     * @param endTime the chosen year for end of query.
     * @return endTime if allowed else return default end year.
     */
    public int setUsableEndTime(int endTime){
         if (endTime > endYear){
             log.info("Using endYear " + endYear);
             return endYear;
         } else {
             log.info("Using endTime" + endTime);
             return endTime;
         }
    }

    /**
     * Get metadata values for all illustrations from query.
     * The returned list contains an object of metadata from each single illustration.
     * These inner objects contain the following values in order: String id, int x, int y, int w, int h, string pageUUID, int pageWidth and int pageHeight.
     * X and Y are coordinates, w = width and h = height. pageUUID, pageWidth and pageHeight are related to the page, which the illustration has been extracted from
     * @return a list of objects consisting of the id, x, y, w, h, pageUUID, pageWidth and pageHeight values that are used to extract illustrations.
     */
     public List<IllustrationMetadata> getMetadataForIllustrations(QueryResponse solrResponse) {
         // TODO: This endpoint still returns some odd illustrations, which are clearly not illustrations nut flaws in the illustration boxes. However it works and these illustrations can be filtered away later by filtering small hights away
         List<IllustrationMetadata> illustrations = new ArrayList<>();
        // Parse result from query and save into a list of strings
        List<String> illustrationList = getIllustrationsList(solrResponse);
        // Map strings to illustration metadata
        for (String s : illustrationList) {
            // Create Illustration metadata object
            IllustrationMetadata singleIllustration = new IllustrationMetadata();
            singleIllustration.setData(s);
            // Add object to list of object
            illustrations.add(singleIllustration);
        }
        return illustrations;
    }

    /**
     * Parse Solr QueryResponse of newspaper pages into list of individual illustrations.
     * @param solrResponse to extract illustrations from.
     * @return a list of strings. Each string contains metadata for a single illustration from the input query response.
     */
     private List<String> getIllustrationsList(QueryResponse solrResponse) {
        SolrDocumentList responseList = new SolrDocumentList();
        responseList = solrResponse.getResults();
        List<String> illustrationList = new ArrayList<>();
        // Extract metadata from documents in solr response
        for (int i = 0; i<responseList.size(); i++) {
            String pageUUID = responseList.get(i).getFieldValue("pageUUID").toString();
            long pageWidth = (long) responseList.get(i).getFieldValue("page_width");
            long pageHeight = (long) responseList.get(i).getFieldValue("page_height");
            List<String> illustrations = (List<String>) responseList.get(i).getFieldValue("illustration");
            // Check if illustrations are present. If not, continue to next SolrDocument in list
            if (illustrations == null) {
                continue;
            }
            // Create metadata string for each illustration
            for (int j = 0; j< illustrations.size(); j++){
                illustrations.set(j, illustrations.get(j) + "," + pageUUID + "," + pageWidth + "," + pageHeight);
            }
            illustrationList.addAll(illustrations);
        }
        return illustrationList;
    }

    /**
     * Create a link for each illustration in the given metadataList.
     * @param metadataList a list of objects consisting of metadata used to construct links to the image server.
     * @return a list of URLs pointing to the imageserver, that contains the illustrations.
     */
    public List<URL> createLinkForAllIllustrations(List<IllustrationMetadata> metadataList) throws IOException {
        List<URL> illustrationUrls = new ArrayList<>();

        for (int i = 0; i< metadataList.size(); i++){
            illustrationUrls.add(createIllustrationLink(metadataList.get(i)));
        }
        return illustrationUrls;
    }

    /**
     * Construct link to illustration from metadata.
     * @param ill is a class containing metadata for an illustration.
     * @return a URL to the illustration described in the input metadata.
     */
    public URL createIllustrationLink(IllustrationMetadata ill) throws IOException {
        String baseURL = ServiceConfig.getConfig().getString("labsapi.aviser.imageserver.url");
        String baseParams = "&CVT=jpeg";
        String pageUuid = ill.getPageUUID() + ".jp2";
        String prePageUuid = "/" + pageUuid.charAt(0) + "/" + pageUuid.charAt(1) + "/" + pageUuid.charAt(2) + "/" + pageUuid.charAt(3) + "/";
        String region = calculateIllustrationRegion(ill.getX(), ill.getY(), ill.getW(), ill.getH(), ill.getPageWidth(), ill.getPageHeight());

        return new URL(baseURL+prePageUuid+pageUuid+region+baseParams);

    }

    /**
     * Calculate X & W coordinates, width and height for region parameter. Converts input pixel values to fractions of image size.
     * The image server containing the images uses the <a href="https://iipimage.sourceforge.io/documentation/protocol/">Internet Imaging Protocol</a>.
     * @param x coordinate for individual illustration.
     * @param y coordinate for individual illustration.
     * @param w represents the width of the individual illustration.
     * @param h represents the height of the individual illustration.
     * @param width of page where the illustration is found.
     * @param height of page where the illustration is found.
     * @return a region string that is ready to be added to an IIP query.
     */
    public String calculateIllustrationRegion(double x, double y, double w, double h, double width, double height){
        // Fraction calculation from: https://math.hws.edu/graphicsbook/c2/s1.html
        // newX = newLeft + ((oldX - oldLeft) / (oldRight - oldLeft)) * (newRight - newLeft))
        // newY = newTop + ((oldY - oldTop) / (oldBottom - oldTop)) * (newBottom - newTop)
        // The one used here is simplified because it has to return a fraction between 0 and 1.
        double calculatedX = x / width;
        double calculatedY = y / height;
        double calculatedW = w / width;
        double calculatedH = h / height;

        return String.format(Locale.ROOT, "&WID=%d&RGN=%1.5f,%1.5f,%1.5f,%1.5f", (int) w, calculatedX, calculatedY, calculatedW, calculatedH);
    }

    /**
     * Download an illustration from given URL and return it as a byte array.
     * @param url pointing to the image to download.
     * @return downloaded image as byte array.
     */
    private byte[] downloadSingleIllustration(URL url) {
        /*
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] illustrationAsByteArray = new byte[0];
        try (InputStream is = url.openStream()) {
            byte[] bytes = new byte[4096];
            int n;

            while ((n = is.read(bytes)) > 0) {
                baos.write(bytes, 0, n);
            }
            illustrationAsByteArray = baos.toByteArray();
        } catch (IOException e) {
            log.error("Failed to download illustration from " + url + " while reading bytes");
        }
        return illustrationAsByteArray;
         */
        try {
            return IOUtils.toByteArray(url);
        }  catch (IOException e) {
            log.error("Failed to download illustration from " + url + " while reading bytes");
            throw new RuntimeException(e);
        }
    }

    /**
     * Streams content of all URLs given in input list to an output stream that delivers a zip file of all images from the URLs.
     * @param illustrationURLs List of URls to get content from.
     * @param illustrationMetadata List of metadata for each image returned from the URL list. Used to construct filenames.
     * @param output output stream which holds the outputted zip file.
     */
    public void illustrationURLSToStream(List<URL> illustrationURLs, List<IllustrationMetadata> illustrationMetadata, OutputStream output, Map<String, Object> metadataMap) throws IOException {
        ZipOutputStream zos = new ZipOutputStream(output);
        zos.setLevel(Deflater.NO_COMPRESSION);

        // Add metadata file to zip
        try {
            addMetadataFileToZip(metadataMap, zos);
        } catch (Exception e) {
            String message = String.format(
                    Locale.ROOT,
                    "Exception adding metadata entry to ZIP with %s illustrationURLs and %s illustrationMetadatas",
                    illustrationURLs == null ? "null" : illustrationURLs.size(),
                    illustrationMetadata == null ? "null" : illustrationMetadata.size());
            log.warn(message, e);
            throw new IOException(message, e);
        }

        int count = 0;
        try {
            for (int i = 0; i < illustrationURLs.size() ; i++) {
                byte[] illustration = downloadSingleIllustration(illustrationURLs.get(i));
                String pageUuid = illustrationMetadata.get(i).getPageUUID();
                addToZipStream(illustration, String.format(Locale.ROOT, "pageUUID_%s_illustration_%03d.jpeg", pageUuid, count), zos);
                count += 1;
            }
            // Close the zip output stream
            zos.close();

        } catch (IOException e) {
            log.error("Error adding illustration to ZIP stream.");
            throw new IOException();
        }
    }

    /**
     * Add a metadata file to the created zip file before images are added to the file.
     * @param metadataMap containing metadata for the given solr query.
     * @param zos ZipOutputStream to write the metadata file to.
     */
    public void addMetadataFileToZip(Map<String, Object> metadataMap, ZipOutputStream zos) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter()).without(JsonGenerator.Feature.AUTO_CLOSE_TARGET);
        ZipEntry ze = new ZipEntry("metadata.json");
        zos.putNextEntry(ze);
        writer.writeValue(zos, metadataMap);
        zos.closeEntry();
    }

    /**
     * Add the content of a byte array to a given zip output stream.
     * @param data to write to zip stream as individual zip entry.
     * @param fileName given to data input in the zip stream.
     * @param zos ZipOutputStream that gets streamed to.
     */
    private void addToZipStream(byte[] data, String fileName, ZipOutputStream zos) throws IOException{
        // Create a buffer to read into
        byte[] buffer = new byte[1024];
        // Create a zip entry with individual filename
        ZipEntry ze = new ZipEntry(fileName);
        // Add the zip entry to the zip output stream
        zos.putNextEntry(ze);
        // Write data to zipEntry
        zos.write(data);
        // Close the zip entry
        zos.closeEntry();
        zos.flush();
    }
}
