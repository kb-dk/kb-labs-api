package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.util.webservice.exception.InternalServiceException;
import dk.kb.util.yaml.YAML;
import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.params.GroupParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URL;
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
    //TODO: Change internal methods to be private
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
     * Get link to image from newspaper page with given query present in text
     *
     * @param query     to search for
     * @param startTime
     * @param endTime
     * @param max       number of images to return
     * @return urls to images
     */
     public ByteArrayOutputStream getImageFromTextQuery(String query, Integer startTime, Integer endTime, int max) throws IOException {
         if (instance.ImageExportService == null) {
            throw new InternalServiceException("Illustration delivery service has not been configured, sorry");
         }
        // Query Solr
        QueryResponse response = illustrationSolrCall(query, startTime, endTime, max);
        // Get illustration metadata
        List<IllustrationMetadata> illustrationMetadata = getMetadataForIllustrations(response);
        // Get illustration URLS
        List<URL> illustrationUrls = createLinkForAllIllustrations(illustrationMetadata);
        // Get illustrations as bytearrays
        List<byte[]> illustrationsAsByteArrays = downloadAllIllustrations(illustrationUrls);
        // Create bytearray of all images as zipArray
        ByteArrayOutputStream allImagesAsJpgs = byteArraysToZipArray(illustrationsAsByteArrays);

        return allImagesAsJpgs;

    }

    /**
     * Call Solr for input query and return fields needed to extract images from pages in the query.
     *
     * @param query     to call Solr with.
     * @param startTime
     * @param endTime
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
     public List<String> getIllustrationsList(QueryResponse solrResponse) {
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
        String pageUuid = ill.getPageUUID();
        String prePageUuid = "/" + pageUuid.charAt(0) + "/" + pageUuid.charAt(1) + "/" + pageUuid.charAt(2) + "/" + pageUuid.charAt(3) + "/";
        // TODO: Should some encoding happen here, so that we are not using commas directly in URL?
        String region = calculateIllustrationRegion(ill.getX(), ill.getY(), ill.getW(), ill.getH(), ill.getPageWidth(), ill.getPageHeight());

        URL finalUrl = new URL(baseURL+prePageUuid+pageUuid+region+baseParams);
        return finalUrl;
    }

    /**
     * Calculate X & W coordinates, width and height for region parameter. Converts input pixel values to fractions of image size.
     * The image server containing the images uses the <a href="https://iipimage.sourceforge.io/documentation/protocol/">Internet Imaging Protocol</a>.
     * @return a region string that is ready to be added to an IIP query.
     */
    public String calculateIllustrationRegion(int x, int y, int w, int h, int width, int height){
        // Some illustrations have X and Y values greater than the width and height of the page.
        // Currently, these values are turned into zeroes and are therefore returning an incorrect image.
        // TODO: Ideally they should return the correct image (if that exists?) or nothing at all. LOOK INTO ALTO FILES
        float calculatedX = (float) x / (float) Math.max(width, x);
        float calculatedY = (float) y / (float) Math.max(height, y);
        float calculatedW = (float) w / (float) Math.max(w, width);
        float calculatedH = (float) h / (float) Math.max(h, height);
        /* Hack to make weird illustrationboxes return something
        if (calculatedX >= 1.0F){
            calculatedX = 0;
        }
        if (calculatedY >= 1.0F){
            calculatedY = 0;
        }
        */
        // Fraction calculation from: https://math.hws.edu/graphicsbook/c2/s1.html
        // newX = newLeft + ((oldX - oldLeft) / (oldRight - oldLeft)) * (newRight - newLeft))
        // newY = newTop + ((oldY - oldTop) / (oldBottom - oldTop)) * (newBottom - newTop)
        return "&RGN="+calculatedX+","+calculatedY+","+calculatedW+","+calculatedH;//+"&WID="+width+"&HEI="+height;
    }

    /**
     * Download all images from a list of URLs.
     * @param illustrationUrls List of URLs pointing to imageserver.
     * @return list of byte arrays, where each array contains an image.
     */
    public List<byte[]> downloadAllIllustrations(List<URL> illustrationUrls) throws IOException {
        // TODO: Stream directly to Streaming output
        List<byte[]> allIllustrations = new ArrayList<>();

        for (int i = 0; i<illustrationUrls.size(); i++) {
            byte[] illustrationAsByteArray = downloadSingleIllustration(illustrationUrls.get(i));
            allIllustrations.add(illustrationAsByteArray);
        }
        return allIllustrations;
    }

    /**
     * Download an illustration from given URL and return it as a byte array.
     * @param url pointing to the image to download.
     * @return downloaded image as byte array.
     */
    public byte[] downloadSingleIllustration(URL url) {
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
    }

    /**
     * Converts a list of byte arrays to an output stream containing hte images packed into a zip file.
     * @param illustrationsAsByteArrays is a list of byte arrays, where each byte array contains an image.
     * @return a zip file containing images as an output stream
     */
    public ByteArrayOutputStream byteArraysToZipArray(List<byte[]> illustrationsAsByteArrays) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ZipOutputStream zos = new ZipOutputStream(baos);
        zos.setLevel(Deflater.NO_COMPRESSION);
        int count = 0;
        try {
            // Add byte arrays from input to zip
            for (int i = 0; i < illustrationsAsByteArrays.size(); i++) {
                // TODO: Add pageUUIDs as prefix to filnames
                addToZipStream(illustrationsAsByteArrays.get(i), "illustration_" + count + ".jpeg", zos);
                count += 1;
            }
            // Close the zip output stream
            zos.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
        return baos;
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
        // Read the data into the buffer
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        int len;
        while ((len = bais.read(buffer)) > 0) {
            // Write the buffer to the zip output stream
            zos.write(buffer, 0, len);
        }
        // Close the zip entry
        zos.closeEntry();
        zos.flush();
    }
}
