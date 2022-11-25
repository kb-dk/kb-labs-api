package dk.kb.labsapi;

import dk.kb.labsapi.config.ServiceConfig;
import dk.kb.util.yaml.YAML;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.core.StreamingOutput;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class ImageExtractor {

    /**
     * Get metadata values for all illustrations from query.
     * The returned array contains and object of metadata from each single illustration.
     * These inner objects contain the following values in order: String id, int x, int y, int w, int h.
     * X and Y are coordinates while w = width and h = height.
     * @return an array of objects consisting of the id, x, y, w and h values that are used to extract illustrations.
     */
    static public List<IllustrationMetadata> getMetadataForIllustrations() throws IOException {
        List<IllustrationMetadata> illustrations = new ArrayList<>();
        // Test SOLR call - This will probably be given as method input/argument
        String jsonString = solrCall();
        // Parse result from query and save into a list of strings
        List<String> illustrationList = getIllustrationsList(jsonString);

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
     * Parse JSON response into list of individual illustrations.
     * @param jsonString to extract illustrations from.
     * @return a list of strings. Each string contains metadata for a single illustration from the input jsonString.
     */
    static public List<String> getIllustrationsList(String jsonString) {
        // Create JSON Array from json string
        JSONArray responseArray = new JSONArray(jsonString);
        // Create list for all illustration values
        List<String> illustrationList = new ArrayList<>();
        // Add all individual illustrations to list
        for (int i = 0; i < responseArray.length(); ++i) {
            JSONObject document = responseArray.getJSONObject(i);
            String illustration = document.getString("illustration");
            String[] illustrationsSplitted = illustration.split("\n");
            String pageUUID = document.getString("pageUUID");
            int pageWidth = document.getInt("page_width");
            int pageHeight = document.getInt("page_height");

            for (int j = 0; j< illustrationsSplitted.length; j++){
                illustrationsSplitted[j] = illustrationsSplitted[j] + "," + pageUUID + "," + pageWidth + "," + pageHeight;
            }
            illustrationList.addAll(Arrays.asList(illustrationsSplitted));
        }
        return illustrationList;
    }

    /**
     * Construct link to illustration from metadata.
     * @param ill is a class containing metadata for an illustration
     * @return a URL to the illustration described in the input metadata
     */
    public static URL createIllustrationLinks(IllustrationMetadata ill) throws IOException {
        YAML conf = ServiceConfig.getConfig();
        String baseURL = conf.getString("labsapi.aviser.imageserver.url");
        String baseParams = "&CVT=jpeg";
        String pageUuid = ill.getPageUUID();
        String prePageUuid = "/" + pageUuid.charAt(0) + "/" + pageUuid.charAt(1) + "/" + pageUuid.charAt(2) + "/" + pageUuid.charAt(3) + "/";

        // SYNTAX for defining region for export
        //RGN=x,y,w,h

        String region = calculateIllustrationRegion(ill.getX(), ill.getY(), ill.getW(), ill.getH(), ill.getPageWidth(), ill.getPageHeight());

        URL finalUrl = new URL(baseURL+prePageUuid+pageUuid+region+baseParams);
        return finalUrl;
    }

    /**
     * Calculate X & W coordinates, width and height for region parameter. Converts input pixel values to fractions of image size.
     * The image server containing the images uses the <a href="https://iipimage.sourceforge.io/documentation/protocol/">Internet Imaging Protocol</a>.
     * @return a region string that is ready to be added to an IIP query
     */
    public static String calculateIllustrationRegion(int x, int y, int w, int h, int width, int height){
        float calculatedX = (float) x / (float) width;
        float calculatedY = (float) y / (float) height;
        float calculatedW = (float) w / (float) width;
        float calculatedH = (float) h / (float) height;
        return "&RGN="+calculatedX+","+calculatedY+","+calculatedW+","+calculatedH;
    }

    static public String solrCall() throws IOException {
        // These fields act as placeholders, while the call to SolrExport.getInstance().export
        // handles all variables itself as it is for now.
        Set<String> fields = new HashSet<>();
        fields.add("pageUUID");
        fields.add("illustration");
        fields.add("page_width");
        fields.add("page_height");
        Set<SolrExport.STRUCTURE> structure = new HashSet<>();
        structure.add(SolrExport.STRUCTURE.valueOf("content"));
        long max = 10;
        // Query here is only important variable/argument
        StreamingOutput stream = SolrExport.getInstance().export("cykel", fields, max , structure , SolrExport.EXPORT_FORMAT.image);
        // Convert StreamingOutput to String
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        stream.write(output);
        return output.toString(StandardCharsets.UTF_8);
    }
}
