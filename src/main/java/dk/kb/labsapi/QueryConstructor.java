package dk.kb.labsapi;

import java.util.List;
import java.util.StringJoiner;

public class QueryConstructor {

    /**
     * Construct a Solr query from input arguments.
     * @param text that is to be searched for.
     * @param booleanOperator defines which boolean operator to use e.g. AND, OR, NOT.
     * @param startYear is the earliest boundary for the query. Boundaries are inclusive.
     * @param endYear is the latest boundary for the query. Boundaries are inclusive.
     * @param familyId limits the query to specific newspapers.
     * @param lplace limits the query to specific publication locations.
     * @return a string representation of a valid solr query constructed from the inputted parameters.
     */
    public static String constructQuery(List<String> text, String booleanOperator, Integer startYear, Integer endYear, List<String> familyId, List<String> lplace){
        String query;
        String textQuaries = "";
        String py = null;
        String familyIdString = null;
        String lplaceString = null;

        if (booleanOperator == null){
            booleanOperator = " AND ";
        }
        if (!(booleanOperator.endsWith(" "))){
            booleanOperator = booleanOperator.concat(" ");
        }
        if (!(booleanOperator.startsWith(" "))){
            booleanOperator = " " +booleanOperator;
        }

        if (!(text == null)){
            for (String value : text) {
                textQuaries = textQuaries.concat(booleanOperator + value);
            }
        }

        // How should we handle values that are out of range? Should we correct them or let them be created here and then corrected when used in other endpoints?
        // TODO: Handle startYear and endYear on their own.
        if (!(startYear == null) && !(endYear == null)){
            py = "py:[" + startYear + " TO " + endYear+ "]";
        }
        if (!(startYear == null) && endYear == null){
            py = "py:[" + startYear + " TO *]";
        }
        if (!(endYear == null) && startYear == null){
            py = "py:[* TO " + endYear + "]";
        }

        // Transform familyIDs to query
        if (!(familyId == null || familyId.size() == 0)){
            familyIdString = createFilter(familyId, "familyId");
        }

        // Transform lplaces to query
        if (!(lplace == null || lplace.size() == 0)){
            lplaceString = createFilter(lplace, "lplace");
        }

        // Construct query from values
        query = String.join(booleanOperator, textQuaries, py, familyIdString, lplaceString);

        // Remove 'AND nulls' from query
        if (query.contains(booleanOperator + "null")){
            query = query.replaceAll(booleanOperator + "null", "");
        }

        // Remove leading and trailing boolean operator if present
        if (query.startsWith(booleanOperator)) {
            query = query.replaceFirst(booleanOperator, "");
        }

        query = query.trim();

        return query;
    }

    /**
     * Create a filter to use as part of solr query.
     * @param parameter to create filter from. This list contains one or more values that are to be used in the filter.
     * @param filterName defines the field to filter on.
     * @return string containing filter.
     */
    private static String createFilter(List<String> parameter, String filterName){
        String output;
        if (parameter.size() == 1){
            output = filterName + ":" + parameter.get(0);
        } else {
            output = filterName + ":(";
            for (String place : parameter){
                output = output.concat(place + " OR ");
            }
            output = output.substring(0, output.length() - 4);
            output = output.concat(")");
        }
        return output;
    }
}
