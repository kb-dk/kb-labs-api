package dk.kb.labsapi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QueryConstructorTest {

    // TODO: Test for all query params
    // Tests for text parameters
    @Test
    public void testSingleTextParams(){
        List<String> testText = new ArrayList<>(Arrays.asList("hest"));
        String correctString = "hest";

        String query = QueryConstructor.constructQuery(testText, null, null, null, null, null);
        Assertions.assertEquals(correctString, query);
    }

    @Test
    public void testMultipleTextParams(){
        List<String> testText = new ArrayList<>(Arrays.asList("hest", "ko", "kylling"));
        String correctString = "hest AND ko AND kylling";

        String query = QueryConstructor.constructQuery(testText, null, null, null, null, null);
        Assertions.assertEquals(correctString, query);
    }

    // Test for boolean constructors
    @Test
    public void testBooleanValues(){
        List<String> testText = new ArrayList<>(Arrays.asList("hest", "ko", "kylling"));
        String booleanOperator = " OR ";
        String correctString = "hest OR ko OR kylling";

        String query = QueryConstructor.constructQuery(testText, booleanOperator, null, null, null, null);
        Assertions.assertEquals(correctString, query);
    }

    @Test
    public void testSpacesInBooleanOperators(){
        List<String> testText = new ArrayList<>(Arrays.asList("hest", "ko", "kylling"));
        String booleanOperator = "AND";
        String correctString = "hest AND ko AND kylling";

        String query = QueryConstructor.constructQuery(testText, booleanOperator, null, null, null, null);
        Assertions.assertEquals(correctString, query);
    }

    // Tests for timestamps
    @Test
    public void testStartYear(){
        List<String> text = new ArrayList<>(List.of("hest"));
        String correctString = "hest AND py:[1740 TO *]";
        String query = QueryConstructor.constructQuery(text, null, 1740, null, null, null);
        Assertions.assertEquals(correctString, query);
    }

    @Test
    public void testEndYear(){
        List<String> text = new ArrayList<>(List.of("hest"));
        String correctString = "hest AND py:[* TO 1840]";
        String query = QueryConstructor.constructQuery(text, null, null, 1840, null, null);
        Assertions.assertEquals(correctString, query);
    }

    @Test
    public void testBothYears(){
        List<String> text = new ArrayList<>(List.of("hest"));
        String correctString = "hest AND py:[1790 TO 1840]";
        String query = QueryConstructor.constructQuery(text, null, 1790, 1840, null, null);
        Assertions.assertEquals(correctString, query);
    }

    // Tests for familyId
    @Test
    public void testSingleFamilyId(){
        List<String> familyId = new ArrayList<>(List.of("testAvis"));
        String correctString = "familyId:testAvis";
        String query = QueryConstructor.constructQuery(null, null, null, null, familyId, null);
        Assertions.assertEquals(correctString, query);
    }

    @Test
    public void testMultipleFamilyIds(){
        List<String> familyId = new ArrayList<>(Arrays.asList("testAvis", "andenAvis", "tredjeAvis"));
        String correctString = "familyId:(testAvis OR andenAvis OR tredjeAvis)";
        String query = QueryConstructor.constructQuery(null, null, null, null, familyId, null);
        Assertions.assertEquals(correctString, query);
    }

    // Tests for lplace
    @Test
    public void testSingleLplace(){
        List<String> lplace = new ArrayList<>(List.of("aarhus"));
        String correctString = "lplace:aarhus";
        String query = QueryConstructor.constructQuery(null, null, null, null, null, lplace);
        Assertions.assertEquals(correctString, query);
    }

    @Test
    public void testMultipleLplace(){
        List<String> lplace = new ArrayList<>(Arrays.asList("aarhus", "aalborg", "viborg"));
        String correctString = "lplace:(aarhus OR aalborg OR viborg)";
        String query = QueryConstructor.constructQuery(null, null, null, null, null, lplace);
        Assertions.assertEquals(correctString, query);
    }

    @Test
    public void testFamilyIdAndLplace(){
        List<String> familyId = new ArrayList<>(Arrays.asList("testAvis", "andenAvis", "tredjeAvis"));
        List<String> lplace = new ArrayList<>(Arrays.asList("aarhus", "aalborg", "viborg"));
        String correctString = "familyId:(testAvis OR andenAvis OR tredjeAvis) AND lplace:(aarhus OR aalborg OR viborg)";

        String query = QueryConstructor.constructQuery(null, null, null, null, familyId, lplace);
        Assertions.assertEquals(correctString, query);
    }

}
