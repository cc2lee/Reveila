package com.reveila.system

class DependencyValidator {

    /**
     * Validates a graph for circular dependencies.
     *
     * @param dependencyMap A map where Key is the component name,
     *                      and Value is its collection of dependencies.
     */
    @Throws(Exception::class)
    fun validate(dependencyMap: Map<String, Collection<String>>) {
        val visited = mutableSetOf<String>()
        val beingVisited = mutableSetOf<String>()

        for (node in dependencyMap.keys) {
            if (hasCycle(node, dependencyMap, visited, beingVisited)) {
                throw Exception("⛔ Circular Dependency Detected involving: $node")
            }
        }
    }

    private fun hasCycle(
        node: String,
        dependencyMap: Map<String, Collection<String>>,
        visited: MutableSet<String>,
        beingVisited: MutableSet<String>
    ): Boolean {
        if (beingVisited.contains(node)) return true
        if (visited.contains(node)) return false

        beingVisited.add(node)

        val dependencies = dependencyMap[node]
        if (dependencies != null) {
            for (dep in dependencies) {
                if (hasCycle(dep, dependencyMap, visited, beingVisited)) {
                    return true
                }
            }
        }

        beingVisited.remove(node)
        visited.add(node)
        return false
    }
}
