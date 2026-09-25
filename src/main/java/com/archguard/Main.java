package com.archguard;

/**
 * Hello world!
 *
 */
public class Main
{
    public static void main( String[] args )
    {
        try{
            SidecarExecutor sidecarExecutor = new SidecarExecutor();
            RuleEngine engine = new RuleEngine();

            String pythonScript = "sidecars/python/parser.py";
            String testfile = "src/test/resources/views/test_view.py";

            String astResult = sidecarExecutor.parsePythonFile(pythonScript, testfile);
            engine.evaluateViewBoundary(astResult);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
