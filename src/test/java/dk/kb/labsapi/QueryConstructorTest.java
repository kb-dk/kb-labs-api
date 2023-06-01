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

    // TODO: Tests for timestamps
    @Test
    public void testTimestamps(){
        List<String> text = new ArrayList<>(Arrays.asList("hest"));
        String correctString = "hest AND py:[1740 TO 1750]";
        String query = QueryConstructor.constructQuery(text, null, 1740, 1750, null, null);
        Assertions.assertEquals(correctString, query);
    }

    // TODO: Tests for familyId
    @Test
    public void testSingleFamilyId(){
        List<String> familyId = new ArrayList<>(Arrays.asList("testAvis"));
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

    // TODO: Tests for lplace

}
