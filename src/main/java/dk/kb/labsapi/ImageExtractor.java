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

    static public void getIllustrationMetadata(int x) throws IOException {

        String jsonString = solrCall();

        List<String> illustrationList =getIllustrationIntegers(jsonString);

        // TODO: Figure out how to use these values
        String[] illustrationIDs = getIDs(illustrationList);
        int[] xValues = getXValues(illustrationList);
        int[] yValues = getYValues(illustrationList);
        int[] hValues = getHValues(illustrationList);
        int[] wValues = getWValues(illustrationList);


    }

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
