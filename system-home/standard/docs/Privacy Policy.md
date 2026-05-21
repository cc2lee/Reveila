## **Privacy Policy: Reveila Personal Edition**
**Effective Date:** April 21, 2026  
**Operator:** Reveila LLC (Montgomery, Alabama)

### **1. Overview**
Reveila Personal Edition is an AI-driven Enterprise Architecture and Agentic Fabric platform. Unlike traditional AI applications that rely on cloud-based processing, Reveila is architected for **Sovereignty and Privacy**. Most data processing, including AI reasoning and tool execution, occurs locally on your device.

### **2. Information We Collect**
* **Local Data (On-Device Only):**
    * **Agentic Workspace:** Any manifests, plugin configurations, or local databases created within the "Reveila System Home" on your device.
    * **Biometric Data:** If you enable the **Biometric Kill Switch**, the app utilizes the Android BiometricPrompt API. **We do not collect, store, or have access to your actual biometric prints.** The app only receives a "success" or "failure" signal from the Android operating system.
* **Voluntary Information:**
    * **Telemetry & Logs:** If enabled by the user, raw "Flight Recorder" logs (session IDs, execution times, and errors) are stored locally. These are not transmitted to Reveila LLC unless you manually export them for support.
* **External AI Providers:**
    * If you configure a remote LLM provider (e.g., OpenAI, Gemini, or a remote Ollama cluster), the content of your prompts is sent to that provider. You are subject to their respective privacy policies.

### **3. How We Use Your Information**
* **Execution Governance:** To enforce the **Agency Perimeter** and ensure that autonomous agents stay within your defined security tiers.
* **System Integration:** To facilitate local tool calls (e.g., EchoService) through our native runtime.

### **4. Data Storage and Security**
* **Hexagonal Isolation:** Your data is isolated using a hexagonal architecture pattern, ensuring that the AI reasoning layer cannot access sensitive system files without explicit manifest-based permission.
* **Encryption:** Locally stored secrets (API keys for remote LLMs) are secured using the **Android Keystore system**.

### **5. Third-Party Sharing**
We **do not sell, rent, or trade** your personal information. Data is only shared with third-party AI providers that *you* explicitly configure in the app settings.

### **6. Your Rights and Controls**
* **Right to Deletion:** You can delete the entire Reveila System Home directory at any time, which wipes all local memory, manifests, and logs.
* **Kill Switch Control:** You can toggle the Biometric Kill Switch or the Global Kill Switch at any time to halt all agentic autonomy.

### **7. Contact Information**
For privacy inquiries related to Reveila LLC, please contact:
**Charles C Lee** Reveila LLC  
Montgomery, AL  
