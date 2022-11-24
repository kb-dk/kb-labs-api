package dk.kb.labsapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.ws.rs.core.StreamingOutput;
import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;

public class ImageExtractor {

    /**
     * Get metadata values for all illustrations from query in mediestream.
     * The returned array contains and array of metadata from each single illustration.
     * These inner arrays contain the following values in order: x, y, w, h.
     * X and Y are coordinates while w = width and h = height.
     * @return an array of integer arrays consisting of the x, y, w and h values that are used to extract the illustration.
     */
    static public int[][] getMetadataForAllIllustrations() throws IOException {
        // Test SOLR call
        String jsonString = solrCall();

        // Parse result from query and save into a list of strings
        List<String> illustrationList = getIllustrationIntegers(jsonString);


        // Creating object to return
        int[][] allXYHWValues = new int[illustrationList.size()][];

        for (int i = 0; i< illustrationList.size(); i++){
            allXYHWValues[i] = getMetadataForSingleIllustration(i);
        }

        return allXYHWValues;
    }
    /**
     * Get metadata values for single illustrations from query in mediestream.
     * The returned array contains the following values in order: x, y, w, h.
     * X and Y are coordinates while w = width and h = height.
     * @param index represents the index at which the illustrations data is placed.
     * @return an array consisting of the x, y, w and h values that are used to extract the illustration.
     */
    static public int[] getMetadataForSingleIllustration(int index) throws IOException {

        // Test SOLR call
        String jsonString = solrCall();

        // Parse result from query and save into a list of strings
        List<String> illustrationList = getIllustrationIntegers(jsonString);

        // TODO: Figure out how to use these values
        // Dividing the strings from illustrationList into individual arrays of respective types
        String[] illustrationIDs = getIDs(illustrationList);
        int[] xValues = getXValues(illustrationList);
        int[] yValues = getYValues(illustrationList);
        int[] wValues = getWValues(illustrationList);
        int[] hValues = getHValues(illustrationList);

        // Creating object to return
        // Consists of x, y, w and h values
        int[] singleXYHWValues = new int[]{
                xValues[index], yValues[index], wValues[index], hValues[index]
        };

        return singleXYHWValues;
    }

    /**
     * Parse JSON response into list of individual illustrations.
     * @param jsonString to extract illustrations from.
     * @return a list of strings. Each string contains metadata for a single illustration from the input jsonString.
     */
    static public List<String> getIllustrationIntegers(String jsonString) {
        // Create JSON Array from json string
        JSONArray responseArray = new JSONArray(jsonString);
        // Create list for all illustration values
        List<String> illustrationList = new ArrayList<>();
        // Add all individual illustrations to list
        for (int i = 0; i < responseArray.length(); ++i) {
            JSONObject document = responseArray.getJSONObject(i);
            String illustration = document.getString("illustration");
            String[] illustrationsSplitted = illustration.split("\n");
            illustrationList.addAll(Arrays.asList(illustrationsSplitted));
        }

        return illustrationList;

    }

    /**
     * Extracts individual IDs from list of strings with the format "ID=IDIDID, x=XX, y=YY, w=WW, h=HH"
     * @param illustrationList input list of strings with specific format
     * @return String array of IDs for illustrations
     */
    static public String[] getIDs(List<String> illustrationList){
        String[] stringSplitted;
        String[] idStrings = new String[illustrationList.size()];

        for (int i = 0; i < illustrationList.size(); ++i) {
            stringSplitted = illustrationList.get(i).split(",");
            idStrings[i] = stringSplitted[0];
            idStrings[i] = idStrings[i].replace("id=", "");
        }

        return idStrings;
    }

    /**
     * Extracts individual X values from list of strings with the format "ID=IDIDID, x=XX, y=YY, w=WW, h=HH"
     * @param illustrationList input list of strings with specific format
     * @return Integer array of X values for illustrations
     */
    static public int[] getXValues(List<String> illustrationList){
        String[] stringSplitted;
        String[] xStrings = new String[illustrationList.size()];
        int[] xInts = new int[xStrings.length];

        for (int i = 0; i < illustrationList.size(); ++i) {
            stringSplitted=illustrationList.get(i).split(",");
            xStrings[i]=stringSplitted[1];
            xStrings[i] = xStrings[i].replace("x=", "");
            try {
                xInts[i] = Integer.parseInt(xStrings[i]);
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
        }
        return xInts;
    }

    /**
     * Extracts individual Y values from list of strings with the format "ID=IDIDID, x=XX, y=YY, w=WW, h=HH"
     * @param illustrationList input list of strings with specific format
     * @return Integer array of Y values for illustrations
     */
    static public int[] getYValues(List<String> illustrationList){
        String[] stringSplitted;
        String[] yStrings = new String[illustrationList.size()];
        int[] yInts = new int[yStrings.length];


        for (int i = 0; i < illustrationList.size(); ++i) {
            stringSplitted=illustrationList.get(i).split(",");
            yStrings[i]=stringSplitted[2];
            yStrings[i] = yStrings[i].replace("y=", "");
            try {
                yInts[i] = Integer.parseInt(yStrings[i]);
            } catch (NumberFormatException e) {
            throw new RuntimeException(e);
            }
        }
            return yInts;
    }

    /**
     * Extracts individual W values from list of strings with the format "ID=IDIDID, x=XX, y=YY, w=WW, h=HH"
     * @param illustrationList input list of strings with specific format
     * @return Integer array of W values for illustrations
     */
    static public int[] getWValues(List<String> illustrationList){
        String[] stringSplitted;
        String[] wStrings = new String[illustrationList.size()];
        int[] wInts = new int[wStrings.length];

        for (int i = 0; i < illustrationList.size(); ++i) {
            stringSplitted=illustrationList.get(i).split(",");
            wStrings[i]=stringSplitted[3];
            wStrings[i] = wStrings[i].replace("w=", "");
            try {
                wInts[i] = Integer.parseInt(wStrings[i]);
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
        }
        return wInts;
    }

    /**
     * Extracts individual H values from list of strings with the format "ID=IDIDID, x=XX, y=YY, w=WW, h=HH"
     * @param illustrationList input list of strings with specific format
     * @return Integer array of H values for illustrations
     */
    static public int[] getHValues(List<String> illustrationList){
        String[] stringSplitted;
        String[] hStrings = new String[illustrationList.size()];
        int[] hInts = new int[hStrings.length];

        for (int i = 0; i < illustrationList.size(); ++i) {
            stringSplitted = illustrationList.get(i).split(",");
            hStrings[i] = stringSplitted[4];
            hStrings[i] = hStrings[i].replace("h=", "");
            try {
                hInts[i] = Integer.parseInt(hStrings[i]);
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
        }
        return hInts;
    }

    static public String solrCall() throws IOException {
        // These fields act as placeholders, while the call to SolrExport.getInstance().export
        // handles all variables itself as it is for now.
        Set<String> fields = new HashSet<>();
        fields.add("alto_box");
        fields.add("illustration");
        Set<SolrExport.STRUCTURE> structure = new HashSet<>();
        structure.add(SolrExport.STRUCTURE.valueOf("content"));
        long max = 10;

        // Query here is only important variable/argument
        StreamingOutput stream = SolrExport.getInstance().export("cykel", fields, max , structure , SolrExport.EXPORT_FORMAT.image);

        // Convert StreamingOutput to String
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        stream.write(output);
        String jsonOutput = output.toString(StandardCharsets.UTF_8);
        return jsonOutput;
    }
}
