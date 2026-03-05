package org.alloytools.alloy.core;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;

import org.alloytools.alloy.dto.InstanceDTO;
import org.junit.Test;

import edu.mit.csail.sdg.alloy4.A4Reporter;
import edu.mit.csail.sdg.ast.Command;
import edu.mit.csail.sdg.parser.CompModule;
import edu.mit.csail.sdg.parser.CompUtil;
import edu.mit.csail.sdg.translator.A4Options;
import edu.mit.csail.sdg.translator.A4Solution;
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
}
