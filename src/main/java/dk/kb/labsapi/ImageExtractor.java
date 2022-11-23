package dk.kb.labsapi;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ImageExtractor {

    static public void getIllustrationIntegers(String jsonString) {
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

        // Individual arrays to save individual values
        String[] stringSplitted;
        String[] idStrings = new String[illustrationList.size()];
        String[] xStrings = new String[illustrationList.size()];
        String[] yStrings = new String[illustrationList.size()];
        String[] wStrings = new String[illustrationList.size()];
        String[] hStrings = new String[illustrationList.size()];

        // Extract illustration values to different arrays
        for (int i = 0; i < illustrationList.size(); ++i) {
            stringSplitted=illustrationList.get(i).split(",");
            idStrings[i]=stringSplitted[0];
            xStrings[i]=stringSplitted[1];
            yStrings[i]=stringSplitted[2];
            wStrings[i]=stringSplitted[3];
            hStrings[i]=stringSplitted[4];
        }

        for (int i = 0; i<responseArray.length();i++){
            idStrings[i] = idStrings[i].replace("id=", "");
            xStrings[i] = xStrings[i].replace("x=", "");
            yStrings[i] = yStrings[i].replace("y=", "");
            wStrings[i] = wStrings[i].replace("w=", "");
            hStrings[i] = hStrings[i].replace("h=", "");
        }

        // Create integer arrays from x,y,w,h strings
        int[] xInts = new int[xStrings.length];
        int[] yInts = new int[yStrings.length];
        int[] wInts = new int[wStrings.length];
        int[] hInts = new int[hStrings.length];

        // parse integers from string values to int[]
        for(int i = 0; i<responseArray.length(); i++) {
            try {
                xInts[i] = Integer.parseInt(xStrings[i]);
                yInts[i] = Integer.parseInt(yStrings[i]);
                wInts[i] = Integer.parseInt(wStrings[i]);
                hInts[i] = Integer.parseInt(hStrings[i]);
            } catch (NumberFormatException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
