package org.alloytools.alloy.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collections;

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

public class EvalFromXmlTest {

    @Test
    public void testStaticSpecEvalFromXml() throws Exception {
        String model = "sig A { f: set A }\nrun { some f } for exactly 2 A";

        // Solve and write XML
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

        // Load from XML and eval — same pattern as CLI eval command
        CompModule evalWorld = CompUtil.parseEverything_fromString(A4Reporter.NOP, model);
        XMLNode xmlNode = new XMLNode(tmp);
        A4Solution loaded = A4SolutionReader.read(evalWorld.getAllReachableSigs(), xmlNode);

        for (ExprVar a : loaded.getAllAtoms()) evalWorld.addGlobal(a.label, a);
        for (ExprVar a : loaded.getAllSkolems()) evalWorld.addGlobal(a.label, a);

        Expr eA = evalWorld.parseOneExpressionFromString("A");
        Object result = loaded.eval(eA);
        assertTrue(result.toString().contains("A$"));

        Expr eF = evalWorld.parseOneExpressionFromString("f");
        Object fResult = loaded.eval(eF);
        assertNotEquals("{}", fResult.toString());
    }

    @Test
    public void testTemporalEvalFromXml() throws Exception {
        String model =
            "sig Thing { var active: lone Thing }\n" +
            "fact { no active\n  after active = Thing -> Thing\n" +
            "  always (some active implies after some active) }\n" +
            "run {} for exactly 1 Thing";

        // Solve and write XML
        CompModule solveWorld = CompUtil.parseEverything_fromString(A4Reporter.NOP, model);
        A4Options opt = new A4Options();
        Command cmd = solveWorld.getAllCommands().get(0);
        A4Solution sol = TranslateAlloyToKodkod.execute_command(A4Reporter.NOP, solveWorld.getAllReachableSigs(), cmd, opt);
        assertTrue(sol.satisfiable());
        assertTrue(sol.getTraceLength() >= 2);

        File tmp = File.createTempFile("alloy-temporal-test", ".xml");
        tmp.deleteOnExit();
        try (PrintWriter pw = new PrintWriter(tmp)) {
            A4SolutionWriter.writeInstance(null, sol, pw, Collections.emptyList(), Collections.emptyMap());
        }

        // Load from XML and eval at different states
        CompModule evalWorld = CompUtil.parseEverything_fromString(A4Reporter.NOP, model);
        XMLNode xmlNode = new XMLNode(tmp);
        A4Solution loaded = A4SolutionReader.read(evalWorld.getAllReachableSigs(), xmlNode);
        assertTrue(loaded.getTraceLength() >= 2);

        for (ExprVar a : loaded.getAllAtoms()) evalWorld.addGlobal(a.label, a);
        for (ExprVar a : loaded.getAllSkolems()) evalWorld.addGlobal(a.label, a);

        Expr eActive = evalWorld.parseOneExpressionFromString("active");
        String state0 = loaded.eval(eActive, 0).toString();
        String state1 = loaded.eval(eActive, 1).toString();

        assertEquals("{}", state0);
        assertNotEquals("{}", state1);
    }
}
