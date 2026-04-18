# "Tool RAG" (Semantic Tool Selection)

### **The Architecture: Tool Registry as a Vector Store**

Instead of scanning PDFs, we scan the **Manifest JSONs**. The same Vector Database (PostgreSQL with `pgvector` / SQLite-vec) we use for the Knowledge Vault can store the "embeddings" of the tool descriptions.

---

### **The 3-Step Retrieval Pipeline**

To make this work in the **LangChain4j 1.x** environment, we implement a dynamic retrieval step before the LLM call:

#### **1. Tool Indexing (The Background Job)**
Every time a new plugin is registered in the Montgomery cluster:
1.  Extract the `name` and `description` from the Manifest.
2.  Generate a vector embedding of that description (e.g., using `text-embedding-3-small`).
3.  Store the embedding along with the **Manifest ID** in the Vector DB.

#### **2. Semantic Tool Retrieval (The "Pre-Prompt")**
When a user asks: *"Check the server logs for the EchoService,"* the system:
1.  Embeds the user's query.
2.  Searches the Vector DB for the **Top 5** most relevant tool descriptions.
3.  Retrieves those specific Manifests.

#### **3. Dynamic Injection**
The system then translates *only* those 5 Manifests into `ToolSpecification` objects and injects them into the current prompt.



---

### **Reveila-Specific Optimization: Hybrid Retrieval**

For an enterprise architect, simple vector search sometimes fails on technical keywords (e.g., it might confuse "EchoService" with "EchoLocation"). I recommend a **Hybrid Approach**:

* **Vector Search:** For broad intent (e.g., "I need to fix a network issue").
* **Metadata Filtering:** If the user specifies a system or tier (e.g., "In the Production tier"), pre-filter the results to only include plugins with the corresponding `agency_perimeter.tier`.

---

### **Implementation in LangChain4j**

We can implement this using a custom **`ToolProvider`**. Instead of a static map, the provider calls the Vector Store at runtime:

```java
public class DynamicToolProvider implements ToolProvider {}
```

### "Rerank" tools?

Sometimes the top vector match isn't the best one, and a tiny secondary LLM pass can ensure the #1 tool is the right one before it hits the main prompt.

We use a **two-stage retrieval** (Search + Rerank) for the plugins. Just because something is a "tool" doesn't mean it should be treated differently than "data"—at the scale of thousands of plugins, tool discovery *is* a search problem.

Using the same approach as the Knowledge Vault ensures that the **Qwen 2.5 1.5b** isn't overwhelmed by the "Short Model Tax" of seeing 50 irrelevant tools.

---

### **The Tool-RAG Pipeline for Reveila**

Instead of just one vector search, we should implement a **"Search & Judge"** architecture.

#### **Stage 1: The Vector Search (Broad Recall)**
* **The Index:** We embed the `description` and `name` from every plugin manifest in the cluster.
* **The Action:** When the user provides an intent, we retrieve the **top 50** candidate tools using standard cosine similarity.
* **Problem:** Vector search alone is "fuzzy." It might pull a `FileDeleteTool` when the user just wants to `ClearCache` because both mention "removing data."

#### **Stage 2: The Reranker (High Precision)**
* **The Model:** Use a **Cross-Encoder** (like `BGE-Reranker` or `Cohere Rerank 3 Nimble`).
* **The Action:** We feed the user intent and the 50 candidate manifests into the Reranker. Unlike vector search, the Reranker looks at the *exact interaction* between the intent and the tool's parameters.
* **The Result:** It picks the **top 5** tools with surgical precision. 

---

### **Technical Implementation in LangChain4j**

Since we're on **version 0.35.0 (or 1.x)**, we can use the `OnnxScoringModel` to run this reranking locally on the Montgomery server (or even the S23 Ultra) using **ONNX**.

```java
// 1. Initial Vector Retrieval
List<ToolSpecification> candidates = toolVectorStore.findRelevant(userIntent, 50);

// 2. Local Reranking (The "Judge")
ScoringModel reranker = new OnnxScoringModel("bge-reranker-v2-m3.onnx");
List<Double> scores = reranker.scoreAll(userIntent, candidates.stream()
    .map(ToolSpecification::description)
    .collect(toList()));

// 3. Select Top 5 for the Prompt
List<ToolSpecification> bestTools = sortAndLimit(candidates, scores, 5);
```

---

### **Why "Judge" Stage is Mandatory for Reveila**

| Feature | Vector Search Only | Search + Rerank |
| :--- | :--- | :--- |
| **Small Model (1.5b) Success** | 65% (Distracted by noise) | **95%+** (Only sees high-relevance tools) |
| **Latency** | ~50ms | ~150ms (Worth the 100ms trade-off) |
| **Logic Mismatch** | High (Model picks wrong tool) | **Low** (Reranker pre-filters logic) |



### **The "Agentic Fabric" Advantage**
Because we are running in clusters, we can even have a **"Global Tool Registry"** that handles the reranking for all nodes. When a worker node in the fabric needs to fulfill a request, it asks the Registry: *"Here is the user intent—give me the 5 specific tools I need to inject."*


# 3-Stage Tool (Plugin) Filtering Pipeline:

Stage 1 (Security): Absolute isolation based on metadata (tier).
Stage 2 (Recall): Vector search within the authorized subset.
Stage 3 (Precision): Reranking to provide the highest-quality tools to the model.

