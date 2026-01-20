package com.reveila.system;

import java.util.*;

public class DependencyValidator {

    /**
     * Validates a graph for circular dependencies.
     * 
     * @param registry A map where Key is the component name,
     *                 and Value is its list of dependencies.
     */
    public void validate(Map<String, ? extends Collection<String>> registry) throws Exception {
        Set<String> visited = new HashSet<>();
        Set<String> beingVisited = new HashSet<>();

        for (String node : registry.keySet()) {
            if (hasCycle(node, registry, visited, beingVisited)) {
                throw new Exception("⛔ Circular Dependency Detected involving: " + node);
            }
        }
    }

    private boolean hasCycle(String node, Map<String, ? extends Collection<String>> registry,
            Set<String> visited, Set<String> beingVisited) {

        // If we are currently visiting this node in the current recursion stack, it's a
        // cycle.
        if (beingVisited.contains(node))
            return true;

        // If we've already fully explored this node and its children, it's safe.
        if (visited.contains(node))
            return false;

        beingVisited.add(node);

        Collection<String> dependencies = registry.get(node);
        if (dependencies != null) {
            for (String dep : dependencies) {
                if (hasCycle(dep, registry, visited, beingVisited)) {
                    return true;
                }
            }
        }

        beingVisited.remove(node);
        visited.add(node);
        return false;
    }
}