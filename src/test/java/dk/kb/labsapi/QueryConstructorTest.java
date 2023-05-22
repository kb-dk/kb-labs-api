package dk.kb.labsapi;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class QueryConstructorTest {

    // TODO: Test for all query params
    // TODO: Test for text parameters
    @Test
    public void testSingleTextParams(){
        List<String> testText = new ArrayList<>(Arrays.asList("hest"));
        String correctString = "hest";

        String query = QueryConstructor.constructQuery(testText, null, null, null, null, null);
        Assertions.assertEquals(query, correctString);
    }

    @Test
    public void testMultipleTextParams(){
        List<String> testText = new ArrayList<>(Arrays.asList("hest", "ko", "kylling"));
        String correctString = "hest AND ko AND kylling";

        String query = QueryConstructor.constructQuery(testText, null, null, null, null, null);
        Assertions.assertEquals(query, correctString);
    }

    // TODO: Test for boolean constructors
    @Test
    public void testBooleanValues(){
        List<String> testText = new ArrayList<>(Arrays.asList("hest", "ko", "kylling"));
        String booleanOperator = "OR";
        String correctString = "hest OR ko OR kylling";

        String query = QueryConstructor.constructQuery(testText, booleanOperator, null, null, null, null);
        Assertions.assertEquals(query, correctString);
    }

    // TODO: Tests for startTime
    // TODO: Tests for endTime
    // TODO: Tests for familyId
    // TODO: Tests for lplace

}
