package dk.kb.labsapi;

import java.util.List;

public class QueryConstructor {

    public static String constructQuery(List<String> text, String booleanOperator, Integer startTime, Integer endTime, List<String> familyId, List<String> lplace){
        String query = "";

        if (booleanOperator == null){
            booleanOperator = "AND";
        }

        if (!(text == null)){
            for (String value : text) {
                query = query.concat(" " + booleanOperator + " " + value);
            }
        }

        if (!(startTime == null)){
            query = query.concat(" AND py:[" + startTime + " TO " + endTime + "]");
        }

        if (!(familyId == null || familyId.size() == 0)){
            query = query.concat(" AND familyId:" + familyId);
        }

        if (query.startsWith(" " + booleanOperator + " ")) {
            query = query.replaceFirst(" "+booleanOperator+" ", "");
        }

        return query;
    }
}
