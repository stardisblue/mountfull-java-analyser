package com.stardisblue.mouthful;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.stardisblue.ast.Compute;
import com.stardisblue.ast.Display;
import com.stardisblue.ast.Write;
import com.stardisblue.ast.structure.Graph;
import com.stardisblue.ast.structure.Matrix;
import com.stardisblue.logging.Logger;
import com.stardisblue.utils.ListUtils;
import spoon.Launcher;
import spoon.reflect.CtModel;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class Main {

    @Parameter(names = {"-d", "--debug"}, description = "allow the debug logs to be displayed")
    private boolean debug = false;

    @Parameter(names = {"-h", "--help"}, description = "displays the help", help = true)
    private boolean help;

    @Parameter(description = "sourcepath")
    private String projectSourcePath = System.getProperty("user.dir") + "/src/main/java/";

    @Parameter(names = {"-o", "--output"}, description = "output file")
    private String output = "results.md";


    @Parameter(names = "-rt", description = "path to classpath")
    private List<String> classpaths;

    public Main() {
        classpaths = new ArrayList<>();
        classpaths.add(System.getProperty("java.home") + "/lib/rt.jar");
    }

    /**
     * Main method
     *
     * @param args program arguments
     */
    public static void main(String... args) {
        Main main = new Main();
        JCommander jcommander = JCommander.newBuilder().addObject(main).build();
        jcommander.parse(args);
        try {
            main.run(jcommander);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void run(JCommander jcommander) throws IOException {
        if (help) { // displays the help
            jcommander.usage();
            return;
        }

        if (debug) { // enables debug
            Logger.enable();
        }

        // setting up result file
        Display.setOutput(output);

        /*
         * Setting up AST
         */
        Launcher launcher = new Launcher();

        // path can be a folder or a file
        // addInputResource can be called several times
        launcher.addInputResource(projectSourcePath);

        // if true, the pretty-printed code is readable without fully-qualified names
        //launcher.getEnvironment().setAutoImports(true); // optional
        // if true, the model can be built even if the dependencies of the analyzed source code are not known or incomplete
        // the classes that are in the current classpath are taken into account
        launcher.getEnvironment().setNoClasspath(true); // optional
        launcher.buildModel();

        CtModel model = launcher.getModel();

        List<CtType<?>> classes = new ArrayList<>(model.getAllTypes());
        List<CtMethod<?>> methods = ListUtils.extract(classes, CtType::getMethods);

        Display.title("Mouthful spoon of java analysis");

        /*
         * Class call graph
         */
        Graph classGraph = Compute.classGraph(methods);
        // generate Json Structure
        List<String> classNodes = Compute.graphNodes(classGraph.getIds(), classGraph.getIsNodeInProject());
        List<String> classLinks = Compute.graphLinks(classGraph.getLinkIds(), classGraph.getSourceCount());
        // display
        Display.title("ClassCall Json graph", 2);
        Display.blockquote("written in `class-call-output.json`");
        // write
        Write.json("class-call-output.json", classNodes, classLinks);


        /*
         * TP4
         *
         * class coupling matrix
         */
        Matrix matrix = Compute.classCoupling(classes, methods);
        // display
        Display.matrix("Class coupling matrix", matrix);

        Display.close();
    }


}
