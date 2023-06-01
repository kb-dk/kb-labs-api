package dk.kb.labsapi;

import java.util.List;
import java.util.StringJoiner;

public class QueryConstructor {

    public static String constructQuery(List<String> text, String booleanOperator, Integer startTime, Integer endTime, List<String> familyId, List<String> lplace){
        String query;
        String textQuaries = "";
        String py = null;
        String familyIdString = null;

        if (booleanOperator == null){
            booleanOperator = " AND ";
        }

        if (!(text == null)){
            for (String value : text) {
                textQuaries = textQuaries.concat(booleanOperator + value);
            }
        }

        // How should we handle values that are out of range? Should we correct them or let them be created here and then corrected when used in other endpoints?
        if (!(startTime == null)){
            py = "py:[" + startTime + " TO " + endTime + "]";
        }

        if (!(familyId == null || familyId.size() == 0)){
            if (familyId.size() == 1){
                familyIdString = "familyId:" + familyId.get(0);
            } else {
                familyIdString = "familyId:(";
                for (String id : familyId){
                    familyIdString = familyIdString.concat(id + " OR ");
                }
                familyIdString = familyIdString.substring(0, familyIdString.length() - 4);
                familyIdString = familyIdString.concat(")");
            }
        }

        // Construct query from values
        query = String.join(booleanOperator, textQuaries, py, familyIdString);

        // Remove AND nulls from query
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
}
