package org.alloytools.alloy.core;

import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.Map;

import org.alloytools.alloy.dto.InstanceDTO;
import org.junit.Test;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.alloy4.XMLNode;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.ast.Expr;
import edu.mit.csail.sdg.ast.ExprVar;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.A4Solution;
import edu.mit.csail.sdg.translator.A4SolutionReader;
import edu.mit.csail.sdg.translator.A4SolutionWriter;
import edu.mit.csail.sdg.translator.TranslateAlloyToKodkod;

public class A4SolutionTest {

    private A4Solution solve(String alloy) {
        CompModule world = CompUtil.parseEverything_fromString(A4Reporter.NOP, alloy);
        A4Options options = new A4Options();
        Command cmd = world.getAllCommands().get(0);
        return TranslateAlloyToKodkod.execute_command(A4Reporter.NOP, world.getAllReachableSigs(), cmd, options);
    }

    @Test
    public void testToDTOInheritedFieldsPresent() {
        A4Solution sol = solve(
            "sig Name {}\n" +
            "sig Animal { name: one Name }\n" +
            "sig Dog extends Animal { friend: lone Dog }\n" +
            "run { some Dog } for 3\n"
        );
        assertTrue(sol.satisfiable());
        InstanceDTO dto = sol.toDTO(-1);

        Map.Entry<String, Map<String, String[][]>> dogEntry = null;
        for (Map.Entry<String, Map<String, String[][]>> e : dto.values.entrySet()) {
            if (e.getValue().containsKey("friend")) {
                dogEntry = e;
                break;
            }
        }
        assertNotNull("Should find an atom with the 'friend' field", dogEntry);
        assertTrue("Dog atom should have inherited 'name' field from Animal",
            dogEntry.getValue().containsKey("name"));
    }

    @Test
    public void testToDTOMultiLevelInheritedFieldsPresent() {
        A4Solution sol = solve(
            "sig Tag {}\n" +
            "sig Toy {}\n" +
            "sig Animal { tag: one Tag }\n" +
            "sig Dog extends Animal { friend: lone Dog }\n" +
            "sig Puppy extends Dog { toy: lone Toy }\n" +
            "run { some Puppy } for 3\n"
        );
        assertTrue(sol.satisfiable());
        InstanceDTO dto = sol.toDTO(-1);

        Map.Entry<String, Map<String, String[][]>> puppyEntry = null;
        for (Map.Entry<String, Map<String, String[][]>> e : dto.values.entrySet()) {
            if (e.getValue().containsKey("toy")) {
                puppyEntry = e;
                break;
            }
        }
        assertNotNull("Should find an atom with the 'toy' field", puppyEntry);
        assertTrue("Puppy should have inherited 'friend' from Dog",
            puppyEntry.getValue().containsKey("friend"));
        assertTrue("Puppy should have inherited 'tag' from Animal",
            puppyEntry.getValue().containsKey("tag"));
    }

    @Test
    public void testToDTOSubsetSigFieldsPresent() {
        A4Solution sol = solve(
            "sig Animal { age: one Int }\n" +
            "sig Young in Animal {}\n" +
            "run { some Young } for 3\n"
        );
        assertTrue(sol.satisfiable());
        InstanceDTO dto = sol.toDTO(-1);

        // Young is a subset sig of Animal, so Young atoms should have 'age'
        boolean foundYoung = false;
        for (Map.Entry<String, Map<String, String[][]>> e : dto.values.entrySet()) {
            if (e.getValue().containsKey("age")) {
                foundYoung = true;
            }
        }
        assertTrue("Subset sig atom should have 'age' field from Animal", foundYoung);
    }

    @Test
    public void testToStringInheritedFieldsPresent() {
        A4Solution sol = solve(
            "sig Name {}\n" +
            "sig Animal { name: one Name }\n" +
            "sig Dog extends Animal { friend: lone Dog }\n" +
            "run { some Dog } for 3\n"
        );
        assertTrue(sol.satisfiable());
        String text = sol.toString();
        // Dog should show its own field and the inherited field from Animal
        assertTrue("toString should include Dog's own 'friend' field",
            text.contains("this/Dog<:friend="));
        assertTrue("toString should include inherited 'name' field under Dog",
            text.contains("this/Dog<:name="));
    }

