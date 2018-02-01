package com.stardisblue.ast;

import com.stardisblue.ast.structure.Graph;
import com.stardisblue.ast.structure.Matrix;
import com.stardisblue.logging.Logger;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.*;
import java.util.function.Function;

public class Compute {

    /**
     * Generates an object representing the class call graph
     *
     * @param methods list of methodDecorators
     * @return an object representing a class call graph
     */
    public static Graph classGraph(List<CtMethod<?>> methods) {
        return graph(methods,
                     ctMethod -> ctMethod.getElements(new TypeFilter<>(CtInvocation.class)),
                     ctMethod -> ctMethod.getDeclaringType().getQualifiedName(),
                     ctInvocation -> {
                         CtTypeReference type = ctInvocation.getTarget().getValueByRole(CtRole.TYPE);
                         if (Objects.equals(type.getSimpleName(), "void")) { // serieusement ?
                             type = ctInvocation.getTarget().getValueByRole(CtRole.ACCESSED_TYPE);
                         }
                         return
                                 type.getQualifiedName();
                     });
    }

    /**
     * Creates a graph structure iterating over parent and child using keynode and valuenode as references
     *
     * @param parent       Parent list
     * @param child        invoked foreach parent to retrieve the childrens
     * @param parentString invoked foreach parent to get the parent string value
     * @param childString  invoked foreach child to get the child string value
     * @param <T>          caller type
     * @param <U>          callee type
     * @return
     */
    public static <T, U> Graph graph(List<T> parent,
                                     Function<T, List<U>> child,
                                     Function<T, String> parentString,
                                     Function<U, String> childString) {
        Graph graph = new Graph();

        for (T caller : parent) {
            // there are people who call
            String callerString = parentString.apply(caller);

            int callerId;
            if (graph.has(callerString)) {// we have her in our phonebook
                callerId = graph.get(callerString);// we get her number
                graph.belongs(callerId, true); // she belongs to us because she's the caller
            } else {// she does not exist in our phonebook
                callerId = graph.nextId();// so we give her a number
                graph.belongs(true);// she belongs to us because she's the caller
                graph.beginCount(0); // we can begin to count the number of callees she has
                graph.save(callerString, callerId); // we add her to our phonebook
            }

            // we her and the people she called together
            HashSet<Integer> callees = graph.getLinkIds().computeIfAbsent(callerId, (k) -> new HashSet<>());

            for (U callee : child.apply(caller)) {
                // the ones who are called
                String calleeString = childString.apply(callee);

                int calleeId;
                if (graph.has(calleeString)) { // we have him in our phonebook
                    calleeId = graph.get(calleeString); // so we get the phone number
                    graph.incrementCount(calleeId); // the called is being called once more
                } else {// we do not have him in our phonebook
                    calleeId = graph.nextId(); // so we get him a number
                    graph.belongs(false); // he does not belong to us
                    graph.beginCount(1); // he was at least called by the caller
                    graph.save(calleeString, calleeId); // we save this guy's number
                }

                callees.add(calleeId); // we add this person to the list of called people
            }
        }

        return graph;
    }

    /**
     * Returns a list of json objects representing nodes
     *
     * @param nodeIds          the ids of the methods
     * @param belongsToProject if the node belongs to the repository
     * @return an array containing a node represented as a json object
     */
    public static ArrayList<String> graphNodes(HashMap<String, Integer> nodeIds, ArrayList<Boolean> belongsToProject) {
        ArrayList<String> nodes = new ArrayList<>(nodeIds.size());

        for (Map.Entry<String, Integer> nodeEntry : nodeIds.entrySet()) {
            // the json has an id, a name and if he belongs to the project
            nodes.add("{\"id\":" + nodeEntry.getValue() + ", " +
                              "\"name\": \"" + nodeEntry.getKey() + "\", " +
                              "\"own\": " + (belongsToProject.get(nodeEntry.getValue()) ? "true" : "false") + "}");
        }

        return nodes;
    }

    /**
     * Returns an array representing all the links as json objects
     *
     * @param linkIds      optimized hashmap representing all the links
     * @param countParents number of source nodes for a given targetNodeId
     * @return an array containing a links represented as json object
     */
    public static List<String> graphLinks(HashMap<Integer, HashSet<Integer>> linkIds, ArrayList<Integer> countParents) {
        ArrayList<String> links = new ArrayList<>();

        for (Map.Entry<Integer, HashSet<Integer>> linkEntry : linkIds.entrySet()) {
            int callerId = linkEntry.getKey();

            for (int calleeId : linkEntry.getValue()) {
                int parentCount = countParents.get(calleeId);
                // the more the callee was called, the less he weights
                float weight = (float) (1 / (0.5 * parentCount + 0.5));

                links.add("{\"source\":" + callerId + ", " +
                                  "\"target\": " + calleeId + ", " +
                                  "\"str\": " + weight + "}");
            }
        }

        return links;
    }

    /**
     * Iterates over methods to extract all the classnames present in classes.
     * <p>
     * Counts the coupling ratio between two classes.
     * <p>
     * For classes named A and B, takes all the method invocations of A in B and B in A, sum it and returns a matrix representing this association.
     *
     * @param classes list of classes
     * @param methods list of methods
     * @return a matrix representing all the coupling between the classes
     */
    public static Matrix classCoupling(List<CtType<?>> classes,
                                       List<CtMethod<?>> methods) {

        ArrayList<String> classNames = new ArrayList<>(classes.size());

        for (CtType aClass : classes) {
            classNames.add(aClass.getQualifiedName());
        }

        // we extract classnames
        Matrix matrix = new Matrix(classNames);

        for (CtMethod callee : methods) {

            Logger.printTitle(callee.getSignature(), Logger.DEBUG);

            for (CtInvocation caller : callee.getElements(new TypeFilter<>(CtInvocation.class))) {
                CtTypeReference type = caller.getTarget().getValueByRole(CtRole.TYPE);
                if (Objects.equals(type.getSimpleName(), "void")) {
                    type = caller.getTarget().getValueByRole(CtRole.ACCESSED_TYPE);
                }

                type.getQualifiedName();
                Logger.println("  - " + type, Logger.DEBUG);
                matrix.increment(callee.getDeclaringType().getQualifiedName(), type.getQualifiedName());
            }
        }

        matrix.generateTable();

        return matrix;
    }

}