    @Test
    public void testFormatInheritedFieldsPresent() {
        A4Solution sol = solve(
            "sig Animal { legs: one Int }\n" +
            "sig Dog extends Animal { friend: lone Dog }\n" +
            "run { some Dog } for 3\n"
        );
        assertTrue(sol.satisfiable());
        // format() uses TableView.toTable(), same path as -t text in CLI.
        // Dog's table should list both its own 'friend' and inherited 'legs'.
        String text = sol.format(0);
        assertTrue("format() should include Dog's own 'friend' field, got: " + text,
            text.contains("friend"));
        assertTrue("format() should include inherited 'legs' field for Dog, got: " + text,
            text.contains("legs"));
    }

    /**
     * Helper: solve, write XML, load from XML, return loaded solution + shared
     * CompModule (needed for expression parsing against the loaded solution).
     */
    private Object[] solveAndLoadXml(String model) throws Exception {
        CompModule solveWorld = CompUtil.parseEverything_fromString(A4Reporter.NOP, model);
        A4Options opt = new A4Options();
        Command cmd = solveWorld.getAllCommands().get(0);
        A4Solution sol = TranslateAlloyToKodkod.execute_command(A4Reporter.NOP, solveWorld.getAllReachableSigs(), cmd, opt);
        assertTrue(sol.satisfiable());

        File tmp = File.createTempFile("alloy-test", ".xml");
        tmp.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(tmp)) {
            A4SolutionWriter.writeInstance(null, sol, pw, Collections.emptyList(), Collections.emptyMap());
        }

        CompModule evalWorld = CompUtil.parseEverything_fromString(A4Reporter.NOP, model);
        XMLNode xmlNode = new XMLNode(tmp);
        A4Solution loaded = A4SolutionReader.read(evalWorld.getAllReachableSigs(), xmlNode);

        for (ExprVar a : loaded.getAllAtoms()) evalWorld.addGlobal(a.label, a);
        for (ExprVar a : loaded.getAllSkolems()) evalWorld.addGlobal(a.label, a);

        return new Object[]{loaded, evalWorld};
    }

    @Test
    public void testXmlRoundTripInheritedFields() throws Exception {
        String model =
            "sig Name {}\n" +
            "sig Animal { name: one Name }\n" +
            "sig Dog extends Animal { friend: lone Dog }\n" +
            "run { some Dog } for 3";

        Object[] result = solveAndLoadXml(model);
        A4Solution loaded = (A4Solution) result[0];
        CompModule evalWorld = (CompModule) result[1];

        // Dog.name — inherited field from Animal
        Expr eDogName = evalWorld.parseOneExpressionFromString("Dog.name");
        String dogNames = loaded.eval(eDogName).toString();
        assertNotEquals("Dog atoms should have inherited 'name' values via XML round-trip",
            "{}", dogNames);

        // Dog.friend — Dog's own field
        Expr eDogFriend = evalWorld.parseOneExpressionFromString("Dog.friend");
        loaded.eval(eDogFriend); // should not throw
    }

    @Test
    public void testXmlRoundTripMultiLevelInheritedFields() throws Exception {
        String model =
            "sig Tag {}\n" +
            "sig Toy {}\n" +
            "sig Animal { tag: one Tag }\n" +
            "sig Dog extends Animal { friend: lone Dog }\n" +
            "sig Puppy extends Dog { toy: lone Toy }\n" +
            "run { some Puppy } for 3";

        Object[] result = solveAndLoadXml(model);
        A4Solution loaded = (A4Solution) result[0];
        CompModule evalWorld = (CompModule) result[1];

        // Puppy.tag — inherited two levels up from Animal
        Expr ePuppyTag = evalWorld.parseOneExpressionFromString("Puppy.tag");
        String puppyTags = loaded.eval(ePuppyTag).toString();
        assertNotEquals("Puppy should have 'tag' inherited from Animal via XML round-trip",
            "{}", puppyTags);

        // Puppy.friend — inherited one level up from Dog
        Expr ePuppyFriend = evalWorld.parseOneExpressionFromString("Puppy.friend");
        loaded.eval(ePuppyFriend); // should not throw
    }
}
