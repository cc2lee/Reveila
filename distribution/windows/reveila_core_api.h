#ifndef KONAN_REVEILA_CORE_H
#define KONAN_REVEILA_CORE_H
#ifdef __cplusplus
extern "C" {
#endif
#ifdef __cplusplus
typedef bool            reveila_core_KBoolean;
#else
typedef _Bool           reveila_core_KBoolean;
#endif
typedef unsigned short     reveila_core_KChar;
typedef signed char        reveila_core_KByte;
typedef short              reveila_core_KShort;
typedef int                reveila_core_KInt;
typedef long long          reveila_core_KLong;
typedef unsigned char      reveila_core_KUByte;
typedef unsigned short     reveila_core_KUShort;
typedef unsigned int       reveila_core_KUInt;
typedef unsigned long long reveila_core_KULong;
typedef float              reveila_core_KFloat;
typedef double             reveila_core_KDouble;
#ifndef _MSC_VER
typedef float __attribute__ ((__vector_size__ (16))) reveila_core_KVector128;
#else
#include <xmmintrin.h>
typedef __m128 reveila_core_KVector128;
#endif
typedef void*              reveila_core_KNativePtr;
struct reveila_core_KType;
typedef struct reveila_core_KType reveila_core_KType;

typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Byte;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Short;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Int;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Long;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Float;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Double;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Char;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Boolean;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Unit;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_UByte;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_UShort;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_UInt;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_ULong;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_AgentSession;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_ReveilaChatMemory;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_collections_MutableMap;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Any;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_AgentSession_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_util_GemmaPromptFormatter;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_util_GemmaPromptFormatter_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_collections_List;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_util_Llama3PromptFormatter;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_util_Llama3PromptFormatter_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_AgentSessionManager;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_collections_Map;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_AgenticFabric;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Plugin;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Subject;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_AgenticFabric_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_AiIntentValidator;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_GuardrailResponse;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_AiResponseValidator;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_AiWatchdog;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmProvider;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_AiWatchdog_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_AutonomousAgent;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_BaseLlmProvider;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmRequest;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_HttpClientService;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmResponse;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_DynamicToolProvider;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_persistence_VectorStore;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_MetadataRegistry;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_ToolScoringModel;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_ReveilaEmbeddingModel;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_GeminiLlmProvider;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_InboundWebhookService;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_InvocationResult;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_InboundWebhookService_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmException;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Throwable;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmProviderFactory;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmRequest_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmRequest_Builder;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_collections_MutableList;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_ReveilaMessage;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmTool;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_Usage;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmRole;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmRole_SYSTEM;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmRole_USER;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmRole_ASSISTANT;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmRole_TOOL;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmScoringModel;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LlmScoringModel_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LocalLlamaProvider;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_LocalLlmServer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_OpenAiLlmProvider;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_OrchestrationService;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_Prompt;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_Prompt_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_FloatArray;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_ReveilaMessage_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_ReveilaTool;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_ToolCall;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_ToolDispatcher;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_TraceContextHolder;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_TraceContextHolder_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_TrackedLlmProvider;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_UsageTracker;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_ai_PlatformTraceContext;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_crypto_PlatformCryptographer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_crypto_CryptoException;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_crypto_Cryptographer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_ByteArray;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_crypto_DefaultCryptographer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_crypto_DefaultCryptographer_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_crypto_PlatformCryptographer_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_crypto_PlatformCryptographerAdapter;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_crypto_MingwPlatformCryptographer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Entity;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Entity_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_LogicalOp;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_SearchOp;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_LogicalOp_AND;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_LogicalOp_OR;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_SearchOp_EQUAL;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_SearchOp_LIKE;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_SearchOp_IN;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_SearchOp_GREATER_THAN;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_SearchOp_LESS_THAN;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_Criterion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Filter_Criterion_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_JsonFileRepository;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Optional;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Sort;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Page;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_collections_Collection;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_QueryRequest;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_QueryRequest_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_SearchRequest;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Sort_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_error_ConfigurationException;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_error_ErrorCode;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_error_ErrorUtil;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_error_ExceptionCollection;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_error_SecurityException;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_error_SystemException;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_event_AutoCallEvent;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_event_AutoCallEvent_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_event_EventConsumer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_event_EventObject;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_event_EventManager;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_persistence_SchemaInitializer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_persistence_SchemaInitializer_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_persistence_VectorMatch;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SecurityPerimeter;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_FlightRecorder;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_GuardedRuntime;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_GuardrailResponse_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_IntentValidator;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_InvocationResult_Status;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_InvocationResult_Status_SUCCESS;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_InvocationResult_Status_ERROR;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_InvocationResult_Status_PENDING_APPROVAL;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_InvocationResult_Status_SECURITY_BREACH;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_InvocationResult_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_JsonSchemaEnforcer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_KillSwitch;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyStatus;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_ManagedInvocation;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_collections_Set;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_PolicyEnforcement;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_PolicyEnforcement_AuthorizationStatus;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_PolicyEnforcement_AuthorizationStatus_AUTHORIZED;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_PolicyEnforcement_AuthorizationStatus_DENIED;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_PolicyEnforcement_AuthorizationStatus_HUMAN_APPROVAL_REQUIRED;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_ReveilaIntent;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyAction;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyAction_HALT;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyAction_ISOLATE;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyAction_KILL;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyCommand;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyCommandListener;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyStatus_ACTIVE;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyStatus_KILLED;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyStatus_PENDING_AUTHORIZATION;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SchemaEnforcer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SecretManager;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SecretManager_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_PlatformHttpEngine;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_DataService;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Repository;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_EchoService;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Array;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_HttpClientService_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_PlatformHttpResponse;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_PlatformHttpEngine_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_RatedService;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_RemoteService;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_MingwPlatformHttpEngine;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_AbstractComponent;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_logging_PlatformLogger;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_ComponentState;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_concurrency_PlatformScheduler;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_concurrency_PlatformCancellable;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Function0;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_concurrency_PlatformScheduler_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_concurrency_MingwPlatformScheduler;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_io_PlatformFileSystem;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_io_PlatformFileSystem_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_logging_PlatformLogger_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_logging_MingwPlatformLogger;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_platform_PlatformSystem;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_platform_PlatformOsInfo;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_platform_PlatformSystem_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_platform_MingwPlatformSystem;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_ComponentState_INITIALIZED;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_ComponentState_STARTING;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_ComponentState_ACTIVE;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_ComponentState_FAILED;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_ComponentState_STOPPING;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_ComponentState_STOPPED;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Configuration;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_MetaObject;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_ConfigurationLinter;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Properties;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Constants;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Context;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_PlatformAdapter;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Proxy;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_DependencyValidator;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Manifest;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Manifest_ExposedMethod;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Manifest_Parameter;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_PerformanceTracker;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_PerformanceTracker_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_PerimeterEnforcementMerger;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_PerimeterEnforcementMerger_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_ReveilaEngine;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Plugin_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_PluginComponent;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_PluginContext;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_SystemContext;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_PluginProxy;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_PluginRunner;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Principal;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_UserPrincipal;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_RolePrincipal;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Function2;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_ReveilaClient;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Startable;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Stoppable;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_collections_MutableSet;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_SystemComponent;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_SystemHome;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_SystemHome_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_UiController;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_tools_Terminal;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_tools_Terminal_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_SafeCast;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_io_FileUtil;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_io_FileUtil_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_io_FormField;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_json_JsonException;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_json_JsonUtil;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_json_JsonUtil_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlinx_serialization_json_Json;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlinx_serialization_json_JsonElement;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_md_Markdown;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_md_Markdown_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_xml_XmlElement;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_xml_XmlDocument;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_xml_XmlErrorHandler;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_xml_XmlUtil;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_xml_XmlUtil_Companion;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_ScoreTracker;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_StringUtil;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_TimeFormat;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_Uuid;


typedef struct {
  /* Service functions. */
  void (*DisposeStablePointer)(reveila_core_KNativePtr ptr);
  void (*DisposeString)(const char* string);
  reveila_core_KBoolean (*IsInstance)(reveila_core_KNativePtr ref, const reveila_core_KType* type);
  reveila_core_kref_kotlin_Byte (*createNullableByte)(reveila_core_KByte);
  reveila_core_KByte (*getNonNullValueOfByte)(reveila_core_kref_kotlin_Byte);
  reveila_core_kref_kotlin_Short (*createNullableShort)(reveila_core_KShort);
  reveila_core_KShort (*getNonNullValueOfShort)(reveila_core_kref_kotlin_Short);
  reveila_core_kref_kotlin_Int (*createNullableInt)(reveila_core_KInt);
  reveila_core_KInt (*getNonNullValueOfInt)(reveila_core_kref_kotlin_Int);
  reveila_core_kref_kotlin_Long (*createNullableLong)(reveila_core_KLong);
  reveila_core_KLong (*getNonNullValueOfLong)(reveila_core_kref_kotlin_Long);
  reveila_core_kref_kotlin_Float (*createNullableFloat)(reveila_core_KFloat);
  reveila_core_KFloat (*getNonNullValueOfFloat)(reveila_core_kref_kotlin_Float);
  reveila_core_kref_kotlin_Double (*createNullableDouble)(reveila_core_KDouble);
  reveila_core_KDouble (*getNonNullValueOfDouble)(reveila_core_kref_kotlin_Double);
  reveila_core_kref_kotlin_Char (*createNullableChar)(reveila_core_KChar);
  reveila_core_KChar (*getNonNullValueOfChar)(reveila_core_kref_kotlin_Char);
  reveila_core_kref_kotlin_Boolean (*createNullableBoolean)(reveila_core_KBoolean);
  reveila_core_KBoolean (*getNonNullValueOfBoolean)(reveila_core_kref_kotlin_Boolean);
  reveila_core_kref_kotlin_Unit (*createNullableUnit)(void);
  reveila_core_kref_kotlin_UByte (*createNullableUByte)(reveila_core_KUByte);
  reveila_core_KUByte (*getNonNullValueOfUByte)(reveila_core_kref_kotlin_UByte);
  reveila_core_kref_kotlin_UShort (*createNullableUShort)(reveila_core_KUShort);
  reveila_core_KUShort (*getNonNullValueOfUShort)(reveila_core_kref_kotlin_UShort);
  reveila_core_kref_kotlin_UInt (*createNullableUInt)(reveila_core_KUInt);
  reveila_core_KUInt (*getNonNullValueOfUInt)(reveila_core_kref_kotlin_UInt);
  reveila_core_kref_kotlin_ULong (*createNullableULong)(reveila_core_KULong);
  reveila_core_KULong (*getNonNullValueOfULong)(reveila_core_kref_kotlin_ULong);

  /* User functions. */
  struct {
    struct {
      struct {
        struct {
          struct {
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_ai_AgentSession_Companion (*_instance)();
                const char* (*get_ID)(reveila_core_kref_com_reveila_ai_AgentSession_Companion thiz);
                const char* (*get_THOUGHT)(reveila_core_kref_com_reveila_ai_AgentSession_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_AgentSession (*AgentSession)(const char* sessionId, const char* parentTraceId, reveila_core_KInt windowSize);
              reveila_core_kref_com_reveila_ai_ReveilaChatMemory (*get_chatMemory)(reveila_core_kref_com_reveila_ai_AgentSession thiz);
              reveila_core_kref_kotlin_collections_MutableMap (*get_context)(reveila_core_kref_com_reveila_ai_AgentSession thiz);
              const char* (*get_parentTraceId)(reveila_core_kref_com_reveila_ai_AgentSession thiz);
              const char* (*get_sessionId)(reveila_core_kref_com_reveila_ai_AgentSession thiz);
              reveila_core_kref_kotlin_Any (*get)(reveila_core_kref_com_reveila_ai_AgentSession thiz, const char* key);
              reveila_core_kref_kotlin_collections_MutableMap (*getContextMap)(reveila_core_kref_com_reveila_ai_AgentSession thiz);
              void (*put)(reveila_core_kref_com_reveila_ai_AgentSession thiz, const char* key, reveila_core_kref_kotlin_Any value);
            } AgentSession;
            struct {
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_ai_util_GemmaPromptFormatter_Companion (*_instance)();
                  const char* (*format)(reveila_core_kref_com_reveila_ai_util_GemmaPromptFormatter_Companion thiz, reveila_core_kref_kotlin_collections_List messages);
                } Companion;
                reveila_core_KType* (*_type)(void);
              } GemmaPromptFormatter;
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_ai_util_Llama3PromptFormatter_Companion (*_instance)();
                  const char* (*format)(reveila_core_kref_com_reveila_ai_util_Llama3PromptFormatter_Companion thiz, reveila_core_kref_kotlin_collections_List messages);
                } Companion;
                reveila_core_KType* (*_type)(void);
              } Llama3PromptFormatter;
            } util;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_AgentSessionManager (*AgentSessionManager)();
              void (*clear)(reveila_core_kref_com_reveila_ai_AgentSessionManager thiz, const char* id);
              reveila_core_kref_kotlin_collections_MutableMap (*getContext)(reveila_core_kref_com_reveila_ai_AgentSessionManager thiz, const char* traceId);
              reveila_core_kref_com_reveila_ai_AgentSession (*getSession)(reveila_core_kref_com_reveila_ai_AgentSessionManager thiz, const char* id);
              void (*onStart)(reveila_core_kref_com_reveila_ai_AgentSessionManager thiz);
              void (*onStop)(reveila_core_kref_com_reveila_ai_AgentSessionManager thiz);
              void (*saveContext)(reveila_core_kref_com_reveila_ai_AgentSessionManager thiz, const char* traceId, reveila_core_kref_kotlin_collections_Map context);
              void (*saveSession)(reveila_core_kref_com_reveila_ai_AgentSessionManager thiz, const char* id, reveila_core_kref_com_reveila_ai_AgentSession session);
            } AgentSessionManager;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_ai_AgenticFabric_Companion (*_instance)();
                const char* (*get_COMPONENT_NAME)(reveila_core_kref_com_reveila_ai_AgenticFabric_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_AgenticFabric (*AgenticFabric)();
              reveila_core_KInt (*get_aiLoopLimit)(reveila_core_kref_com_reveila_ai_AgenticFabric thiz);
              void (*set_aiLoopLimit)(reveila_core_kref_com_reveila_ai_AgenticFabric thiz, reveila_core_KInt set);
              reveila_core_kref_kotlin_collections_Map (*askAgent)(reveila_core_kref_com_reveila_ai_AgenticFabric thiz, const char* userIntent, const char* sessionId, const char* systemPrompt);
              reveila_core_kref_kotlin_Any (*delegate)(reveila_core_kref_com_reveila_ai_AgenticFabric thiz, reveila_core_kref_com_reveila_system_Plugin parent, const char* targetIntent, reveila_core_kref_kotlin_collections_Map taskArguments);
              const char* (*interpretAiResponse)(reveila_core_kref_com_reveila_ai_AgenticFabric thiz, reveila_core_kref_kotlin_collections_Map jsonResponse);
              void (*onStart)(reveila_core_kref_com_reveila_ai_AgenticFabric thiz);
              void (*onStop)(reveila_core_kref_com_reveila_ai_AgenticFabric thiz);
              reveila_core_kref_kotlin_collections_Map (*processIntent)(reveila_core_kref_com_reveila_ai_AgenticFabric thiz, reveila_core_kref_com_reveila_ai_AgentSession session, reveila_core_kref_com_reveila_system_Subject subject, const char* intent);
              const char* (*summarizeSession)(reveila_core_kref_com_reveila_ai_AgenticFabric thiz, const char* sessionId);
            } AgenticFabric;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_AiIntentValidator (*AiIntentValidator)();
              reveila_core_kref_com_reveila_safety_GuardrailResponse (*getGuardrailResponse)(reveila_core_kref_com_reveila_ai_AiIntentValidator thiz, const char* pluginId, const char* maskedArgs, const char* systemContext);
              void (*onStart)(reveila_core_kref_com_reveila_ai_AiIntentValidator thiz);
              void (*onStop)(reveila_core_kref_com_reveila_ai_AiIntentValidator thiz);
              reveila_core_KBoolean (*performSafetyAudit)(reveila_core_kref_com_reveila_ai_AiIntentValidator thiz, const char* pluginId, const char* maskedArgs, const char* systemContext);
              void (*validateIntent)(reveila_core_kref_com_reveila_ai_AiIntentValidator thiz, const char* intent);
            } AiIntentValidator;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_AiResponseValidator (*AiResponseValidator)();
              const char* (*getMessage)(reveila_core_kref_com_reveila_ai_AiResponseValidator thiz, const char* agentResponse);
            } AiResponseValidator;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_ai_AiWatchdog_Companion (*_instance)();
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_AiWatchdog (*AiWatchdog)(reveila_core_kref_com_reveila_ai_LlmProvider remoteLlmProvider, reveila_core_kref_com_reveila_ai_LlmProvider localOllama);
              const char* (*getResponse)(reveila_core_kref_com_reveila_ai_AiWatchdog thiz, const char* userPrompt);
            } AiWatchdog;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_AutonomousAgent (*AutonomousAgent)();
              void (*doTask)(reveila_core_kref_com_reveila_ai_AutonomousAgent thiz);
              void (*onStart)(reveila_core_kref_com_reveila_ai_AutonomousAgent thiz);
              void (*onStop)(reveila_core_kref_com_reveila_ai_AutonomousAgent thiz);
            } AutonomousAgent;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_BaseLlmProvider (*BaseLlmProvider)();
              const char* (*get_apiKey)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              void (*set_apiKey)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz, const char* value);
              reveila_core_KBoolean (*get_enabled)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              void (*set_enabled)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz, reveila_core_KBoolean set);
              const char* (*get_endpoint)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              void (*set_endpoint)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz, const char* set);
              const char* (*get_model)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              void (*set_model)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz, const char* set);
              const char* (*get_resolvedApiKey)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              void (*set_resolvedApiKey)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz, const char* set);
              reveila_core_KDouble (*get_temperature)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              void (*set_temperature)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz, reveila_core_KDouble set);
              const char* (*buildRequestBody)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz, reveila_core_kref_com_reveila_ai_LlmRequest request);
              reveila_core_kref_kotlin_collections_Map (*getHeaders)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              reveila_core_kref_com_reveila_service_HttpClientService (*getHttpClientService)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              const char* (*getName)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              reveila_core_kref_com_reveila_ai_LlmResponse (*invoke)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz, reveila_core_kref_com_reveila_ai_LlmRequest request);
              reveila_core_KBoolean (*isEnabled)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              void (*onStart)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              void (*onStop)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              reveila_core_kref_com_reveila_ai_LlmResponse (*parseResponse)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz, const char* json);
              const char* (*resolveApiKey)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz);
              void (*setName)(reveila_core_kref_com_reveila_ai_BaseLlmProvider thiz, const char* name);
            } BaseLlmProvider;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_DynamicToolProvider (*DynamicToolProvider)(reveila_core_kref_com_reveila_persistence_VectorStore toolVectorStore, reveila_core_kref_com_reveila_safety_MetadataRegistry registry, reveila_core_kref_com_reveila_ai_ToolScoringModel reranker, reveila_core_kref_com_reveila_ai_ReveilaEmbeddingModel embeddingModel, const char* securityTier);
              reveila_core_kref_kotlin_collections_List (*provideTools)(reveila_core_kref_com_reveila_ai_DynamicToolProvider thiz, const char* query);
              void (*setTopK)(reveila_core_kref_com_reveila_ai_DynamicToolProvider thiz, reveila_core_KInt topK);
            } DynamicToolProvider;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_GeminiLlmProvider (*GeminiLlmProvider)();
              const char* (*buildRequestBody)(reveila_core_kref_com_reveila_ai_GeminiLlmProvider thiz, reveila_core_kref_com_reveila_ai_LlmRequest request);
              reveila_core_kref_kotlin_collections_Map (*getHeaders)(reveila_core_kref_com_reveila_ai_GeminiLlmProvider thiz);
            } GeminiLlmProvider;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_ai_InboundWebhookService_Companion (*_instance)();
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_InboundWebhookService (*InboundWebhookService)();
              reveila_core_kref_com_reveila_safety_InvocationResult (*ingest)(reveila_core_kref_com_reveila_ai_InboundWebhookService thiz, reveila_core_kref_kotlin_collections_Map payload);
              void (*onStart)(reveila_core_kref_com_reveila_ai_InboundWebhookService thiz);
              void (*onStop)(reveila_core_kref_com_reveila_ai_InboundWebhookService thiz);
            } InboundWebhookService;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_LlmException (*LlmException)(const char* message);
              reveila_core_kref_com_reveila_ai_LlmException (*LlmException_)(const char* message, reveila_core_kref_kotlin_Throwable cause);
              reveila_core_kref_com_reveila_ai_LlmException (*LlmException__)(const char* message, reveila_core_kref_kotlin_Throwable cause, const char* errorCode);
            } LlmException;
            struct {
              reveila_core_KType* (*_type)(void);
              const char* (*getName)(reveila_core_kref_com_reveila_ai_LlmProvider thiz);
              reveila_core_kref_com_reveila_ai_LlmResponse (*invoke)(reveila_core_kref_com_reveila_ai_LlmProvider thiz, reveila_core_kref_com_reveila_ai_LlmRequest request);
              reveila_core_KBoolean (*isConfigured)(reveila_core_kref_com_reveila_ai_LlmProvider thiz);
              reveila_core_KBoolean (*isEnabled)(reveila_core_kref_com_reveila_ai_LlmProvider thiz);
            } LlmProvider;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_LlmProviderFactory (*LlmProviderFactory)();
              reveila_core_kref_com_reveila_ai_LlmProvider (*getActiveProvider)(reveila_core_kref_com_reveila_ai_LlmProviderFactory thiz);
              reveila_core_kref_com_reveila_ai_LlmProvider (*getProvider)(reveila_core_kref_com_reveila_ai_LlmProviderFactory thiz, const char* name);
              void (*loadProviders)(reveila_core_kref_com_reveila_ai_LlmProviderFactory thiz);
              void (*onStart)(reveila_core_kref_com_reveila_ai_LlmProviderFactory thiz);
              void (*onStop)(reveila_core_kref_com_reveila_ai_LlmProviderFactory thiz);
              void (*setActiveProvider)(reveila_core_kref_com_reveila_ai_LlmProviderFactory thiz, const char* name);
            } LlmProviderFactory;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_ai_LlmRequest_Companion (*_instance)();
                reveila_core_kref_com_reveila_ai_LlmRequest_Builder (*builder)(reveila_core_kref_com_reveila_ai_LlmRequest_Companion thiz);
              } Companion;
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_ai_LlmRequest_Builder (*Builder)();
                reveila_core_kref_kotlin_collections_MutableList (*get_messages)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz);
                reveila_core_kref_kotlin_collections_MutableMap (*get_metadata)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz);
                const char* (*get_modelId)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz);
                reveila_core_kref_kotlin_Double (*get_temperature)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz);
                reveila_core_kref_kotlin_collections_MutableList (*get_tools)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz);
                reveila_core_kref_com_reveila_ai_LlmRequest_Builder (*addMessage)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz, reveila_core_kref_com_reveila_ai_ReveilaMessage message);
                reveila_core_kref_com_reveila_ai_LlmRequest_Builder (*addMetadata)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz, const char* key, reveila_core_kref_kotlin_Any value);
                reveila_core_kref_com_reveila_ai_LlmRequest_Builder (*addTool)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz, reveila_core_kref_com_reveila_ai_LlmTool tool);
                reveila_core_kref_com_reveila_ai_LlmRequest (*build)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz);
                reveila_core_kref_com_reveila_ai_LlmRequest_Builder (*messages)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz, reveila_core_kref_kotlin_collections_List messages);
                reveila_core_kref_com_reveila_ai_LlmRequest_Builder (*metadata)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz, reveila_core_kref_kotlin_collections_Map metadata);
                reveila_core_kref_com_reveila_ai_LlmRequest_Builder (*modelId)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz, const char* modelId);
                reveila_core_kref_com_reveila_ai_LlmRequest_Builder (*temperature)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz, reveila_core_kref_kotlin_Double temperature);
                const char* (*toString)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz);
                reveila_core_kref_com_reveila_ai_LlmRequest_Builder (*tools)(reveila_core_kref_com_reveila_ai_LlmRequest_Builder thiz, reveila_core_kref_kotlin_collections_List tools);
              } Builder;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_kotlin_collections_List (*get_messages)(reveila_core_kref_com_reveila_ai_LlmRequest thiz);
              reveila_core_kref_kotlin_collections_Map (*get_metadata)(reveila_core_kref_com_reveila_ai_LlmRequest thiz);
              const char* (*get_modelId)(reveila_core_kref_com_reveila_ai_LlmRequest thiz);
              reveila_core_kref_kotlin_Double (*get_temperature)(reveila_core_kref_com_reveila_ai_LlmRequest thiz);
              reveila_core_kref_kotlin_collections_List (*get_tools)(reveila_core_kref_com_reveila_ai_LlmRequest thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_ai_LlmRequest thiz);
            } LlmRequest;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_LlmResponse (*LlmResponse)();
              reveila_core_kref_com_reveila_ai_LlmResponse (*LlmResponse_)(const char* content, const char* finishReason, reveila_core_kref_com_reveila_ai_Usage usage, reveila_core_kref_kotlin_collections_List toolCalls, const char* requestId);
              const char* (*get_content)(reveila_core_kref_com_reveila_ai_LlmResponse thiz);
              void (*set_content)(reveila_core_kref_com_reveila_ai_LlmResponse thiz, const char* set);
              const char* (*get_finishReason)(reveila_core_kref_com_reveila_ai_LlmResponse thiz);
              void (*set_finishReason)(reveila_core_kref_com_reveila_ai_LlmResponse thiz, const char* set);
              const char* (*get_requestId)(reveila_core_kref_com_reveila_ai_LlmResponse thiz);
              void (*set_requestId)(reveila_core_kref_com_reveila_ai_LlmResponse thiz, const char* set);
              reveila_core_kref_kotlin_collections_List (*get_toolCalls)(reveila_core_kref_com_reveila_ai_LlmResponse thiz);
              void (*set_toolCalls)(reveila_core_kref_com_reveila_ai_LlmResponse thiz, reveila_core_kref_kotlin_collections_List set);
              reveila_core_kref_com_reveila_ai_Usage (*get_usage)(reveila_core_kref_com_reveila_ai_LlmResponse thiz);
              void (*set_usage)(reveila_core_kref_com_reveila_ai_LlmResponse thiz, reveila_core_kref_com_reveila_ai_Usage set);
            } LlmResponse;
            struct {
              struct {
                reveila_core_kref_com_reveila_ai_LlmRole (*get)(); /* enum entry for SYSTEM. */
              } SYSTEM;
              struct {
                reveila_core_kref_com_reveila_ai_LlmRole (*get)(); /* enum entry for USER. */
              } USER;
              struct {
                reveila_core_kref_com_reveila_ai_LlmRole (*get)(); /* enum entry for ASSISTANT. */
              } ASSISTANT;
              struct {
                reveila_core_kref_com_reveila_ai_LlmRole (*get)(); /* enum entry for TOOL. */
              } TOOL;
              reveila_core_KType* (*_type)(void);
            } LlmRole;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_ai_LlmScoringModel_Companion (*_instance)();
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_LlmScoringModel (*LlmScoringModel)(reveila_core_kref_com_reveila_ai_LlmProvider provider);
              reveila_core_kref_kotlin_collections_List (*scoreAll)(reveila_core_kref_com_reveila_ai_LlmScoringModel thiz, const char* query, reveila_core_kref_kotlin_collections_List candidates);
            } LlmScoringModel;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_LlmTool (*LlmTool)();
              const char* (*get_description)(reveila_core_kref_com_reveila_ai_LlmTool thiz);
              void (*set_description)(reveila_core_kref_com_reveila_ai_LlmTool thiz, const char* set);
              const char* (*get_name)(reveila_core_kref_com_reveila_ai_LlmTool thiz);
              void (*set_name)(reveila_core_kref_com_reveila_ai_LlmTool thiz, const char* set);
              reveila_core_kref_kotlin_collections_Map (*get_parameterSchema)(reveila_core_kref_com_reveila_ai_LlmTool thiz);
              void (*set_parameterSchema)(reveila_core_kref_com_reveila_ai_LlmTool thiz, reveila_core_kref_kotlin_collections_Map value);
              reveila_core_kref_kotlin_collections_MutableMap (*get_reveilaMetadata)(reveila_core_kref_com_reveila_ai_LlmTool thiz);
              void (*set_reveilaMetadata)(reveila_core_kref_com_reveila_ai_LlmTool thiz, reveila_core_kref_kotlin_collections_MutableMap set);
              void (*setReveilaMetadata)(reveila_core_kref_com_reveila_ai_LlmTool thiz, const char* tier, reveila_core_kref_kotlin_collections_List humanInTheLoop, reveila_core_kref_kotlin_collections_List accessScopes);
              const char* (*toJsonString)(reveila_core_kref_com_reveila_ai_LlmTool thiz);
              const char* (*toPlainText)(reveila_core_kref_com_reveila_ai_LlmTool thiz);
            } LlmTool;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_LocalLlamaProvider (*LocalLlamaProvider)();
              const char* (*buildRequestBody)(reveila_core_kref_com_reveila_ai_LocalLlamaProvider thiz, reveila_core_kref_com_reveila_ai_LlmRequest request);
              reveila_core_kref_kotlin_collections_Map (*getHeaders)(reveila_core_kref_com_reveila_ai_LocalLlamaProvider thiz);
              reveila_core_KBoolean (*isConfigured)(reveila_core_kref_com_reveila_ai_LocalLlamaProvider thiz);
              reveila_core_kref_com_reveila_ai_LlmResponse (*parseResponse)(reveila_core_kref_com_reveila_ai_LocalLlamaProvider thiz, const char* json);
            } LocalLlamaProvider;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_LocalLlmServer (*LocalLlmServer)(const char* executablePath, const char* modelFilePath);
              reveila_core_KBoolean (*isRunning)(reveila_core_kref_com_reveila_ai_LocalLlmServer thiz);
              void (*start)(reveila_core_kref_com_reveila_ai_LocalLlmServer thiz);
              void (*stop)(reveila_core_kref_com_reveila_ai_LocalLlmServer thiz);
            } LocalLlmServer;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_OpenAiLlmProvider (*OpenAiLlmProvider)();
              const char* (*buildRequestBody)(reveila_core_kref_com_reveila_ai_OpenAiLlmProvider thiz, reveila_core_kref_com_reveila_ai_LlmRequest request);
              reveila_core_kref_kotlin_collections_Map (*getHeaders)(reveila_core_kref_com_reveila_ai_OpenAiLlmProvider thiz);
              reveila_core_KBoolean (*isConfigured)(reveila_core_kref_com_reveila_ai_OpenAiLlmProvider thiz);
              reveila_core_kref_com_reveila_ai_LlmResponse (*parseResponse)(reveila_core_kref_com_reveila_ai_OpenAiLlmProvider thiz, const char* json);
            } OpenAiLlmProvider;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_OrchestrationService (*OrchestrationService)();
              const char* (*get_optimizationPriority)(reveila_core_kref_com_reveila_ai_OrchestrationService thiz);
              void (*closeSession)(reveila_core_kref_com_reveila_ai_OrchestrationService thiz, const char* sessionId);
              reveila_core_kref_com_reveila_ai_AgentSession (*createSession)(reveila_core_kref_com_reveila_ai_OrchestrationService thiz, const char* sessionId, const char* parentTraceId);
              reveila_core_kref_com_reveila_ai_AgentSession (*createSession_)(reveila_core_kref_com_reveila_ai_OrchestrationService thiz, const char* parentTraceId);
              reveila_core_kref_kotlin_collections_List (*getActiveSessions)(reveila_core_kref_com_reveila_ai_OrchestrationService thiz);
              reveila_core_KInt (*getMaxMessages)(reveila_core_kref_com_reveila_ai_OrchestrationService thiz);
              reveila_core_kref_com_reveila_ai_AgentSession (*getSession)(reveila_core_kref_com_reveila_ai_OrchestrationService thiz, const char* sessionId);
              reveila_core_kref_kotlin_collections_List (*getSessionHistory)(reveila_core_kref_com_reveila_ai_OrchestrationService thiz, const char* sessionId);
              void (*onStart)(reveila_core_kref_com_reveila_ai_OrchestrationService thiz);
              void (*onStop)(reveila_core_kref_com_reveila_ai_OrchestrationService thiz);
            } OrchestrationService;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_ai_Prompt_Companion (*_instance)();
                const char* (*getSystemPrompt)(reveila_core_kref_com_reveila_ai_Prompt_Companion thiz, const char* role, const char* context, const char* constraints);
              } Companion;
              reveila_core_KType* (*_type)(void);
            } Prompt;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_ReveilaChatMemory (*ReveilaChatMemory)(reveila_core_KInt maxMessages);
              void (*add)(reveila_core_kref_com_reveila_ai_ReveilaChatMemory thiz, reveila_core_kref_com_reveila_ai_ReveilaMessage message);
              void (*clear)(reveila_core_kref_com_reveila_ai_ReveilaChatMemory thiz);
              reveila_core_kref_kotlin_collections_List (*messages)(reveila_core_kref_com_reveila_ai_ReveilaChatMemory thiz);
            } ReveilaChatMemory;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_kotlin_FloatArray (*embed)(reveila_core_kref_com_reveila_ai_ReveilaEmbeddingModel thiz, const char* text);
            } ReveilaEmbeddingModel;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_ai_ReveilaMessage_Companion (*_instance)();
                reveila_core_kref_com_reveila_ai_ReveilaMessage (*assistant)(reveila_core_kref_com_reveila_ai_ReveilaMessage_Companion thiz, const char* content);
                reveila_core_kref_com_reveila_ai_ReveilaMessage (*system)(reveila_core_kref_com_reveila_ai_ReveilaMessage_Companion thiz, const char* content);
                reveila_core_kref_com_reveila_ai_ReveilaMessage (*tool)(reveila_core_kref_com_reveila_ai_ReveilaMessage_Companion thiz, const char* content);
                reveila_core_kref_com_reveila_ai_ReveilaMessage (*user)(reveila_core_kref_com_reveila_ai_ReveilaMessage_Companion thiz, const char* content);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_ReveilaMessage (*ReveilaMessage)(reveila_core_kref_com_reveila_ai_LlmRole role, const char* content);
              const char* (*get_content)(reveila_core_kref_com_reveila_ai_ReveilaMessage thiz);
              reveila_core_kref_com_reveila_ai_LlmRole (*get_role)(reveila_core_kref_com_reveila_ai_ReveilaMessage thiz);
              reveila_core_kref_com_reveila_ai_LlmRole (*component1)(reveila_core_kref_com_reveila_ai_ReveilaMessage thiz);
              const char* (*component2)(reveila_core_kref_com_reveila_ai_ReveilaMessage thiz);
              const char* (*content)(reveila_core_kref_com_reveila_ai_ReveilaMessage thiz);
              reveila_core_kref_com_reveila_ai_ReveilaMessage (*copy)(reveila_core_kref_com_reveila_ai_ReveilaMessage thiz, reveila_core_kref_com_reveila_ai_LlmRole role, const char* content);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_ai_ReveilaMessage thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_ai_ReveilaMessage thiz);
              reveila_core_kref_com_reveila_ai_LlmRole (*role)(reveila_core_kref_com_reveila_ai_ReveilaMessage thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_ai_ReveilaMessage thiz);
            } ReveilaMessage;
            struct {
              reveila_core_KType* (*_type)(void);
              const char* (*execute)(reveila_core_kref_com_reveila_ai_ReveilaTool thiz, reveila_core_kref_kotlin_collections_Map args);
              const char* (*getDescription)(reveila_core_kref_com_reveila_ai_ReveilaTool thiz);
              const char* (*getName)(reveila_core_kref_com_reveila_ai_ReveilaTool thiz);
            } ReveilaTool;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_ToolCall (*ToolCall)(const char* functionName, reveila_core_kref_kotlin_Any arguments);
              reveila_core_kref_kotlin_Any (*get_arguments)(reveila_core_kref_com_reveila_ai_ToolCall thiz);
              void (*set_arguments)(reveila_core_kref_com_reveila_ai_ToolCall thiz, reveila_core_kref_kotlin_Any set);
              const char* (*get_functionName)(reveila_core_kref_com_reveila_ai_ToolCall thiz);
              void (*set_functionName)(reveila_core_kref_com_reveila_ai_ToolCall thiz, const char* set);
            } ToolCall;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_ToolDispatcher (*ToolDispatcher)();
              const char* (*handleAiRequest)(reveila_core_kref_com_reveila_ai_ToolDispatcher thiz, const char* jsonResponseFromLlm);
            } ToolDispatcher;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_kotlin_collections_List (*scoreAll)(reveila_core_kref_com_reveila_ai_ToolScoringModel thiz, const char* query, reveila_core_kref_kotlin_collections_List candidates);
            } ToolScoringModel;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_ai_TraceContextHolder_Companion (*_instance)();
                void (*clear)(reveila_core_kref_com_reveila_ai_TraceContextHolder_Companion thiz);
                const char* (*getTraceId)(reveila_core_kref_com_reveila_ai_TraceContextHolder_Companion thiz);
                void (*setTraceId)(reveila_core_kref_com_reveila_ai_TraceContextHolder_Companion thiz, const char* traceId);
              } Companion;
              reveila_core_KType* (*_type)(void);
            } TraceContextHolder;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_TrackedLlmProvider (*TrackedLlmProvider)(reveila_core_kref_com_reveila_ai_LlmProvider delegate, reveila_core_kref_com_reveila_ai_UsageTracker tracker);
              const char* (*getName)(reveila_core_kref_com_reveila_ai_TrackedLlmProvider thiz);
              reveila_core_kref_com_reveila_ai_LlmResponse (*invoke)(reveila_core_kref_com_reveila_ai_TrackedLlmProvider thiz, reveila_core_kref_com_reveila_ai_LlmRequest request);
              reveila_core_KBoolean (*isConfigured)(reveila_core_kref_com_reveila_ai_TrackedLlmProvider thiz);
              reveila_core_KBoolean (*isEnabled)(reveila_core_kref_com_reveila_ai_TrackedLlmProvider thiz);
            } TrackedLlmProvider;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_Usage (*Usage)();
              reveila_core_kref_com_reveila_ai_Usage (*Usage_)(reveila_core_KInt promptTokens, reveila_core_KInt completionTokens, reveila_core_KInt totalTokens, reveila_core_KInt cachedPromptTokens, reveila_core_kref_kotlin_Int reasoningTokens, reveila_core_KDouble estimatedCost);
              reveila_core_KInt (*get_cachedPromptTokens)(reveila_core_kref_com_reveila_ai_Usage thiz);
              void (*set_cachedPromptTokens)(reveila_core_kref_com_reveila_ai_Usage thiz, reveila_core_KInt set);
              reveila_core_KInt (*get_completionTokens)(reveila_core_kref_com_reveila_ai_Usage thiz);
              void (*set_completionTokens)(reveila_core_kref_com_reveila_ai_Usage thiz, reveila_core_KInt set);
              reveila_core_KDouble (*get_estimatedCost)(reveila_core_kref_com_reveila_ai_Usage thiz);
              void (*set_estimatedCost)(reveila_core_kref_com_reveila_ai_Usage thiz, reveila_core_KDouble set);
              reveila_core_KInt (*get_promptTokens)(reveila_core_kref_com_reveila_ai_Usage thiz);
              void (*set_promptTokens)(reveila_core_kref_com_reveila_ai_Usage thiz, reveila_core_KInt set);
              reveila_core_kref_kotlin_Int (*get_reasoningTokens)(reveila_core_kref_com_reveila_ai_Usage thiz);
              void (*set_reasoningTokens)(reveila_core_kref_com_reveila_ai_Usage thiz, reveila_core_kref_kotlin_Int set);
              reveila_core_KInt (*get_totalTokens)(reveila_core_kref_com_reveila_ai_Usage thiz);
              void (*set_totalTokens)(reveila_core_kref_com_reveila_ai_Usage thiz, reveila_core_KInt set);
            } Usage;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_UsageTracker (*UsageTracker)();
              void (*logUsage)(reveila_core_kref_com_reveila_ai_UsageTracker thiz, const char* tenantId, const char* requestId, const char* modelId, reveila_core_kref_com_reveila_ai_Usage usage, reveila_core_KLong latencyMs, reveila_core_kref_kotlin_collections_Map securityResult);
              void (*onStart)(reveila_core_kref_com_reveila_ai_UsageTracker thiz);
              void (*onStop)(reveila_core_kref_com_reveila_ai_UsageTracker thiz);
            } UsageTracker;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_ai_PlatformTraceContext (*_instance)();
              void (*clear)(reveila_core_kref_com_reveila_ai_PlatformTraceContext thiz);
              const char* (*get)(reveila_core_kref_com_reveila_ai_PlatformTraceContext thiz);
              void (*set)(reveila_core_kref_com_reveila_ai_PlatformTraceContext thiz, const char* value);
            } PlatformTraceContext;
          } ai;
          struct {
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_crypto_CryptoException (*CryptoException)(const char* message);
              reveila_core_kref_com_reveila_crypto_CryptoException (*CryptoException_)(const char* message, reveila_core_kref_kotlin_Throwable cause);
              reveila_core_kref_com_reveila_crypto_CryptoException (*CryptoException__)(const char* message, reveila_core_kref_kotlin_Throwable cause, const char* errorCode);
            } CryptoException;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_kotlin_ByteArray (*decrypt)(reveila_core_kref_com_reveila_crypto_Cryptographer thiz, reveila_core_kref_kotlin_ByteArray data);
              reveila_core_kref_kotlin_ByteArray (*encrypt)(reveila_core_kref_com_reveila_crypto_Cryptographer thiz, reveila_core_kref_kotlin_ByteArray data);
              reveila_core_kref_kotlin_ByteArray (*hash)(reveila_core_kref_com_reveila_crypto_Cryptographer thiz, reveila_core_kref_kotlin_ByteArray data);
            } Cryptographer;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_crypto_DefaultCryptographer_Companion (*_instance)();
                const char* (*bytesToHex)(reveila_core_kref_com_reveila_crypto_DefaultCryptographer_Companion thiz, reveila_core_kref_kotlin_ByteArray bytes);
                reveila_core_kref_kotlin_ByteArray (*deriveKey)(reveila_core_kref_com_reveila_crypto_DefaultCryptographer_Companion thiz, const char* password, reveila_core_kref_kotlin_ByteArray salt);
                reveila_core_kref_kotlin_ByteArray (*generateRandomKey)(reveila_core_kref_com_reveila_crypto_DefaultCryptographer_Companion thiz);
                const char* (*generateSaltHex)(reveila_core_kref_com_reveila_crypto_DefaultCryptographer_Companion thiz);
                reveila_core_kref_kotlin_ByteArray (*hexToBytes)(reveila_core_kref_com_reveila_crypto_DefaultCryptographer_Companion thiz, const char* s);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_crypto_DefaultCryptographer (*DefaultCryptographer)(const char* password, reveila_core_kref_kotlin_ByteArray salt);
              reveila_core_kref_com_reveila_crypto_DefaultCryptographer (*DefaultCryptographer_)();
              reveila_core_kref_com_reveila_crypto_DefaultCryptographer (*DefaultCryptographer__)(reveila_core_kref_kotlin_ByteArray secretKey, reveila_core_kref_com_reveila_crypto_PlatformCryptographer platformCrypto);
            } DefaultCryptographer;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_crypto_PlatformCryptographer_Companion (*_instance)();
                reveila_core_kref_com_reveila_crypto_PlatformCryptographer (*invoke)(reveila_core_kref_com_reveila_crypto_PlatformCryptographer_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_kotlin_ByteArray (*base64Decode)(reveila_core_kref_com_reveila_crypto_PlatformCryptographer thiz, const char* encoded);
              const char* (*base64Encode)(reveila_core_kref_com_reveila_crypto_PlatformCryptographer thiz, reveila_core_kref_kotlin_ByteArray data);
              reveila_core_kref_kotlin_ByteArray (*decrypt)(reveila_core_kref_com_reveila_crypto_PlatformCryptographer thiz, reveila_core_kref_kotlin_ByteArray encryptedData, reveila_core_kref_kotlin_ByteArray secretKey);
              reveila_core_kref_kotlin_ByteArray (*encrypt)(reveila_core_kref_com_reveila_crypto_PlatformCryptographer thiz, reveila_core_kref_kotlin_ByteArray data, reveila_core_kref_kotlin_ByteArray secretKey);
              reveila_core_kref_kotlin_ByteArray (*sha256)(reveila_core_kref_com_reveila_crypto_PlatformCryptographer thiz, reveila_core_kref_kotlin_ByteArray data);
            } PlatformCryptographer;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_crypto_PlatformCryptographerAdapter (*PlatformCryptographerAdapter)(reveila_core_kref_kotlin_ByteArray secretKey, reveila_core_kref_com_reveila_crypto_PlatformCryptographer platformCrypto);
              reveila_core_kref_com_reveila_crypto_PlatformCryptographer (*get_platformCrypto)(reveila_core_kref_com_reveila_crypto_PlatformCryptographerAdapter thiz);
              reveila_core_kref_kotlin_ByteArray (*get_secretKey)(reveila_core_kref_com_reveila_crypto_PlatformCryptographerAdapter thiz);
              reveila_core_kref_kotlin_ByteArray (*decrypt)(reveila_core_kref_com_reveila_crypto_PlatformCryptographerAdapter thiz, reveila_core_kref_kotlin_ByteArray data);
              reveila_core_kref_kotlin_ByteArray (*encrypt)(reveila_core_kref_com_reveila_crypto_PlatformCryptographerAdapter thiz, reveila_core_kref_kotlin_ByteArray data);
              reveila_core_kref_kotlin_ByteArray (*hash)(reveila_core_kref_com_reveila_crypto_PlatformCryptographerAdapter thiz, reveila_core_kref_kotlin_ByteArray data);
            } PlatformCryptographerAdapter;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_crypto_MingwPlatformCryptographer (*MingwPlatformCryptographer)();
              reveila_core_kref_kotlin_ByteArray (*base64Decode)(reveila_core_kref_com_reveila_crypto_MingwPlatformCryptographer thiz, const char* encoded);
              const char* (*base64Encode)(reveila_core_kref_com_reveila_crypto_MingwPlatformCryptographer thiz, reveila_core_kref_kotlin_ByteArray data);
              reveila_core_kref_kotlin_ByteArray (*decrypt)(reveila_core_kref_com_reveila_crypto_MingwPlatformCryptographer thiz, reveila_core_kref_kotlin_ByteArray encryptedData, reveila_core_kref_kotlin_ByteArray secretKey);
              reveila_core_kref_kotlin_ByteArray (*encrypt)(reveila_core_kref_com_reveila_crypto_MingwPlatformCryptographer thiz, reveila_core_kref_kotlin_ByteArray data, reveila_core_kref_kotlin_ByteArray secretKey);
              reveila_core_kref_kotlin_ByteArray (*sha256)(reveila_core_kref_com_reveila_crypto_MingwPlatformCryptographer thiz, reveila_core_kref_kotlin_ByteArray data);
            } MingwPlatformCryptographer;
            reveila_core_kref_com_reveila_crypto_PlatformCryptographer (*createPlatformCryptographer)();
          } crypto;
          struct {
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_data_Entity_Companion (*_instance)();
                const char* (*get_ATTRIBUTES)(reveila_core_kref_com_reveila_data_Entity_Companion thiz);
                const char* (*get_KEY)(reveila_core_kref_com_reveila_data_Entity_Companion thiz);
                const char* (*get_TYPE)(reveila_core_kref_com_reveila_data_Entity_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_data_Entity (*Entity)(const char* type, reveila_core_kref_kotlin_collections_Map key, reveila_core_kref_kotlin_collections_Map attributes);
              reveila_core_kref_kotlin_collections_Map (*get_attributes)(reveila_core_kref_com_reveila_data_Entity thiz);
              reveila_core_kref_kotlin_collections_Map (*get_key)(reveila_core_kref_com_reveila_data_Entity thiz);
              const char* (*get_type)(reveila_core_kref_com_reveila_data_Entity thiz);
            } Entity;
            struct {
              struct {
                struct {
                  reveila_core_kref_com_reveila_data_Filter_LogicalOp (*get)(); /* enum entry for AND. */
                } AND;
                struct {
                  reveila_core_kref_com_reveila_data_Filter_LogicalOp (*get)(); /* enum entry for OR. */
                } OR;
                reveila_core_KType* (*_type)(void);
              } LogicalOp;
              struct {
                struct {
                  reveila_core_kref_com_reveila_data_Filter_SearchOp (*get)(); /* enum entry for EQUAL. */
                } EQUAL;
                struct {
                  reveila_core_kref_com_reveila_data_Filter_SearchOp (*get)(); /* enum entry for LIKE. */
                } LIKE;
                struct {
                  reveila_core_kref_com_reveila_data_Filter_SearchOp (*get)(); /* enum entry for IN. */
                } IN;
                struct {
                  reveila_core_kref_com_reveila_data_Filter_SearchOp (*get)(); /* enum entry for GREATER_THAN. */
                } GREATER_THAN;
                struct {
                  reveila_core_kref_com_reveila_data_Filter_SearchOp (*get)(); /* enum entry for LESS_THAN. */
                } LESS_THAN;
                reveila_core_KType* (*_type)(void);
              } SearchOp;
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_data_Filter_Criterion_Companion (*_instance)();
                  reveila_core_kref_com_reveila_data_Filter_Criterion (*equal)(reveila_core_kref_com_reveila_data_Filter_Criterion_Companion thiz, reveila_core_kref_kotlin_Any value);
                  reveila_core_kref_com_reveila_data_Filter_Criterion (*like)(reveila_core_kref_com_reveila_data_Filter_Criterion_Companion thiz, const char* value);
                } Companion;
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_data_Filter_Criterion (*Criterion)(reveila_core_kref_kotlin_Any value, reveila_core_kref_com_reveila_data_Filter_SearchOp operator_);
                reveila_core_kref_com_reveila_data_Filter_SearchOp (*get_operator)(reveila_core_kref_com_reveila_data_Filter_Criterion thiz);
                reveila_core_kref_kotlin_Any (*get_value)(reveila_core_kref_com_reveila_data_Filter_Criterion thiz);
                reveila_core_kref_kotlin_Any (*component1)(reveila_core_kref_com_reveila_data_Filter_Criterion thiz);
                reveila_core_kref_com_reveila_data_Filter_SearchOp (*component2)(reveila_core_kref_com_reveila_data_Filter_Criterion thiz);
                reveila_core_kref_com_reveila_data_Filter_Criterion (*copy)(reveila_core_kref_com_reveila_data_Filter_Criterion thiz, reveila_core_kref_kotlin_Any value, reveila_core_kref_com_reveila_data_Filter_SearchOp operator_);
                reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_data_Filter_Criterion thiz, reveila_core_kref_kotlin_Any other);
                reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_data_Filter_Criterion thiz);
                reveila_core_kref_com_reveila_data_Filter_SearchOp (*operator_)(reveila_core_kref_com_reveila_data_Filter_Criterion thiz);
                const char* (*toString)(reveila_core_kref_com_reveila_data_Filter_Criterion thiz);
                reveila_core_kref_kotlin_Any (*value)(reveila_core_kref_com_reveila_data_Filter_Criterion thiz);
              } Criterion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_data_Filter (*Filter)();
              reveila_core_kref_com_reveila_data_Filter (*Filter_)(reveila_core_kref_kotlin_collections_Map conditions);
              reveila_core_kref_com_reveila_data_Filter (*Filter__)(reveila_core_kref_com_reveila_data_Filter_LogicalOp logicalOp);
              reveila_core_kref_kotlin_collections_MutableMap (*get_conditions)(reveila_core_kref_com_reveila_data_Filter thiz);
              reveila_core_kref_com_reveila_data_Filter_LogicalOp (*get_logicalOp)(reveila_core_kref_com_reveila_data_Filter thiz);
              void (*set_logicalOp)(reveila_core_kref_com_reveila_data_Filter thiz, reveila_core_kref_com_reveila_data_Filter_LogicalOp set);
              reveila_core_kref_com_reveila_data_Filter (*add)(reveila_core_kref_com_reveila_data_Filter thiz, const char* field, reveila_core_kref_kotlin_Any value, reveila_core_kref_com_reveila_data_Filter_SearchOp op);
            } Filter;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_data_JsonFileRepository (*JsonFileRepository)(const char* dataDir, const char* entityType);
              void (*commit)(reveila_core_kref_com_reveila_data_JsonFileRepository thiz);
              reveila_core_KLong (*count)(reveila_core_kref_com_reveila_data_JsonFileRepository thiz);
              void (*disposeById)(reveila_core_kref_com_reveila_data_JsonFileRepository thiz, reveila_core_kref_kotlin_collections_Map id);
              reveila_core_kref_kotlin_collections_List (*fetchAll)(reveila_core_kref_com_reveila_data_JsonFileRepository thiz);
              reveila_core_kref_com_reveila_data_Optional (*fetchById)(reveila_core_kref_com_reveila_data_JsonFileRepository thiz, reveila_core_kref_kotlin_collections_Map id);
              reveila_core_kref_com_reveila_data_Page (*fetchPage)(reveila_core_kref_com_reveila_data_JsonFileRepository thiz, reveila_core_kref_com_reveila_data_Filter filter, reveila_core_kref_com_reveila_data_Sort sort, reveila_core_kref_kotlin_collections_List fetches, reveila_core_KInt page, reveila_core_KInt size, reveila_core_KBoolean includeCount);
              const char* (*getType)(reveila_core_kref_com_reveila_data_JsonFileRepository thiz);
              reveila_core_KBoolean (*hasId)(reveila_core_kref_com_reveila_data_JsonFileRepository thiz, reveila_core_kref_kotlin_collections_Map id);
              reveila_core_kref_com_reveila_data_Entity (*store)(reveila_core_kref_com_reveila_data_JsonFileRepository thiz, reveila_core_kref_com_reveila_data_Entity entity);
              reveila_core_kref_kotlin_collections_List (*storeAll)(reveila_core_kref_com_reveila_data_JsonFileRepository thiz, reveila_core_kref_kotlin_collections_Collection entities);
            } JsonFileRepository;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_data_QueryRequest_Companion (*_instance)();
                reveila_core_kref_com_reveila_data_QueryRequest (*defaultPage)(reveila_core_kref_com_reveila_data_QueryRequest_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_data_QueryRequest (*QueryRequest)(reveila_core_kref_com_reveila_data_Filter filter, reveila_core_kref_com_reveila_data_Sort sort, reveila_core_kref_kotlin_collections_List fetches, reveila_core_KInt page, reveila_core_KInt size, reveila_core_KBoolean includeCount);
              reveila_core_kref_kotlin_collections_List (*get_fetches)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_kref_com_reveila_data_Filter (*get_filter)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_KBoolean (*get_includeCount)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_KInt (*get_page)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_KInt (*get_size)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_kref_com_reveila_data_Sort (*get_sort)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_kref_com_reveila_data_Filter (*component1)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_kref_com_reveila_data_Sort (*component2)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_kref_kotlin_collections_List (*component3)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_KInt (*component4)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_KInt (*component5)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_KBoolean (*component6)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_kref_com_reveila_data_QueryRequest (*copy)(reveila_core_kref_com_reveila_data_QueryRequest thiz, reveila_core_kref_com_reveila_data_Filter filter, reveila_core_kref_com_reveila_data_Sort sort, reveila_core_kref_kotlin_collections_List fetches, reveila_core_KInt page, reveila_core_KInt size, reveila_core_KBoolean includeCount);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_data_QueryRequest thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_kref_kotlin_collections_List (*fetches)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_kref_com_reveila_data_Filter (*filter)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_KBoolean (*includeCount)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_KInt (*page)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_KInt (*size)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              reveila_core_kref_com_reveila_data_Sort (*sort)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_data_QueryRequest thiz);
            } QueryRequest;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_data_SearchRequest (*SearchRequest)();
              reveila_core_kref_com_reveila_data_SearchRequest (*SearchRequest_)(const char* entityType, reveila_core_kref_com_reveila_data_Filter filter, reveila_core_kref_com_reveila_data_Sort sort, reveila_core_kref_kotlin_collections_List fetches, reveila_core_KInt page, reveila_core_KInt size, reveila_core_KBoolean isIncludeCount);
              const char* (*get_entityType)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              void (*set_entityType)(reveila_core_kref_com_reveila_data_SearchRequest thiz, const char* set);
              reveila_core_kref_kotlin_collections_List (*get_fetches)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              void (*set_fetches)(reveila_core_kref_com_reveila_data_SearchRequest thiz, reveila_core_kref_kotlin_collections_List set);
              reveila_core_kref_com_reveila_data_Filter (*get_filter)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              void (*set_filter)(reveila_core_kref_com_reveila_data_SearchRequest thiz, reveila_core_kref_com_reveila_data_Filter set);
              reveila_core_KBoolean (*get_isIncludeCount)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              void (*set_isIncludeCount)(reveila_core_kref_com_reveila_data_SearchRequest thiz, reveila_core_KBoolean set);
              reveila_core_KInt (*get_page)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              void (*set_page)(reveila_core_kref_com_reveila_data_SearchRequest thiz, reveila_core_KInt set);
              reveila_core_KInt (*get_size)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              void (*set_size)(reveila_core_kref_com_reveila_data_SearchRequest thiz, reveila_core_KInt set);
              reveila_core_kref_com_reveila_data_Sort (*get_sort)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              void (*set_sort)(reveila_core_kref_com_reveila_data_SearchRequest thiz, reveila_core_kref_com_reveila_data_Sort set);
              const char* (*entityType)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              reveila_core_kref_kotlin_collections_List (*fetches)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              reveila_core_kref_com_reveila_data_Filter (*filter)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              reveila_core_KBoolean (*includeCount)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              reveila_core_KInt (*page)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              reveila_core_KInt (*size)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
              reveila_core_kref_com_reveila_data_Sort (*sort)(reveila_core_kref_com_reveila_data_SearchRequest thiz);
            } SearchRequest;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_data_Sort_Companion (*_instance)();
                reveila_core_kref_com_reveila_data_Sort (*asc)(reveila_core_kref_com_reveila_data_Sort_Companion thiz, const char* field);
                reveila_core_kref_com_reveila_data_Sort (*desc)(reveila_core_kref_com_reveila_data_Sort_Companion thiz, const char* field);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_data_Sort (*Sort)(const char* field, reveila_core_KBoolean ascending);
              reveila_core_KBoolean (*get_ascending)(reveila_core_kref_com_reveila_data_Sort thiz);
              const char* (*get_field)(reveila_core_kref_com_reveila_data_Sort thiz);
              reveila_core_KBoolean (*ascending)(reveila_core_kref_com_reveila_data_Sort thiz);
              const char* (*component1)(reveila_core_kref_com_reveila_data_Sort thiz);
              reveila_core_KBoolean (*component2)(reveila_core_kref_com_reveila_data_Sort thiz);
              reveila_core_kref_com_reveila_data_Sort (*copy)(reveila_core_kref_com_reveila_data_Sort thiz, const char* field, reveila_core_KBoolean ascending);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_data_Sort thiz, reveila_core_kref_kotlin_Any other);
              const char* (*field)(reveila_core_kref_com_reveila_data_Sort thiz);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_data_Sort thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_data_Sort thiz);
            } Sort;
          } data;
          struct {
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_error_ConfigurationException (*ConfigurationException)(const char* message);
              reveila_core_kref_com_reveila_error_ConfigurationException (*ConfigurationException_)(const char* message, reveila_core_kref_kotlin_Throwable cause);
              reveila_core_kref_com_reveila_error_ConfigurationException (*ConfigurationException__)(const char* message, reveila_core_kref_kotlin_Throwable cause, const char* errorCode);
            } ConfigurationException;
            struct {
              reveila_core_KType* (*_type)(void);
              const char* (*get_errorCode)(reveila_core_kref_com_reveila_error_ErrorCode thiz);
            } ErrorCode;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_error_ErrorUtil (*_instance)();
              reveila_core_kref_kotlin_Throwable (*getRootCause)(reveila_core_kref_com_reveila_error_ErrorUtil thiz, reveila_core_kref_kotlin_Throwable thrown);
              const char* (*toString)(reveila_core_kref_com_reveila_error_ErrorUtil thiz, reveila_core_kref_kotlin_Throwable thrown);
            } ErrorUtil;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_error_ExceptionCollection (*ExceptionCollection)();
              reveila_core_kref_com_reveila_error_ExceptionCollection (*ExceptionCollection_)(const char* message);
              reveila_core_kref_com_reveila_error_ExceptionCollection (*ExceptionCollection__)(reveila_core_kref_kotlin_Throwable throwable);
              reveila_core_kref_com_reveila_error_ExceptionCollection (*ExceptionCollection___)(const char* message, reveila_core_kref_kotlin_Throwable throwable);
              const char* (*get_message)(reveila_core_kref_com_reveila_error_ExceptionCollection thiz);
              void (*addException)(reveila_core_kref_com_reveila_error_ExceptionCollection thiz, reveila_core_kref_kotlin_Throwable throwable);
              reveila_core_kref_kotlin_collections_List (*getExceptions)(reveila_core_kref_com_reveila_error_ExceptionCollection thiz);
              reveila_core_KBoolean (*isEmpty)(reveila_core_kref_com_reveila_error_ExceptionCollection thiz);
              void (*setExceptions)(reveila_core_kref_com_reveila_error_ExceptionCollection thiz, reveila_core_kref_kotlin_collections_List list);
              const char* (*toString)(reveila_core_kref_com_reveila_error_ExceptionCollection thiz);
            } ExceptionCollection;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_error_SecurityException (*SecurityException)(const char* message);
              reveila_core_kref_com_reveila_error_SecurityException (*SecurityException_)(const char* message, reveila_core_kref_kotlin_Throwable cause);
              reveila_core_kref_com_reveila_error_SecurityException (*SecurityException__)(const char* message, reveila_core_kref_kotlin_Throwable cause, const char* errorCode);
              const char* (*get_errorCode)(reveila_core_kref_com_reveila_error_SecurityException thiz);
            } SecurityException;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_error_SystemException (*SystemException)(const char* message);
              reveila_core_kref_com_reveila_error_SystemException (*SystemException_)(const char* message, reveila_core_kref_kotlin_Throwable cause);
              reveila_core_kref_com_reveila_error_SystemException (*SystemException__)(const char* message, reveila_core_kref_kotlin_Throwable cause, const char* errorCode);
              const char* (*get_errorCode)(reveila_core_kref_com_reveila_error_SystemException thiz);
            } SystemException;
          } error;
          struct {
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_event_AutoCallEvent_Companion (*_instance)();
                reveila_core_KInt (*get_COMPLETED)(reveila_core_kref_com_reveila_event_AutoCallEvent_Companion thiz);
                reveila_core_KInt (*get_FAILED)(reveila_core_kref_com_reveila_event_AutoCallEvent_Companion thiz);
                reveila_core_KInt (*get_STARTED)(reveila_core_kref_com_reveila_event_AutoCallEvent_Companion thiz);
                reveila_core_KInt (*get_UPDATE)(reveila_core_kref_com_reveila_event_AutoCallEvent_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_event_AutoCallEvent (*AutoCallEvent)(reveila_core_kref_kotlin_Any source, const char* proxyName, const char* methodName, reveila_core_KInt eventType, reveila_core_KLong timeStamp, reveila_core_kref_kotlin_Throwable error);
              reveila_core_kref_kotlin_Throwable (*get_error)(reveila_core_kref_com_reveila_event_AutoCallEvent thiz);
              reveila_core_KInt (*get_eventType)(reveila_core_kref_com_reveila_event_AutoCallEvent thiz);
              const char* (*get_methodName)(reveila_core_kref_com_reveila_event_AutoCallEvent thiz);
              const char* (*get_proxyName)(reveila_core_kref_com_reveila_event_AutoCallEvent thiz);
              reveila_core_KLong (*get_timeStamp)(reveila_core_kref_com_reveila_event_AutoCallEvent thiz);
            } AutoCallEvent;
            struct {
              reveila_core_KType* (*_type)(void);
              void (*notifyEvent)(reveila_core_kref_com_reveila_event_EventConsumer thiz, reveila_core_kref_com_reveila_event_EventObject evtObj);
            } EventConsumer;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_event_EventManager (*EventManager)();
              void (*addEventWatcher)(reveila_core_kref_com_reveila_event_EventManager thiz, reveila_core_kref_com_reveila_event_EventConsumer l);
              void (*clear)(reveila_core_kref_com_reveila_event_EventManager thiz);
              void (*dispatchEvent)(reveila_core_kref_com_reveila_event_EventManager thiz, reveila_core_kref_com_reveila_event_EventObject event);
              void (*removeEventConsumer)(reveila_core_kref_com_reveila_event_EventManager thiz, reveila_core_kref_com_reveila_event_EventConsumer c);
            } EventManager;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_event_EventObject (*EventObject)(reveila_core_kref_kotlin_Any source);
              reveila_core_kref_kotlin_Any (*get_source)(reveila_core_kref_com_reveila_event_EventObject thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_event_EventObject thiz);
            } EventObject;
          } event;
          struct {
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_persistence_SchemaInitializer_Companion (*_instance)();
                reveila_core_kref_kotlin_collections_List (*getSqlStatements)(reveila_core_kref_com_reveila_persistence_SchemaInitializer_Companion thiz, const char* sql, reveila_core_KBoolean isSqlite);
                const char* (*loadSchemaSql)(reveila_core_kref_com_reveila_persistence_SchemaInitializer_Companion thiz, const char* schemaPath);
                const char* (*transformSql)(reveila_core_kref_com_reveila_persistence_SchemaInitializer_Companion thiz, const char* rawSql, reveila_core_KBoolean isSqlite);
              } Companion;
              reveila_core_KType* (*_type)(void);
            } SchemaInitializer;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_persistence_VectorMatch (*VectorMatch)(const char* id, reveila_core_kref_kotlin_FloatArray vector, reveila_core_KDouble score, const char* payload);
              const char* (*get_id)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              const char* (*get_payload)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              reveila_core_KDouble (*get_score)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              reveila_core_kref_kotlin_FloatArray (*get_vector)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              const char* (*component1)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              reveila_core_kref_kotlin_FloatArray (*component2)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              reveila_core_KDouble (*component3)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              const char* (*component4)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              reveila_core_kref_com_reveila_persistence_VectorMatch (*copy)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz, const char* id, reveila_core_kref_kotlin_FloatArray vector, reveila_core_KDouble score, const char* payload);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              const char* (*id)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              const char* (*payload)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              reveila_core_KDouble (*score)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
              reveila_core_kref_kotlin_FloatArray (*vector)(reveila_core_kref_com_reveila_persistence_VectorMatch thiz);
            } VectorMatch;
            struct {
              reveila_core_KType* (*_type)(void);
              void (*insert)(reveila_core_kref_com_reveila_persistence_VectorStore thiz, const char* id, reveila_core_kref_kotlin_FloatArray vector, const char* payload);
              reveila_core_kref_kotlin_collections_List (*search)(reveila_core_kref_com_reveila_persistence_VectorStore thiz, reveila_core_kref_kotlin_FloatArray query, reveila_core_KInt limit);
            } VectorStore;
          } persistence;
          struct {
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime (*AbstractGuardedRuntime)();
              reveila_core_KBoolean (*get_isSuspended)(reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime thiz);
              void (*set_isSuspended)(reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime thiz, reveila_core_KBoolean set);
              reveila_core_kref_com_reveila_safety_InvocationResult (*execute)(reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime thiz, reveila_core_kref_com_reveila_system_Plugin plugin, reveila_core_kref_com_reveila_safety_SecurityPerimeter perimeter, reveila_core_kref_kotlin_collections_Map arguments, reveila_core_kref_kotlin_collections_Map jitCredentials);
              reveila_core_KBoolean (*isSuspended)(reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime thiz);
              reveila_core_kref_com_reveila_safety_InvocationResult (*onExecute)(reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime thiz, reveila_core_kref_com_reveila_system_Plugin plugin, reveila_core_kref_com_reveila_safety_SecurityPerimeter perimeter, reveila_core_kref_kotlin_collections_Map arguments, reveila_core_kref_kotlin_collections_Map jitCredentials);
              reveila_core_KBoolean (*resume)(reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime thiz, reveila_core_kref_kotlin_collections_Map jitCredentials);
              reveila_core_KBoolean (*suspend)(reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime thiz, reveila_core_kref_kotlin_collections_Map jitCredentials);
              void (*validateRequest)(reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime thiz, reveila_core_kref_com_reveila_system_Plugin plugin, reveila_core_kref_com_reveila_safety_SecurityPerimeter perimeter);
            } AbstractGuardedRuntime;
            struct {
              reveila_core_KType* (*_type)(void);
              void (*recordForensicMetadata)(reveila_core_kref_com_reveila_safety_FlightRecorder thiz, reveila_core_kref_com_reveila_system_Plugin plugin, reveila_core_kref_kotlin_collections_Map metadata);
              void (*recordReasoning)(reveila_core_kref_com_reveila_safety_FlightRecorder thiz, reveila_core_kref_com_reveila_system_Plugin plugin, const char* reasoning);
              void (*recordStep)(reveila_core_kref_com_reveila_safety_FlightRecorder thiz, reveila_core_kref_com_reveila_system_Plugin plugin, const char* stepName, reveila_core_kref_kotlin_collections_Map data);
              void (*recordToolOutput)(reveila_core_kref_com_reveila_safety_FlightRecorder thiz, reveila_core_kref_com_reveila_system_Plugin plugin, const char* toolName, reveila_core_kref_kotlin_Any output);
            } FlightRecorder;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_InvocationResult (*execute)(reveila_core_kref_com_reveila_safety_GuardedRuntime thiz, reveila_core_kref_com_reveila_system_Plugin plugin, reveila_core_kref_com_reveila_safety_SecurityPerimeter perimeter, reveila_core_kref_kotlin_collections_Map arguments, reveila_core_kref_kotlin_collections_Map jitCredentials);
              reveila_core_KBoolean (*resume)(reveila_core_kref_com_reveila_safety_GuardedRuntime thiz, reveila_core_kref_kotlin_collections_Map jitCredentials);
              reveila_core_KBoolean (*suspend)(reveila_core_kref_com_reveila_safety_GuardedRuntime thiz, reveila_core_kref_kotlin_collections_Map jitCredentials);
            } GuardedRuntime;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_safety_GuardrailResponse_Companion (*_instance)();
                reveila_core_kref_com_reveila_safety_GuardrailResponse (*failSafe)(reveila_core_kref_com_reveila_safety_GuardrailResponse_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_GuardrailResponse (*GuardrailResponse)(reveila_core_KBoolean approved, const char* reasoning, const char* status);
              reveila_core_KBoolean (*get_approved)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
              const char* (*get_reasoning)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
              const char* (*get_status)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
              reveila_core_KBoolean (*approved)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
              reveila_core_KBoolean (*component1)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
              const char* (*component2)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
              const char* (*component3)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
              reveila_core_kref_com_reveila_safety_GuardrailResponse (*copy)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz, reveila_core_KBoolean approved, const char* reasoning, const char* status);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
              const char* (*reasoning)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
              const char* (*status)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_safety_GuardrailResponse thiz);
            } GuardrailResponse;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_KBoolean (*performSafetyAudit)(reveila_core_kref_com_reveila_safety_IntentValidator thiz, const char* pluginId, const char* maskedArgs, const char* systemContext);
              void (*validateIntent)(reveila_core_kref_com_reveila_safety_IntentValidator thiz, const char* intent);
            } IntentValidator;
            struct {
              struct {
                struct {
                  reveila_core_kref_com_reveila_safety_InvocationResult_Status (*get)(); /* enum entry for SUCCESS. */
                } SUCCESS;
                struct {
                  reveila_core_kref_com_reveila_safety_InvocationResult_Status (*get)(); /* enum entry for ERROR. */
                } ERROR;
                struct {
                  reveila_core_kref_com_reveila_safety_InvocationResult_Status (*get)(); /* enum entry for PENDING_APPROVAL. */
                } PENDING_APPROVAL;
                struct {
                  reveila_core_kref_com_reveila_safety_InvocationResult_Status (*get)(); /* enum entry for SECURITY_BREACH. */
                } SECURITY_BREACH;
                reveila_core_KType* (*_type)(void);
              } Status;
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_safety_InvocationResult_Companion (*_instance)();
                reveila_core_kref_com_reveila_safety_InvocationResult (*error)(reveila_core_kref_com_reveila_safety_InvocationResult_Companion thiz, const char* message);
                reveila_core_kref_com_reveila_safety_InvocationResult (*pendingApproval)(reveila_core_kref_com_reveila_safety_InvocationResult_Companion thiz, const char* intent, const char* traceId, reveila_core_kref_kotlin_Any approvalData);
                reveila_core_kref_com_reveila_safety_InvocationResult (*securityBreach)(reveila_core_kref_com_reveila_safety_InvocationResult_Companion thiz, const char* message);
                reveila_core_kref_com_reveila_safety_InvocationResult (*success)(reveila_core_kref_com_reveila_safety_InvocationResult_Companion thiz, reveila_core_kref_kotlin_Any data);
                reveila_core_kref_com_reveila_safety_InvocationResult (*success_)(reveila_core_kref_com_reveila_safety_InvocationResult_Companion thiz, reveila_core_kref_kotlin_Any data, const char* message);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_InvocationResult (*InvocationResult)(reveila_core_kref_com_reveila_safety_InvocationResult_Status status, reveila_core_kref_kotlin_Any data, const char* message, const char* callbackUrl);
              const char* (*get_callbackUrl)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              reveila_core_kref_kotlin_Any (*get_data)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              const char* (*get_message)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              reveila_core_kref_com_reveila_safety_InvocationResult_Status (*get_status)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              const char* (*callbackUrl)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              reveila_core_kref_com_reveila_safety_InvocationResult_Status (*component1)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              reveila_core_kref_kotlin_Any (*component2)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              const char* (*component3)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              const char* (*component4)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              reveila_core_kref_com_reveila_safety_InvocationResult (*copy)(reveila_core_kref_com_reveila_safety_InvocationResult thiz, reveila_core_kref_com_reveila_safety_InvocationResult_Status status, reveila_core_kref_kotlin_Any data, const char* message, const char* callbackUrl);
              reveila_core_kref_kotlin_Any (*data)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_safety_InvocationResult thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              const char* (*message)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              reveila_core_kref_com_reveila_safety_InvocationResult_Status (*status)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_safety_InvocationResult thiz);
            } InvocationResult;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_JsonSchemaEnforcer (*JsonSchemaEnforcer)();
              reveila_core_kref_kotlin_collections_Map (*enforce)(reveila_core_kref_com_reveila_safety_JsonSchemaEnforcer thiz, const char* pluginId, reveila_core_kref_kotlin_collections_Map rawArguments);
              void (*onStart)(reveila_core_kref_com_reveila_safety_JsonSchemaEnforcer thiz);
              void (*onStop)(reveila_core_kref_com_reveila_safety_JsonSchemaEnforcer thiz);
            } JsonSchemaEnforcer;
            struct {
              reveila_core_KType* (*_type)(void);
              void (*emergencyStopAll)(reveila_core_kref_com_reveila_safety_KillSwitch thiz);
              reveila_core_kref_com_reveila_safety_SafetyStatus (*getStatus)(reveila_core_kref_com_reveila_safety_KillSwitch thiz, const char* agentId);
              reveila_core_KBoolean (*isAuthorized)(reveila_core_kref_com_reveila_safety_KillSwitch thiz, const char* agentId);
            } KillSwitch;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_ManagedInvocation (*ManagedInvocation)();
              reveila_core_kref_kotlin_Any (*handleCallback)(reveila_core_kref_com_reveila_safety_ManagedInvocation thiz, const char* jitToken, const char* component, const char* method, reveila_core_kref_kotlin_collections_Map arguments);
              reveila_core_kref_com_reveila_safety_InvocationResult (*invoke)(reveila_core_kref_com_reveila_safety_ManagedInvocation thiz, reveila_core_kref_com_reveila_ai_ToolCall toolCall, reveila_core_kref_com_reveila_safety_SecurityPerimeter perimeter, const char* intent, reveila_core_kref_kotlin_collections_Map metaInfo);
              void (*onStart)(reveila_core_kref_com_reveila_safety_ManagedInvocation thiz);
              void (*onStop)(reveila_core_kref_com_reveila_safety_ManagedInvocation thiz);
            } ManagedInvocation;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest (*PluginManifest)(const char* plugin_id, const char* manifestName, const char* manifestVersion, reveila_core_kref_kotlin_collections_Map tool_definitions, const char* tier, reveila_core_kref_com_reveila_safety_SecurityPerimeter agency_perimeter, reveila_core_kref_kotlin_collections_Set secret_parameters, reveila_core_kref_kotlin_collections_Set masked_parameters);
                reveila_core_kref_com_reveila_safety_SecurityPerimeter (*get_agency_perimeter)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*get_manifestName)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*get_manifestVersion)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Set (*get_masked_parameters)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*get_plugin_id)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Set (*get_secret_parameters)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*get_tier)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Map (*get_tool_definitions)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_com_reveila_safety_SecurityPerimeter (*agency_perimeter)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*component1)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*component2)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*component3)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Map (*component4)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*component5)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_com_reveila_safety_SecurityPerimeter (*component6)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Set (*component7)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Set (*component8)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest (*copy)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz, const char* plugin_id, const char* manifestName, const char* manifestVersion, reveila_core_kref_kotlin_collections_Map tool_definitions, const char* tier, reveila_core_kref_com_reveila_safety_SecurityPerimeter agency_perimeter, reveila_core_kref_kotlin_collections_Set secret_parameters, reveila_core_kref_kotlin_collections_Set masked_parameters);
                reveila_core_kref_com_reveila_safety_SecurityPerimeter (*defaultPerimeter)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz, reveila_core_kref_kotlin_Any other);
                reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Set (*hitlRequiredIntents)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*id)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Set (*maskedParameters)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Set (*masked_parameters)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*name)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*plugin_id)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Set (*secretParameters)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Set (*secret_parameters)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*tier)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*toString)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Map (*toolDefinitions)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                reveila_core_kref_kotlin_collections_Map (*tool_definitions)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
                const char* (*version)(reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest thiz);
              } PluginManifest;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_MetadataRegistry (*MetadataRegistry)();
              reveila_core_kref_kotlin_collections_Map (*exportToMCP)(reveila_core_kref_com_reveila_safety_MetadataRegistry thiz);
              reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest (*getManifest)(reveila_core_kref_com_reveila_safety_MetadataRegistry thiz, const char* pluginId);
              void (*onStart)(reveila_core_kref_com_reveila_safety_MetadataRegistry thiz);
              void (*onStop)(reveila_core_kref_com_reveila_safety_MetadataRegistry thiz);
              void (*register_)(reveila_core_kref_com_reveila_safety_MetadataRegistry thiz, reveila_core_kref_com_reveila_safety_MetadataRegistry_PluginManifest manifest);
            } MetadataRegistry;
            struct {
              struct {
                struct {
                  reveila_core_kref_com_reveila_safety_PolicyEnforcement_AuthorizationStatus (*get)(); /* enum entry for AUTHORIZED. */
                } AUTHORIZED;
                struct {
                  reveila_core_kref_com_reveila_safety_PolicyEnforcement_AuthorizationStatus (*get)(); /* enum entry for DENIED. */
                } DENIED;
                struct {
                  reveila_core_kref_com_reveila_safety_PolicyEnforcement_AuthorizationStatus (*get)(); /* enum entry for HUMAN_APPROVAL_REQUIRED. */
                } HUMAN_APPROVAL_REQUIRED;
                reveila_core_KType* (*_type)(void);
              } AuthorizationStatus;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_PolicyEnforcement_AuthorizationStatus (*authorize)(reveila_core_kref_com_reveila_safety_PolicyEnforcement thiz, reveila_core_kref_com_reveila_system_Plugin plugin, reveila_core_kref_com_reveila_safety_SecurityPerimeter perimeter, const char* toolName, reveila_core_kref_kotlin_collections_Map arguments);
              reveila_core_kref_kotlin_collections_Map (*getJitCredentials)(reveila_core_kref_com_reveila_safety_PolicyEnforcement thiz, reveila_core_kref_com_reveila_system_Plugin plugin, const char* scope);
            } PolicyEnforcement;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_ReveilaIntent (*ReveilaIntent)(const char* intent, reveila_core_kref_kotlin_collections_Map arguments, const char* sourceTool);
              reveila_core_kref_kotlin_collections_Map (*get_arguments)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
              const char* (*get_intent)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
              const char* (*get_sourceTool)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
              reveila_core_kref_kotlin_collections_Map (*arguments)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
              const char* (*component1)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
              reveila_core_kref_kotlin_collections_Map (*component2)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
              const char* (*component3)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
              reveila_core_kref_com_reveila_safety_ReveilaIntent (*copy)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz, const char* intent, reveila_core_kref_kotlin_collections_Map arguments, const char* sourceTool);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
              const char* (*intent)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
              const char* (*sourceTool)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_safety_ReveilaIntent thiz);
            } ReveilaIntent;
            struct {
              struct {
                reveila_core_kref_com_reveila_safety_SafetyAction (*get)(); /* enum entry for HALT. */
              } HALT;
              struct {
                reveila_core_kref_com_reveila_safety_SafetyAction (*get)(); /* enum entry for ISOLATE. */
              } ISOLATE;
              struct {
                reveila_core_kref_com_reveila_safety_SafetyAction (*get)(); /* enum entry for KILL. */
              } KILL;
              reveila_core_KType* (*_type)(void);
            } SafetyAction;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_SafetyCommand (*SafetyCommand)(const char* agentId, reveila_core_kref_com_reveila_safety_SafetyAction action, reveila_core_kref_kotlin_ByteArray biometricSignature, reveila_core_KLong timestamp);
              reveila_core_kref_com_reveila_safety_SafetyAction (*get_action)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              const char* (*get_agentId)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              reveila_core_kref_kotlin_ByteArray (*get_biometricSignature)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              reveila_core_KLong (*get_timestamp)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              reveila_core_kref_com_reveila_safety_SafetyAction (*action)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              const char* (*agentId)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              reveila_core_kref_kotlin_ByteArray (*biometricSignature)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              const char* (*component1)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              reveila_core_kref_com_reveila_safety_SafetyAction (*component2)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              reveila_core_kref_kotlin_ByteArray (*component3)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              reveila_core_KLong (*component4)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              reveila_core_kref_com_reveila_safety_SafetyCommand (*copy)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz, const char* agentId, reveila_core_kref_com_reveila_safety_SafetyAction action, reveila_core_kref_kotlin_ByteArray biometricSignature, reveila_core_KLong timestamp);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              reveila_core_KLong (*timestamp)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_safety_SafetyCommand thiz);
            } SafetyCommand;
            struct {
              reveila_core_KType* (*_type)(void);
              void (*onSafetyCommand)(reveila_core_kref_com_reveila_safety_SafetyCommandListener thiz, reveila_core_kref_com_reveila_safety_SafetyCommand command);
            } SafetyCommandListener;
            struct {
              struct {
                reveila_core_kref_com_reveila_safety_SafetyStatus (*get)(); /* enum entry for ACTIVE. */
              } ACTIVE;
              struct {
                reveila_core_kref_com_reveila_safety_SafetyStatus (*get)(); /* enum entry for KILLED. */
              } KILLED;
              struct {
                reveila_core_kref_com_reveila_safety_SafetyStatus (*get)(); /* enum entry for PENDING_AUTHORIZATION. */
              } PENDING_AUTHORIZATION;
              reveila_core_KType* (*_type)(void);
            } SafetyStatus;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_kotlin_collections_Map (*enforce)(reveila_core_kref_com_reveila_safety_SchemaEnforcer thiz, const char* pluginId, reveila_core_kref_kotlin_collections_Map rawArguments);
            } SchemaEnforcer;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_safety_SecretManager_Companion (*_instance)();
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_SecretManager (*SecretManager)();
              reveila_core_kref_kotlin_collections_Map (*generateJitToken)(reveila_core_kref_com_reveila_safety_SecretManager thiz, reveila_core_kref_com_reveila_system_Plugin plugin, const char* scope);
              const char* (*getSecret)(reveila_core_kref_com_reveila_safety_SecretManager thiz, const char* secretKey);
              void (*onStart)(reveila_core_kref_com_reveila_safety_SecretManager thiz);
              void (*onStop)(reveila_core_kref_com_reveila_safety_SecretManager thiz);
              void (*storeSecret)(reveila_core_kref_com_reveila_safety_SecretManager thiz, const char* key, const char* value);
              reveila_core_KBoolean (*validateToken)(reveila_core_kref_com_reveila_safety_SecretManager thiz, const char* token);
            } SecretManager;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_safety_SecurityPerimeter (*SecurityPerimeter)(reveila_core_kref_kotlin_collections_Set accessScopes, reveila_core_kref_kotlin_collections_Set allowedDomains, reveila_core_KBoolean internetAccessBlocked, reveila_core_KLong maxMemoryMb, reveila_core_KInt maxCpuCores, reveila_core_KInt maxExecutionSec, reveila_core_KBoolean delegationAllowed);
              reveila_core_kref_kotlin_collections_Set (*get_accessScopes)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_kref_kotlin_collections_Set (*get_allowedDomains)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KBoolean (*get_delegationAllowed)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KBoolean (*get_internetAccessBlocked)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KInt (*get_maxCpuCores)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KInt (*get_maxExecutionSec)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KLong (*get_maxMemoryMb)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_kref_kotlin_collections_Set (*accessScopes)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_kref_kotlin_collections_Set (*allowedDomains)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_kref_kotlin_collections_Set (*component1)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_kref_kotlin_collections_Set (*component2)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KBoolean (*component3)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KLong (*component4)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KInt (*component5)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KInt (*component6)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KBoolean (*component7)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_kref_com_reveila_safety_SecurityPerimeter (*copy)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz, reveila_core_kref_kotlin_collections_Set accessScopes, reveila_core_kref_kotlin_collections_Set allowedDomains, reveila_core_KBoolean internetAccessBlocked, reveila_core_KLong maxMemoryMb, reveila_core_KInt maxCpuCores, reveila_core_KInt maxExecutionSec, reveila_core_KBoolean delegationAllowed);
              reveila_core_KBoolean (*delegationAllowed)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KBoolean (*internetAccessBlocked)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_kref_com_reveila_safety_SecurityPerimeter (*intersect)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz, reveila_core_kref_com_reveila_safety_SecurityPerimeter other);
              reveila_core_KBoolean (*isScopeAllowed)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz, const char* scope);
              reveila_core_KInt (*maxCpuCores)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KInt (*maxExecutionSec)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              reveila_core_KLong (*maxMemoryMb)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_safety_SecurityPerimeter thiz);
            } SecurityPerimeter;
          } safety;
          struct {
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_service_DataService (*DataService)();
              void (*delete_)(reveila_core_kref_com_reveila_service_DataService thiz, const char* entityType, reveila_core_kref_kotlin_collections_Map key);
              reveila_core_kref_com_reveila_data_Entity (*findById)(reveila_core_kref_com_reveila_service_DataService thiz, const char* entityType, reveila_core_kref_kotlin_collections_Map key);
              reveila_core_kref_com_reveila_data_Repository (*getRepository)(reveila_core_kref_com_reveila_service_DataService thiz, const char* entityType);
              void (*onStart)(reveila_core_kref_com_reveila_service_DataService thiz);
              void (*onStop)(reveila_core_kref_com_reveila_service_DataService thiz);
              reveila_core_kref_com_reveila_data_Entity (*save)(reveila_core_kref_com_reveila_service_DataService thiz, const char* entityType, reveila_core_kref_kotlin_collections_Map entityMap);
              reveila_core_kref_com_reveila_data_Page (*search)(reveila_core_kref_com_reveila_service_DataService thiz, reveila_core_kref_kotlin_collections_Map requestMap);
            } DataService;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_service_EchoService (*EchoService)();
              reveila_core_KBoolean (*get_isReverse)(reveila_core_kref_com_reveila_service_EchoService thiz);
              void (*set_isReverse)(reveila_core_kref_com_reveila_service_EchoService thiz, reveila_core_KBoolean set);
              reveila_core_KInt (*get_repeat)(reveila_core_kref_com_reveila_service_EchoService thiz);
              void (*set_repeat)(reveila_core_kref_com_reveila_service_EchoService thiz, reveila_core_KInt set);
              const char* (*echo)(reveila_core_kref_com_reveila_service_EchoService thiz, const char* name);
              void (*notifyEvent)(reveila_core_kref_com_reveila_service_EchoService thiz, reveila_core_kref_com_reveila_event_EventObject evtObj);
              void (*onStart)(reveila_core_kref_com_reveila_service_EchoService thiz);
              void (*onStop)(reveila_core_kref_com_reveila_service_EchoService thiz);
            } EchoService;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_service_HttpClientService_Companion (*_instance)();
                const char* (*get_JSON)(reveila_core_kref_com_reveila_service_HttpClientService_Companion thiz);
                const char* (*get_SOAP)(reveila_core_kref_com_reveila_service_HttpClientService_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_service_HttpClientService (*HttpClientService)();
              reveila_core_KLong (*get_connectTimeout)(reveila_core_kref_com_reveila_service_HttpClientService thiz);
              void (*set_connectTimeout)(reveila_core_kref_com_reveila_service_HttpClientService thiz, reveila_core_KLong set);
              reveila_core_KLong (*get_readTimeout)(reveila_core_kref_com_reveila_service_HttpClientService thiz);
              void (*set_readTimeout)(reveila_core_kref_com_reveila_service_HttpClientService thiz, reveila_core_KLong set);
              reveila_core_KLong (*get_writeTimeout)(reveila_core_kref_com_reveila_service_HttpClientService thiz);
              void (*set_writeTimeout)(reveila_core_kref_com_reveila_service_HttpClientService thiz, reveila_core_KLong set);
              const char* (*invokeRest)(reveila_core_kref_com_reveila_service_HttpClientService thiz, const char* url, const char* method, const char* payload, const char* payloadFormat, reveila_core_kref_kotlin_collections_Map headers);
              const char* (*invokeRest_)(reveila_core_kref_com_reveila_service_HttpClientService thiz, reveila_core_kref_kotlin_Array args);
              const char* (*invokeSoap)(reveila_core_kref_com_reveila_service_HttpClientService thiz, const char* url, const char* soapAction, const char* soapEnvelope);
              void (*onStart)(reveila_core_kref_com_reveila_service_HttpClientService thiz);
              void (*onStop)(reveila_core_kref_com_reveila_service_HttpClientService thiz);
            } HttpClientService;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_service_PlatformHttpResponse (*PlatformHttpResponse)(reveila_core_KInt statusCode, const char* body, reveila_core_kref_kotlin_collections_Map headers);
              const char* (*get_body)(reveila_core_kref_com_reveila_service_PlatformHttpResponse thiz);
              reveila_core_kref_kotlin_collections_Map (*get_headers)(reveila_core_kref_com_reveila_service_PlatformHttpResponse thiz);
              reveila_core_KInt (*get_statusCode)(reveila_core_kref_com_reveila_service_PlatformHttpResponse thiz);
              reveila_core_KInt (*component1)(reveila_core_kref_com_reveila_service_PlatformHttpResponse thiz);
              const char* (*component2)(reveila_core_kref_com_reveila_service_PlatformHttpResponse thiz);
              reveila_core_kref_kotlin_collections_Map (*component3)(reveila_core_kref_com_reveila_service_PlatformHttpResponse thiz);
              reveila_core_kref_com_reveila_service_PlatformHttpResponse (*copy)(reveila_core_kref_com_reveila_service_PlatformHttpResponse thiz, reveila_core_KInt statusCode, const char* body, reveila_core_kref_kotlin_collections_Map headers);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_service_PlatformHttpResponse thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_service_PlatformHttpResponse thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_service_PlatformHttpResponse thiz);
            } PlatformHttpResponse;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_service_PlatformHttpEngine_Companion (*_instance)();
                reveila_core_kref_com_reveila_service_PlatformHttpEngine (*invoke)(reveila_core_kref_com_reveila_service_PlatformHttpEngine_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_service_PlatformHttpResponse (*execute)(reveila_core_kref_com_reveila_service_PlatformHttpEngine thiz, const char* url, const char* method, const char* payload, const char* contentType, reveila_core_kref_kotlin_collections_Map headers, reveila_core_KLong timeoutSeconds);
            } PlatformHttpEngine;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_service_RatedService (*RatedService)(const char* name);
              const char* (*getBestProvider)(reveila_core_kref_com_reveila_service_RatedService thiz);
              const char* (*getWorstProvider)(reveila_core_kref_com_reveila_service_RatedService thiz);
              reveila_core_kref_kotlin_Any (*invoke)(reveila_core_kref_com_reveila_service_RatedService thiz, const char* methodName, reveila_core_kref_kotlin_Array args);
              void (*onStart)(reveila_core_kref_com_reveila_service_RatedService thiz);
              void (*onStop)(reveila_core_kref_com_reveila_service_RatedService thiz);
              void (*rateProvider)(reveila_core_kref_com_reveila_service_RatedService thiz, const char* provider, reveila_core_kref_kotlin_Long points);
              void (*setInitialPoints)(reveila_core_kref_com_reveila_service_RatedService thiz, reveila_core_kref_kotlin_collections_List providerNameAndPoints);
            } RatedService;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_service_RemoteService (*RemoteService)();
              void (*addRemoteNode)(reveila_core_kref_com_reveila_service_RemoteService thiz, const char* urlAndPriority);
              reveila_core_kref_kotlin_Any (*invoke)(reveila_core_kref_com_reveila_service_RemoteService thiz, const char* componentName, const char* methodName);
              reveila_core_kref_kotlin_Any (*invoke_)(reveila_core_kref_com_reveila_service_RemoteService thiz, const char* componentName, const char* methodName, reveila_core_kref_kotlin_Any arg1);
              reveila_core_kref_kotlin_Any (*invoke__)(reveila_core_kref_com_reveila_service_RemoteService thiz, const char* componentName, const char* methodName, reveila_core_kref_kotlin_Any arg1, reveila_core_kref_kotlin_Any arg2);
              reveila_core_kref_kotlin_Any (*invoke___)(reveila_core_kref_com_reveila_service_RemoteService thiz, const char* componentName, const char* methodName, reveila_core_kref_kotlin_Any arg1, reveila_core_kref_kotlin_Any arg2, reveila_core_kref_kotlin_Any arg3);
              reveila_core_kref_kotlin_Any (*invoke____)(reveila_core_kref_com_reveila_service_RemoteService thiz, reveila_core_kref_kotlin_Array remoteCallArgs);
              void (*onStart)(reveila_core_kref_com_reveila_service_RemoteService thiz);
              void (*onStop)(reveila_core_kref_com_reveila_service_RemoteService thiz);
              void (*setRemoteURLs)(reveila_core_kref_com_reveila_service_RemoteService thiz, reveila_core_kref_kotlin_Array baseUrls);
            } RemoteService;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_service_MingwPlatformHttpEngine (*MingwPlatformHttpEngine)();
              reveila_core_kref_com_reveila_service_PlatformHttpResponse (*execute)(reveila_core_kref_com_reveila_service_MingwPlatformHttpEngine thiz, const char* url, const char* method, const char* payload, const char* contentType, reveila_core_kref_kotlin_collections_Map headers, reveila_core_KLong timeoutSeconds);
            } MingwPlatformHttpEngine;
            reveila_core_kref_com_reveila_service_PlatformHttpEngine (*createPlatformHttpEngine)();
          } service;
          struct {
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_AbstractComponent (*AbstractComponent)();
              reveila_core_KBoolean (*get_isDebug)(reveila_core_kref_com_reveila_system_AbstractComponent thiz);
              void (*set_isDebug)(reveila_core_kref_com_reveila_system_AbstractComponent thiz, reveila_core_KBoolean set);
              reveila_core_KBoolean (*get_isManaged)(reveila_core_kref_com_reveila_system_AbstractComponent thiz);
              void (*set_isManaged)(reveila_core_kref_com_reveila_system_AbstractComponent thiz, reveila_core_KBoolean set);
              reveila_core_kref_com_reveila_system_logging_PlatformLogger (*get_logger)(reveila_core_kref_com_reveila_system_AbstractComponent thiz);
              reveila_core_KLong (*get_startupLatencyMs)(reveila_core_kref_com_reveila_system_AbstractComponent thiz);
              reveila_core_kref_com_reveila_system_ComponentState (*get_state)(reveila_core_kref_com_reveila_system_AbstractComponent thiz);
              reveila_core_KBoolean (*isRunning)(reveila_core_kref_com_reveila_system_AbstractComponent thiz);
              void (*onStart)(reveila_core_kref_com_reveila_system_AbstractComponent thiz);
              void (*onStop)(reveila_core_kref_com_reveila_system_AbstractComponent thiz);
              void (*start)(reveila_core_kref_com_reveila_system_AbstractComponent thiz);
              void (*stop)(reveila_core_kref_com_reveila_system_AbstractComponent thiz);
            } AbstractComponent;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_KBoolean (*cancel)(reveila_core_kref_com_reveila_system_concurrency_PlatformCancellable thiz, reveila_core_KBoolean mayInterruptIfRunning);
                reveila_core_KBoolean (*isCancelled)(reveila_core_kref_com_reveila_system_concurrency_PlatformCancellable thiz);
              } PlatformCancellable;
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_system_concurrency_PlatformScheduler_Companion (*_instance)();
                  reveila_core_kref_com_reveila_system_concurrency_PlatformScheduler (*invoke)(reveila_core_kref_com_reveila_system_concurrency_PlatformScheduler_Companion thiz, reveila_core_KInt poolSize);
                } Companion;
                reveila_core_KType* (*_type)(void);
                void (*execute)(reveila_core_kref_com_reveila_system_concurrency_PlatformScheduler thiz, reveila_core_kref_kotlin_Function0 task);
                reveila_core_kref_com_reveila_system_concurrency_PlatformCancellable (*scheduleWithFixedDelay)(reveila_core_kref_com_reveila_system_concurrency_PlatformScheduler thiz, reveila_core_KLong initialDelaySeconds, reveila_core_KLong intervalSeconds, reveila_core_kref_kotlin_Function0 task);
                void (*shutdown)(reveila_core_kref_com_reveila_system_concurrency_PlatformScheduler thiz);
              } PlatformScheduler;
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_concurrency_MingwPlatformScheduler (*MingwPlatformScheduler)(reveila_core_KInt poolSize);
                void (*execute)(reveila_core_kref_com_reveila_system_concurrency_MingwPlatformScheduler thiz, reveila_core_kref_kotlin_Function0 task);
                reveila_core_kref_com_reveila_system_concurrency_PlatformCancellable (*scheduleWithFixedDelay)(reveila_core_kref_com_reveila_system_concurrency_MingwPlatformScheduler thiz, reveila_core_KLong initialDelaySeconds, reveila_core_KLong intervalSeconds, reveila_core_kref_kotlin_Function0 task);
                void (*shutdown)(reveila_core_kref_com_reveila_system_concurrency_MingwPlatformScheduler thiz);
              } MingwPlatformScheduler;
              reveila_core_kref_com_reveila_system_concurrency_PlatformScheduler (*createPlatformScheduler)(reveila_core_KInt poolSize);
            } concurrency;
            struct {
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_system_io_PlatformFileSystem_Companion (*_instance)();
                  reveila_core_kref_com_reveila_system_io_PlatformFileSystem (*invoke)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem_Companion thiz);
                } Companion;
                reveila_core_KType* (*_type)(void);
                void (*createDirectories)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* path);
                reveila_core_KBoolean (*delete_)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* path, reveila_core_KBoolean recursive);
                reveila_core_KBoolean (*exists)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* path);
                const char* (*getDefaultSystemHome)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz);
                reveila_core_KBoolean (*isDirectory)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* path);
                reveila_core_kref_kotlin_collections_List (*listRelativePaths)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* directory, const char* extension);
                const char* (*normalize)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* path);
                reveila_core_kref_kotlin_ByteArray (*readBytes)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* path);
                const char* (*readText)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* path, const char* charset);
                const char* (*resolve)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* base, const char* relative);
                const char* (*toSafePath)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* base, const char* userPath);
                void (*writeBytes)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* path, reveila_core_kref_kotlin_ByteArray bytes, reveila_core_KBoolean append);
                void (*writeText)(reveila_core_kref_com_reveila_system_io_PlatformFileSystem thiz, const char* path, const char* text, reveila_core_KBoolean append);
              } PlatformFileSystem;
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem (*MingwPlatformFileSystem)();
                void (*createDirectories)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* path);
                reveila_core_KBoolean (*delete_)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* path, reveila_core_KBoolean recursive);
                reveila_core_KBoolean (*exists)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* path);
                const char* (*getDefaultSystemHome)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz);
                reveila_core_KBoolean (*isDirectory)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* path);
                reveila_core_kref_kotlin_collections_List (*listRelativePaths)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* directory, const char* extension);
                const char* (*normalize)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* path);
                reveila_core_kref_kotlin_ByteArray (*readBytes)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* path);
                const char* (*readText)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* path, const char* charset);
                const char* (*resolve)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* base, const char* relative);
                const char* (*toSafePath)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* base, const char* userPath);
                void (*writeBytes)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* path, reveila_core_kref_kotlin_ByteArray bytes, reveila_core_KBoolean append);
                void (*writeText)(reveila_core_kref_com_reveila_system_io_MingwPlatformFileSystem thiz, const char* path, const char* text, reveila_core_KBoolean append);
              } MingwPlatformFileSystem;
              reveila_core_kref_com_reveila_system_io_PlatformFileSystem (*createPlatformFileSystem)();
            } io;
            struct {
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_system_logging_PlatformLogger_Companion (*_instance)();
                  reveila_core_kref_com_reveila_system_logging_PlatformLogger (*invoke)(reveila_core_kref_com_reveila_system_logging_PlatformLogger_Companion thiz, const char* name);
                } Companion;
                reveila_core_KType* (*_type)(void);
                void (*debug)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, reveila_core_kref_kotlin_Function0 message);
                void (*debug_)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, const char* message);
                void (*info)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, reveila_core_kref_kotlin_Function0 message);
                void (*info_)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, const char* message);
                void (*severe)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, reveila_core_kref_kotlin_Function0 message, reveila_core_kref_kotlin_Throwable throwable);
                void (*severe_)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, const char* message);
                void (*severe__)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, const char* message, reveila_core_kref_kotlin_Throwable throwable);
                void (*warning)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, reveila_core_kref_kotlin_Function0 message, reveila_core_kref_kotlin_Throwable throwable);
                void (*warning_)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, const char* message);
                void (*warning__)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, const char* message, reveila_core_kref_kotlin_Throwable throwable);
              } PlatformLogger;
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_logging_MingwPlatformLogger (*MingwPlatformLogger)(const char* name);
                void (*debug)(reveila_core_kref_com_reveila_system_logging_MingwPlatformLogger thiz, reveila_core_kref_kotlin_Function0 message);
                void (*info)(reveila_core_kref_com_reveila_system_logging_MingwPlatformLogger thiz, reveila_core_kref_kotlin_Function0 message);
                void (*severe)(reveila_core_kref_com_reveila_system_logging_MingwPlatformLogger thiz, reveila_core_kref_kotlin_Function0 message, reveila_core_kref_kotlin_Throwable throwable);
                void (*warning)(reveila_core_kref_com_reveila_system_logging_MingwPlatformLogger thiz, reveila_core_kref_kotlin_Function0 message, reveila_core_kref_kotlin_Throwable throwable);
              } MingwPlatformLogger;
              reveila_core_kref_com_reveila_system_logging_PlatformLogger (*createPlatformLogger)(const char* name);
            } logging;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_platform_PlatformOsInfo (*PlatformOsInfo)(const char* name, const char* version, const char* arch);
                const char* (*get_arch)(reveila_core_kref_com_reveila_system_platform_PlatformOsInfo thiz);
                const char* (*get_name)(reveila_core_kref_com_reveila_system_platform_PlatformOsInfo thiz);
                const char* (*get_version)(reveila_core_kref_com_reveila_system_platform_PlatformOsInfo thiz);
                const char* (*component1)(reveila_core_kref_com_reveila_system_platform_PlatformOsInfo thiz);
                const char* (*component2)(reveila_core_kref_com_reveila_system_platform_PlatformOsInfo thiz);
                const char* (*component3)(reveila_core_kref_com_reveila_system_platform_PlatformOsInfo thiz);
                reveila_core_kref_com_reveila_system_platform_PlatformOsInfo (*copy)(reveila_core_kref_com_reveila_system_platform_PlatformOsInfo thiz, const char* name, const char* version, const char* arch);
                reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_system_platform_PlatformOsInfo thiz, reveila_core_kref_kotlin_Any other);
                reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_system_platform_PlatformOsInfo thiz);
                const char* (*toString)(reveila_core_kref_com_reveila_system_platform_PlatformOsInfo thiz);
              } PlatformOsInfo;
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_system_platform_PlatformSystem_Companion (*_instance)();
                  reveila_core_kref_com_reveila_system_platform_PlatformSystem (*current)(reveila_core_kref_com_reveila_system_platform_PlatformSystem_Companion thiz);
                  reveila_core_KLong (*currentTimeMillis)(reveila_core_kref_com_reveila_system_platform_PlatformSystem_Companion thiz);
                  const char* (*getEnv)(reveila_core_kref_com_reveila_system_platform_PlatformSystem_Companion thiz, const char* key);
                  reveila_core_kref_com_reveila_system_platform_PlatformOsInfo (*getOsInfo)(reveila_core_kref_com_reveila_system_platform_PlatformSystem_Companion thiz);
                  const char* (*getProperty)(reveila_core_kref_com_reveila_system_platform_PlatformSystem_Companion thiz, const char* key);
                } Companion;
                reveila_core_KType* (*_type)(void);
                reveila_core_KLong (*currentTimeMillis)(reveila_core_kref_com_reveila_system_platform_PlatformSystem thiz);
                const char* (*getEnv)(reveila_core_kref_com_reveila_system_platform_PlatformSystem thiz, const char* key);
                reveila_core_kref_com_reveila_system_platform_PlatformOsInfo (*getOsInfo)(reveila_core_kref_com_reveila_system_platform_PlatformSystem thiz);
                const char* (*getProperty)(reveila_core_kref_com_reveila_system_platform_PlatformSystem thiz, const char* key);
              } PlatformSystem;
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_platform_MingwPlatformSystem (*_instance)();
                reveila_core_KLong (*currentTimeMillis)(reveila_core_kref_com_reveila_system_platform_MingwPlatformSystem thiz);
                const char* (*getEnv)(reveila_core_kref_com_reveila_system_platform_MingwPlatformSystem thiz, const char* key);
                reveila_core_kref_com_reveila_system_platform_PlatformOsInfo (*getOsInfo)(reveila_core_kref_com_reveila_system_platform_MingwPlatformSystem thiz);
                const char* (*getProperty)(reveila_core_kref_com_reveila_system_platform_MingwPlatformSystem thiz, const char* key);
              } MingwPlatformSystem;
              reveila_core_kref_com_reveila_system_platform_PlatformSystem (*getPlatformSystem)();
            } platform;
            struct {
              struct {
                reveila_core_kref_com_reveila_system_ComponentState (*get)(); /* enum entry for INITIALIZED. */
              } INITIALIZED;
              struct {
                reveila_core_kref_com_reveila_system_ComponentState (*get)(); /* enum entry for STARTING. */
              } STARTING;
              struct {
                reveila_core_kref_com_reveila_system_ComponentState (*get)(); /* enum entry for ACTIVE. */
              } ACTIVE;
              struct {
                reveila_core_kref_com_reveila_system_ComponentState (*get)(); /* enum entry for FAILED. */
              } FAILED;
              struct {
                reveila_core_kref_com_reveila_system_ComponentState (*get)(); /* enum entry for STOPPING. */
              } STOPPING;
              struct {
                reveila_core_kref_com_reveila_system_ComponentState (*get)(); /* enum entry for STOPPED. */
              } STOPPED;
              reveila_core_KType* (*_type)(void);
            } ComponentState;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_Configuration (*Configuration)(const char* jsonContent);
              reveila_core_kref_com_reveila_system_Configuration (*Configuration_)(reveila_core_kref_kotlin_collections_MutableList metaObjects);
              void (*add)(reveila_core_kref_com_reveila_system_Configuration thiz, reveila_core_kref_com_reveila_system_MetaObject metaObject);
              reveila_core_kref_kotlin_collections_List (*getMetaObjects)(reveila_core_kref_com_reveila_system_Configuration thiz);
              void (*remove)(reveila_core_kref_com_reveila_system_Configuration thiz, reveila_core_kref_com_reveila_system_MetaObject metaObject);
              const char* (*toJsonString)(reveila_core_kref_com_reveila_system_Configuration thiz);
            } Configuration;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_ConfigurationLinter (*ConfigurationLinter)();
              void (*lint)(reveila_core_kref_com_reveila_system_ConfigurationLinter thiz, reveila_core_kref_kotlin_collections_Collection metas, reveila_core_kref_com_reveila_system_Properties props);
            } ConfigurationLinter;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_Constants (*_instance)();
              const char* (*get_AI_STATUS_COMPLETED)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_AI_STATUS_ESCALATE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_AI_STATUS_FAILED)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_AI_STATUS_INSUFFICIENT_CONTEXT)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_AI_STATUS_TOOL_CALL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_ARGUMENTS)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_AUTHOR)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_AUTO_START)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_CAPABILITIES)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_CHARACTER_ENCODING)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_CLASS)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_COMPONENT)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_COMPONENT_START_TIMEOUT)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_CONFIGS_DIR_NAME)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_DB_CREATE_SCHEMA)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_DEPENDENCIES)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_DESCRIPTION)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_DIRECTORY)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_DISPLAY_NAME)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_HOT_DEPLOY)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_ISOLATION)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_JOB_ARG_DELAY)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_JOB_ARG_LASTRUN)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_JOB_DATE_FORMAT)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_LAUNCH_STRICT_MODE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_LIB_DIR_NAME)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_LICENSE_TOKEN)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_LOG_CONSOLE_ENABLED)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_LOG_FILE_COUNT)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_LOG_FILE_SIZE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_LOG_LEVEL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_MANEFEST)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_METHODS)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_NAME)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_NETWORK)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_PLATFORM)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_PLATFORM_OS)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_PLUGIN)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_PROVIDER)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REMOTE_REVEILA)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_CAPABILITIES)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_COMPONENTS)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_LIBRARIES)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PERMISSIONS)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_HOT_DEPLOY)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_INSTALL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_INSTALL_ALL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_RELOAD)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_RELOAD_ALL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_RESTART)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_RESTART_ALL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_START)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_STOP)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_UNINSTALL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_UNINSTALL_ALL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_UNLOAD)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_UNLOAD_ALL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_UPDATE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_PLUGINS_RESTART_ON_UPDATE_ALL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_REQUIRED_ROLES)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_RESET_HOME)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_RESTRICTED)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_RUNNABLE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_RUNNABLE_DELAY)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_RUNNABLE_INTERVAL)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_RUNNABLE_METHOD)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_SECURITY_PERIMETER)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_SERVICE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_STANDALONE_MODE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_SYSTEM)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_SYSTEM_HOME)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_SYSTEM_MODE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_SYSTEM_NAME)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_SYSTEM_PROPERTIES)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_SYSTEM_VERSION)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_TASK)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_THREAD_SAFE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_TYPE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_VALUE)(reveila_core_kref_com_reveila_system_Constants thiz);
              const char* (*get_VERSION)(reveila_core_kref_com_reveila_system_Constants thiz);
            } Constants;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_crypto_Cryptographer (*get_cryptographer)(reveila_core_kref_com_reveila_system_Context thiz);
              reveila_core_kref_com_reveila_system_PlatformAdapter (*get_platformAdapter)(reveila_core_kref_com_reveila_system_Context thiz);
              reveila_core_kref_com_reveila_system_Properties (*get_properties)(reveila_core_kref_com_reveila_system_Context thiz);
              reveila_core_kref_com_reveila_system_logging_PlatformLogger (*getLogger)(reveila_core_kref_com_reveila_system_Context thiz);
              reveila_core_kref_com_reveila_system_Proxy (*getProxy)(reveila_core_kref_com_reveila_system_Context thiz, const char* name);
            } Context;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_DependencyValidator (*DependencyValidator)();
              void (*validate)(reveila_core_kref_com_reveila_system_DependencyValidator thiz, reveila_core_kref_kotlin_collections_Map dependencyMap);
            } DependencyValidator;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_Manifest_ExposedMethod (*ExposedMethod)();
                const char* (*get_description)(reveila_core_kref_com_reveila_system_Manifest_ExposedMethod thiz);
                void (*set_description)(reveila_core_kref_com_reveila_system_Manifest_ExposedMethod thiz, const char* set);
                const char* (*get_name)(reveila_core_kref_com_reveila_system_Manifest_ExposedMethod thiz);
                void (*set_name)(reveila_core_kref_com_reveila_system_Manifest_ExposedMethod thiz, const char* set);
                reveila_core_kref_kotlin_collections_MutableList (*get_parameters)(reveila_core_kref_com_reveila_system_Manifest_ExposedMethod thiz);
                void (*set_parameters)(reveila_core_kref_com_reveila_system_Manifest_ExposedMethod thiz, reveila_core_kref_kotlin_collections_MutableList set);
                reveila_core_kref_kotlin_collections_MutableList (*get_requiredRoles)(reveila_core_kref_com_reveila_system_Manifest_ExposedMethod thiz);
                void (*set_requiredRoles)(reveila_core_kref_com_reveila_system_Manifest_ExposedMethod thiz, reveila_core_kref_kotlin_collections_MutableList set);
                const char* (*get_returnType)(reveila_core_kref_com_reveila_system_Manifest_ExposedMethod thiz);
                void (*set_returnType)(reveila_core_kref_com_reveila_system_Manifest_ExposedMethod thiz, const char* set);
              } ExposedMethod;
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_Manifest_Parameter (*Parameter)();
                const char* (*get_description)(reveila_core_kref_com_reveila_system_Manifest_Parameter thiz);
                void (*set_description)(reveila_core_kref_com_reveila_system_Manifest_Parameter thiz, const char* set);
                reveila_core_KBoolean (*get_isRequired)(reveila_core_kref_com_reveila_system_Manifest_Parameter thiz);
                void (*set_isRequired)(reveila_core_kref_com_reveila_system_Manifest_Parameter thiz, reveila_core_KBoolean set);
                reveila_core_KBoolean (*get_isSecret)(reveila_core_kref_com_reveila_system_Manifest_Parameter thiz);
                void (*set_isSecret)(reveila_core_kref_com_reveila_system_Manifest_Parameter thiz, reveila_core_KBoolean set);
                const char* (*get_name)(reveila_core_kref_com_reveila_system_Manifest_Parameter thiz);
                void (*set_name)(reveila_core_kref_com_reveila_system_Manifest_Parameter thiz, const char* set);
                const char* (*get_type)(reveila_core_kref_com_reveila_system_Manifest_Parameter thiz);
                void (*set_type)(reveila_core_kref_com_reveila_system_Manifest_Parameter thiz, const char* set);
              } Parameter;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_Manifest (*Manifest)();
              const char* (*get_author)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_author)(reveila_core_kref_com_reveila_system_Manifest thiz, const char* set);
              const char* (*get_componentType)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_componentType)(reveila_core_kref_com_reveila_system_Manifest thiz, const char* set);
              const char* (*get_description)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_description)(reveila_core_kref_com_reveila_system_Manifest thiz, const char* set);
              const char* (*get_displayName)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_displayName)(reveila_core_kref_com_reveila_system_Manifest thiz, const char* set);
              reveila_core_kref_kotlin_collections_MutableList (*get_exposedMethods)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_exposedMethods)(reveila_core_kref_com_reveila_system_Manifest thiz, reveila_core_kref_kotlin_collections_MutableList set);
              const char* (*get_implementationClass)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_implementationClass)(reveila_core_kref_com_reveila_system_Manifest thiz, const char* set);
              const char* (*get_name)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_name)(reveila_core_kref_com_reveila_system_Manifest thiz, const char* set);
              const char* (*get_org)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_org)(reveila_core_kref_com_reveila_system_Manifest thiz, const char* set);
              reveila_core_kref_kotlin_collections_MutableList (*get_requiredRoles)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_requiredRoles)(reveila_core_kref_com_reveila_system_Manifest thiz, reveila_core_kref_kotlin_collections_MutableList set);
              reveila_core_kref_kotlin_collections_MutableList (*get_roles)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_roles)(reveila_core_kref_com_reveila_system_Manifest thiz, reveila_core_kref_kotlin_collections_MutableList set);
              const char* (*get_version)(reveila_core_kref_com_reveila_system_Manifest thiz);
              void (*set_version)(reveila_core_kref_com_reveila_system_Manifest thiz, const char* set);
            } Manifest;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_MetaObject (*MetaObject)(reveila_core_kref_kotlin_collections_Map dataMap);
              reveila_core_kref_kotlin_collections_Map (*get_dataMap)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              reveila_core_KBoolean (*get_isPlugin)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              void (*set_isPlugin)(reveila_core_kref_com_reveila_system_MetaObject thiz, reveila_core_KBoolean set);
              reveila_core_kref_kotlin_collections_List (*getArguments)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              const char* (*getAuthor)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              reveila_core_kref_kotlin_collections_Map (*getAutoRunConf)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              reveila_core_kref_kotlin_collections_List (*getDependencies)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              const char* (*getDescription)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              const char* (*getImplementationClassName)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              const char* (*getLicense)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              reveila_core_kref_com_reveila_system_Manifest (*getManifest)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              const char* (*getName)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              const char* (*getNetworkPolicy)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              const char* (*getVersion)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              reveila_core_KBoolean (*isAutoStart)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              reveila_core_KBoolean (*isHotDeployEnabled)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              reveila_core_KBoolean (*isThreadSafe)(reveila_core_kref_com_reveila_system_MetaObject thiz);
              reveila_core_KBoolean (*requiresRuntimeIsolation)(reveila_core_kref_com_reveila_system_MetaObject thiz);
            } MetaObject;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_PerformanceTracker_Companion (*_instance)();
                reveila_core_KLong (*get_DEFAULT_PENALTY_MS)(reveila_core_kref_com_reveila_system_PerformanceTracker_Companion thiz);
                reveila_core_kref_com_reveila_system_PerformanceTracker (*getInstance)(reveila_core_kref_com_reveila_system_PerformanceTracker_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_PerformanceTracker (*PerformanceTracker)();
              void (*clear)(reveila_core_kref_com_reveila_system_PerformanceTracker thiz);
              const char* (*getBestNodeUrl)(reveila_core_kref_com_reveila_system_PerformanceTracker thiz);
              reveila_core_KInt (*getCapacity)(reveila_core_kref_com_reveila_system_PerformanceTracker thiz);
              void (*setCapacity)(reveila_core_kref_com_reveila_system_PerformanceTracker thiz, reveila_core_KInt capacity);
              reveila_core_KInt (*size)(reveila_core_kref_com_reveila_system_PerformanceTracker thiz);
              void (*track)(reveila_core_kref_com_reveila_system_PerformanceTracker thiz, reveila_core_kref_kotlin_Long timeUsed, const char* url);
            } PerformanceTracker;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_PerimeterEnforcementMerger_Companion (*_instance)();
                void (*updatePluginJson)(reveila_core_kref_com_reveila_system_PerimeterEnforcementMerger_Companion thiz, const char* jsonFilePath, reveila_core_kref_kotlin_collections_Map perimeterMap);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_PerimeterEnforcementMerger (*PerimeterEnforcementMerger)();
              void (*mergePerimeterSettings)(reveila_core_kref_com_reveila_system_PerimeterEnforcementMerger thiz, const char* configPath, const char* pluginsDir);
            } PerimeterEnforcementMerger;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_crypto_Cryptographer (*getCryptographer)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz);
              reveila_core_kref_com_reveila_system_logging_PlatformLogger (*getLogger)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz);
              const char* (*getPlatformDescription)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz);
              const char* (*getPlatformName)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz);
              reveila_core_kref_com_reveila_system_Properties (*getProperties)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz);
              reveila_core_kref_com_reveila_data_Repository (*getRepository)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz, const char* entityType);
              reveila_core_kref_com_reveila_system_concurrency_PlatformScheduler (*getScheduler)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz);
              reveila_core_kref_kotlin_Array (*listRelativePaths)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz, const char* relativeDirectory, const char* ext);
              void (*loadProperties)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz, reveila_core_kref_com_reveila_system_Properties overrides);
              void (*plug)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz, reveila_core_kref_com_reveila_system_ReveilaEngine reveila);
              reveila_core_kref_kotlin_ByteArray (*readFileBytes)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz, const char* relativePath);
              const char* (*readFileString)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz, const char* relativePath);
              void (*registerAutoCall)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz, const char* componentName, const char* methodName, reveila_core_KLong delaySeconds, reveila_core_KLong intervalSeconds, reveila_core_kref_com_reveila_event_EventConsumer eventConsumer, reveila_core_kref_com_reveila_system_Subject subject);
              void (*unplug)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz);
              void (*unregisterAutoCall)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz, const char* componentName);
              void (*writeFileBytes)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz, const char* relativePath, reveila_core_kref_kotlin_ByteArray data, reveila_core_KBoolean append);
              void (*writeFileString)(reveila_core_kref_com_reveila_system_PlatformAdapter thiz, const char* relativePath, const char* text, reveila_core_KBoolean append);
            } PlatformAdapter;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_Plugin_Companion (*_instance)();
                reveila_core_kref_com_reveila_system_Plugin (*create)(reveila_core_kref_com_reveila_system_Plugin_Companion thiz, const char* name, const char* tenantId);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_Plugin (*Plugin)(const char* sessionId, const char* name, const char* tenantId, const char* traceId);
              const char* (*get_name)(reveila_core_kref_com_reveila_system_Plugin thiz);
              const char* (*get_sessionId)(reveila_core_kref_com_reveila_system_Plugin thiz);
              const char* (*get_tenantId)(reveila_core_kref_com_reveila_system_Plugin thiz);
              const char* (*get_traceId)(reveila_core_kref_com_reveila_system_Plugin thiz);
              reveila_core_kref_com_reveila_system_Plugin (*createChild)(reveila_core_kref_com_reveila_system_Plugin thiz, const char* childName);
              reveila_core_kref_com_reveila_system_Plugin (*deriveChild)(reveila_core_kref_com_reveila_system_Plugin thiz, const char* childName);
              const char* (*toString)(reveila_core_kref_com_reveila_system_Plugin thiz);
            } Plugin;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_PluginComponent (*PluginComponent)();
              reveila_core_kref_com_reveila_system_Context (*get_context)(reveila_core_kref_com_reveila_system_PluginComponent thiz);
              void (*set_context)(reveila_core_kref_com_reveila_system_PluginComponent thiz, reveila_core_kref_com_reveila_system_Context set);
              reveila_core_kref_com_reveila_system_Context (*getContext)(reveila_core_kref_com_reveila_system_PluginComponent thiz);
              void (*setContext)(reveila_core_kref_com_reveila_system_PluginComponent thiz, reveila_core_kref_com_reveila_system_Context context);
            } PluginComponent;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_PluginContext (*PluginContext)(reveila_core_kref_com_reveila_system_SystemContext systemContext, reveila_core_kref_com_reveila_system_Manifest manifest, reveila_core_kref_com_reveila_system_Properties properties);
              reveila_core_kref_com_reveila_system_PlatformAdapter (*get_platformAdapter)(reveila_core_kref_com_reveila_system_PluginContext thiz);
              reveila_core_kref_com_reveila_system_Properties (*get_properties)(reveila_core_kref_com_reveila_system_PluginContext thiz);
              reveila_core_kref_com_reveila_system_logging_PlatformLogger (*getLogger)(reveila_core_kref_com_reveila_system_PluginContext thiz);
              reveila_core_kref_com_reveila_system_Proxy (*getProxy)(reveila_core_kref_com_reveila_system_PluginContext thiz, const char* name);
            } PluginContext;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_PluginProxy (*PluginProxy)(reveila_core_kref_com_reveila_system_Proxy targetProxy, reveila_core_kref_com_reveila_system_Subject subject);
              reveila_core_kref_kotlin_Any (*getInstance)(reveila_core_kref_com_reveila_system_PluginProxy thiz);
              const char* (*getName)(reveila_core_kref_com_reveila_system_PluginProxy thiz);
              reveila_core_kref_kotlin_collections_List (*getRequiredRoles)(reveila_core_kref_com_reveila_system_PluginProxy thiz);
              reveila_core_kref_kotlin_Any (*invoke)(reveila_core_kref_com_reveila_system_PluginProxy thiz, const char* methodName, reveila_core_kref_kotlin_Array args);
            } PluginProxy;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_PluginRunner (*_instance)();
              void (*reportResult)(reveila_core_kref_com_reveila_system_PluginRunner thiz, const char* callbackUrl, const char* traceId, const char* pluginId, const char* methodName, reveila_core_kref_kotlin_Any result);
            } PluginRunner;
            struct {
              reveila_core_KType* (*_type)(void);
              const char* (*get_name)(reveila_core_kref_com_reveila_system_Principal thiz);
            } Principal;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_UserPrincipal (*UserPrincipal)(const char* name);
              const char* (*get_name)(reveila_core_kref_com_reveila_system_UserPrincipal thiz);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_system_UserPrincipal thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_system_UserPrincipal thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_system_UserPrincipal thiz);
            } UserPrincipal;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_RolePrincipal (*RolePrincipal)(const char* name);
              const char* (*get_name)(reveila_core_kref_com_reveila_system_RolePrincipal thiz);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_system_RolePrincipal thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_system_RolePrincipal thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_system_RolePrincipal thiz);
            } RolePrincipal;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_Properties (*Properties)(reveila_core_kref_kotlin_collections_Map initialMap);
              reveila_core_kref_com_reveila_system_Properties (*Properties_)(reveila_core_kref_com_reveila_system_Properties defaults);
              reveila_core_KInt (*get_size)(reveila_core_kref_com_reveila_system_Properties thiz);
              void (*clear)(reveila_core_kref_com_reveila_system_Properties thiz);
              reveila_core_KBoolean (*containsKey)(reveila_core_kref_com_reveila_system_Properties thiz, const char* key);
              void (*forEach)(reveila_core_kref_com_reveila_system_Properties thiz, reveila_core_kref_kotlin_Function2 action);
              const char* (*get)(reveila_core_kref_com_reveila_system_Properties thiz, const char* key);
              const char* (*getProperty)(reveila_core_kref_com_reveila_system_Properties thiz, const char* key);
              const char* (*getProperty_)(reveila_core_kref_com_reveila_system_Properties thiz, const char* key, const char* defaultValue);
              reveila_core_KBoolean (*isEmpty)(reveila_core_kref_com_reveila_system_Properties thiz);
              void (*load)(reveila_core_kref_com_reveila_system_Properties thiz, const char* content);
              void (*load_)(reveila_core_kref_com_reveila_system_Properties thiz, reveila_core_kref_kotlin_ByteArray bytes);
              void (*putAll)(reveila_core_kref_com_reveila_system_Properties thiz, reveila_core_kref_com_reveila_system_Properties other);
              void (*putAll_)(reveila_core_kref_com_reveila_system_Properties thiz, reveila_core_kref_kotlin_collections_Map other);
              const char* (*remove)(reveila_core_kref_com_reveila_system_Properties thiz, const char* key);
              void (*set)(reveila_core_kref_com_reveila_system_Properties thiz, const char* key, const char* value);
              const char* (*setProperty)(reveila_core_kref_com_reveila_system_Properties thiz, const char* key, const char* value);
              const char* (*store)(reveila_core_kref_com_reveila_system_Properties thiz, const char* comments);
              reveila_core_kref_kotlin_collections_Set (*stringPropertyNames)(reveila_core_kref_com_reveila_system_Properties thiz);
              reveila_core_kref_kotlin_collections_Map (*toMap)(reveila_core_kref_com_reveila_system_Properties thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_system_Properties thiz);
            } Properties;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_kotlin_Any (*getInstance)(reveila_core_kref_com_reveila_system_Proxy thiz);
              const char* (*getName)(reveila_core_kref_com_reveila_system_Proxy thiz);
              reveila_core_kref_kotlin_collections_List (*getRequiredRoles)(reveila_core_kref_com_reveila_system_Proxy thiz);
              reveila_core_kref_kotlin_Any (*invoke)(reveila_core_kref_com_reveila_system_Proxy thiz, const char* methodName, reveila_core_kref_kotlin_Array args);
            } Proxy;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_ReveilaClient (*ReveilaClient)();
              reveila_core_kref_kotlin_Any (*invokeHost)(reveila_core_kref_com_reveila_system_ReveilaClient thiz, const char* component, const char* method, reveila_core_kref_kotlin_collections_Map arguments);
            } ReveilaClient;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_kotlin_Any (*invoke)(reveila_core_kref_com_reveila_system_ReveilaEngine thiz, const char* componentName, const char* methodName, reveila_core_kref_kotlin_Array params, const char* callerIp, reveila_core_kref_com_reveila_system_Subject subject);
              reveila_core_KBoolean (*isRunning)(reveila_core_kref_com_reveila_system_ReveilaEngine thiz);
              void (*shutdown)(reveila_core_kref_com_reveila_system_ReveilaEngine thiz);
            } ReveilaEngine;
            struct {
              reveila_core_KType* (*_type)(void);
              void (*start)(reveila_core_kref_com_reveila_system_Startable thiz);
            } Startable;
            struct {
              reveila_core_KType* (*_type)(void);
              void (*stop)(reveila_core_kref_com_reveila_system_Stoppable thiz);
            } Stoppable;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_Subject (*Subject)(reveila_core_kref_kotlin_collections_Set initialPrincipals);
              reveila_core_kref_kotlin_collections_MutableSet (*get_principals)(reveila_core_kref_com_reveila_system_Subject thiz);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_system_Subject thiz, reveila_core_kref_kotlin_Any other);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_system_Subject thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_system_Subject thiz);
            } Subject;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_SystemComponent (*SystemComponent)();
              reveila_core_kref_com_reveila_system_Context (*get_context)(reveila_core_kref_com_reveila_system_SystemComponent thiz);
              void (*set_context)(reveila_core_kref_com_reveila_system_SystemComponent thiz, reveila_core_kref_com_reveila_system_Context set);
              reveila_core_kref_kotlin_Any (*getComponent)(reveila_core_kref_com_reveila_system_SystemComponent thiz, const char* name);
              void (*notifyEvent)(reveila_core_kref_com_reveila_system_SystemComponent thiz, reveila_core_kref_com_reveila_event_EventObject evtObj);
            } SystemComponent;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_SystemContext (*SystemContext)(reveila_core_kref_com_reveila_system_Properties properties, reveila_core_kref_com_reveila_event_EventManager eventManager, reveila_core_kref_com_reveila_system_logging_PlatformLogger logger, reveila_core_kref_com_reveila_crypto_Cryptographer cryptographer, reveila_core_kref_com_reveila_system_PlatformAdapter platformAdapter);
              reveila_core_kref_com_reveila_crypto_Cryptographer (*get_cryptographer)(reveila_core_kref_com_reveila_system_SystemContext thiz);
              void (*set_cryptographer)(reveila_core_kref_com_reveila_system_SystemContext thiz, reveila_core_kref_com_reveila_crypto_Cryptographer set);
              reveila_core_kref_com_reveila_system_PlatformAdapter (*get_platformAdapter)(reveila_core_kref_com_reveila_system_SystemContext thiz);
              reveila_core_kref_com_reveila_system_Properties (*get_properties)(reveila_core_kref_com_reveila_system_SystemContext thiz);
              void (*add)(reveila_core_kref_com_reveila_system_SystemContext thiz, reveila_core_kref_com_reveila_system_Proxy proxy);
              void (*clear)(reveila_core_kref_com_reveila_system_SystemContext thiz);
              reveila_core_kref_com_reveila_event_EventManager (*getEventManager)(reveila_core_kref_com_reveila_system_SystemContext thiz);
              reveila_core_kref_com_reveila_system_logging_PlatformLogger (*getLogger)(reveila_core_kref_com_reveila_system_SystemContext thiz);
              const char* (*getProperty)(reveila_core_kref_com_reveila_system_SystemContext thiz, const char* propertyName, const char* pluginName);
              reveila_core_kref_com_reveila_system_Proxy (*getProxy)(reveila_core_kref_com_reveila_system_SystemContext thiz, const char* name);
              reveila_core_kref_com_reveila_system_Proxy (*getProxy_)(reveila_core_kref_com_reveila_system_SystemContext thiz, const char* name, reveila_core_kref_com_reveila_system_Subject subject);
              void (*notifyEvent)(reveila_core_kref_com_reveila_system_SystemContext thiz, reveila_core_kref_com_reveila_event_EventObject evtObj);
              void (*remove)(reveila_core_kref_com_reveila_system_SystemContext thiz, reveila_core_kref_com_reveila_system_Proxy proxy);
            } SystemContext;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_system_SystemHome_Companion (*_instance)();
                reveila_core_kref_kotlin_Array (*get_DIRS)(reveila_core_kref_com_reveila_system_SystemHome_Companion thiz);
              } Companion;
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_SystemHome (*SystemHome)(const char* systemHome);
              const char* (*get_systemHome)(reveila_core_kref_com_reveila_system_SystemHome thiz);
              void (*createDirectoryStructure)(reveila_core_kref_com_reveila_system_SystemHome thiz, reveila_core_KBoolean createNew);
              const char* (*getBinDirectory)(reveila_core_kref_com_reveila_system_SystemHome thiz);
              const char* (*getConfigsDirectory)(reveila_core_kref_com_reveila_system_SystemHome thiz);
              const char* (*getDataDirectory)(reveila_core_kref_com_reveila_system_SystemHome thiz);
              const char* (*getLogsDirectory)(reveila_core_kref_com_reveila_system_SystemHome thiz);
              const char* (*getPluginsDirectory)(reveila_core_kref_com_reveila_system_SystemHome thiz);
            } SystemHome;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_UiController (*UiController)();
              const char* (*getSettings)(reveila_core_kref_com_reveila_system_UiController thiz, const char* tab);
              const char* (*getUiSchema)(reveila_core_kref_com_reveila_system_UiController thiz);
              void (*onStart)(reveila_core_kref_com_reveila_system_UiController thiz);
              void (*onStop)(reveila_core_kref_com_reveila_system_UiController thiz);
              void (*saveSettings)(reveila_core_kref_com_reveila_system_UiController thiz, const char* tab, const char* jsonConfig);
            } UiController;
          } system;
          struct {
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_tools_Terminal_Companion (*_instance)();
                reveila_core_KLong (*get_DEFAULT_TIMEOUT_SECONDS)(reveila_core_kref_com_reveila_tools_Terminal_Companion thiz);
                reveila_core_kref_com_reveila_tools_Terminal (*getInstance)(reveila_core_kref_com_reveila_tools_Terminal_Companion thiz, const char* secureScriptDirectory);
              } Companion;
              reveila_core_KType* (*_type)(void);
              const char* (*get_secureScriptDirectory)(reveila_core_kref_com_reveila_tools_Terminal thiz);
              const char* (*executeDynamicScript)(reveila_core_kref_com_reveila_tools_Terminal thiz, const char* rawScriptContent);
              const char* (*executeSafeScript)(reveila_core_kref_com_reveila_tools_Terminal thiz, const char* scriptPath, reveila_core_kref_kotlin_collections_List args);
            } Terminal;
            const char* (*executePlatformProcess)(reveila_core_kref_kotlin_collections_List command, reveila_core_KLong timeoutSeconds);
          } tools;
          struct {
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_util_SafeCast (*_instance)();
            } SafeCast;
            struct {
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_util_io_FileUtil_Companion (*_instance)();
                  reveila_core_KLong (*copyFile)(reveila_core_kref_com_reveila_util_io_FileUtil_Companion thiz, const char* sourcePath, const char* targetPath, reveila_core_KBoolean overwrite);
                  void (*createDirectories)(reveila_core_kref_com_reveila_util_io_FileUtil_Companion thiz, const char* path);
                  reveila_core_KBoolean (*delete_)(reveila_core_kref_com_reveila_util_io_FileUtil_Companion thiz, const char* path, reveila_core_KBoolean recursive);
                  reveila_core_KBoolean (*exists)(reveila_core_kref_com_reveila_util_io_FileUtil_Companion thiz, const char* path);
                  reveila_core_KBoolean (*isDirectory)(reveila_core_kref_com_reveila_util_io_FileUtil_Companion thiz, const char* path);
                  reveila_core_kref_kotlin_collections_List (*listRelativePaths)(reveila_core_kref_com_reveila_util_io_FileUtil_Companion thiz, const char* directory, const char* fileExt);
                  const char* (*readText)(reveila_core_kref_com_reveila_util_io_FileUtil_Companion thiz, const char* path);
                  void (*writeText)(reveila_core_kref_com_reveila_util_io_FileUtil_Companion thiz, const char* path, const char* text, reveila_core_KBoolean append);
                } Companion;
                reveila_core_KType* (*_type)(void);
              } FileUtil;
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_util_io_FormField (*FormField)(const char* name, const char* className, const char* value);
                const char* (*get_className)(reveila_core_kref_com_reveila_util_io_FormField thiz);
                void (*set_className)(reveila_core_kref_com_reveila_util_io_FormField thiz, const char* set);
                const char* (*get_description)(reveila_core_kref_com_reveila_util_io_FormField thiz);
                void (*set_description)(reveila_core_kref_com_reveila_util_io_FormField thiz, const char* set);
                reveila_core_KBoolean (*get_isBoolean)(reveila_core_kref_com_reveila_util_io_FormField thiz);
                void (*set_isBoolean)(reveila_core_kref_com_reveila_util_io_FormField thiz, reveila_core_KBoolean set);
                reveila_core_KBoolean (*get_isReadable)(reveila_core_kref_com_reveila_util_io_FormField thiz);
                void (*set_isReadable)(reveila_core_kref_com_reveila_util_io_FormField thiz, reveila_core_KBoolean set);
                reveila_core_KBoolean (*get_isWritable)(reveila_core_kref_com_reveila_util_io_FormField thiz);
                void (*set_isWritable)(reveila_core_kref_com_reveila_util_io_FormField thiz, reveila_core_KBoolean set);
                const char* (*get_label)(reveila_core_kref_com_reveila_util_io_FormField thiz);
                void (*set_label)(reveila_core_kref_com_reveila_util_io_FormField thiz, const char* set);
                const char* (*get_name)(reveila_core_kref_com_reveila_util_io_FormField thiz);
                void (*set_name)(reveila_core_kref_com_reveila_util_io_FormField thiz, const char* set);
                const char* (*get_value)(reveila_core_kref_com_reveila_util_io_FormField thiz);
                void (*set_value)(reveila_core_kref_com_reveila_util_io_FormField thiz, const char* set);
                const char* (*setValue)(reveila_core_kref_com_reveila_util_io_FormField thiz, const char* value);
              } FormField;
            } io;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_util_json_JsonException (*JsonException)(const char* message);
                reveila_core_kref_com_reveila_util_json_JsonException (*JsonException_)(const char* message, reveila_core_kref_kotlin_Throwable cause);
              } JsonException;
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_util_json_JsonUtil_Companion (*_instance)();
                  reveila_core_kref_kotlinx_serialization_json_Json (*get_JSON)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz);
                  reveila_core_kref_kotlinx_serialization_json_Json (*get_PRETTY_JSON)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz);
                  const char* (*clean)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, const char* json);
                  reveila_core_kref_kotlin_collections_List (*findValuesByKey)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, reveila_core_kref_kotlin_collections_Map map, const char* key);
                  reveila_core_kref_kotlin_Any (*fromElement)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, reveila_core_kref_kotlinx_serialization_json_JsonElement element);
                  reveila_core_kref_kotlin_collections_List (*parseJsonFileToList)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, const char* filePath);
                  reveila_core_kref_kotlin_collections_Map (*parseJsonFileToMap)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, const char* filePath);
                  reveila_core_kref_kotlin_collections_List (*parseJsonStringToList)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, const char* json);
                  reveila_core_kref_kotlin_collections_Map (*parseJsonStringToMap)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, const char* json);
                  const char* (*removeRoot)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, const char* json);
                  reveila_core_kref_kotlinx_serialization_json_JsonElement (*toElement)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, reveila_core_kref_kotlin_Any value);
                  void (*toJsonFile)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, reveila_core_kref_kotlin_Any data, const char* filePath);
                  const char* (*toJsonString)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, reveila_core_kref_kotlin_Any data);
                  const char* (*toPrettyJsonString)(reveila_core_kref_com_reveila_util_json_JsonUtil_Companion thiz, reveila_core_kref_kotlin_Any data);
                } Companion;
                reveila_core_KType* (*_type)(void);
              } JsonUtil;
            } json;
            struct {
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_util_md_Markdown_Companion (*_instance)();
                  const char* (*toHtml)(reveila_core_kref_com_reveila_util_md_Markdown_Companion thiz, const char* markdown, reveila_core_KBoolean escapeHtml);
                } Companion;
                reveila_core_KType* (*_type)(void);
              } Markdown;
            } md;
            struct {
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_util_xml_XmlElement (*XmlElement)(const char* name, reveila_core_kref_kotlin_collections_Map attributes, reveila_core_kref_kotlin_collections_List children, const char* text);
                reveila_core_kref_kotlin_collections_Map (*get_attributes)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz);
                reveila_core_kref_kotlin_collections_List (*get_children)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz);
                const char* (*get_name)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz);
                const char* (*get_text)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz);
                const char* (*component1)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz);
                reveila_core_kref_kotlin_collections_Map (*component2)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz);
                reveila_core_kref_kotlin_collections_List (*component3)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz);
                const char* (*component4)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz);
                reveila_core_kref_com_reveila_util_xml_XmlElement (*copy)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz, const char* name, reveila_core_kref_kotlin_collections_Map attributes, reveila_core_kref_kotlin_collections_List children, const char* text);
                reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz, reveila_core_kref_kotlin_Any other);
                const char* (*getAttribute)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz, const char* attrName);
                reveila_core_kref_com_reveila_util_xml_XmlElement (*getChild)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz, const char* childName);
                reveila_core_kref_kotlin_collections_List (*getChildren)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz, const char* childName);
                reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz);
                const char* (*toString)(reveila_core_kref_com_reveila_util_xml_XmlElement thiz);
              } XmlElement;
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_util_xml_XmlDocument (*XmlDocument)(reveila_core_kref_com_reveila_util_xml_XmlElement root);
                reveila_core_kref_com_reveila_util_xml_XmlElement (*get_root)(reveila_core_kref_com_reveila_util_xml_XmlDocument thiz);
                reveila_core_kref_com_reveila_util_xml_XmlElement (*getRootElement)(reveila_core_kref_com_reveila_util_xml_XmlDocument thiz);
              } XmlDocument;
              struct {
                reveila_core_KType* (*_type)(void);
                reveila_core_kref_com_reveila_util_xml_XmlErrorHandler (*XmlErrorHandler)();
                reveila_core_kref_kotlin_collections_MutableList (*get_errors)(reveila_core_kref_com_reveila_util_xml_XmlErrorHandler thiz);
                reveila_core_kref_kotlin_collections_MutableList (*get_warnings)(reveila_core_kref_com_reveila_util_xml_XmlErrorHandler thiz);
                void (*error)(reveila_core_kref_com_reveila_util_xml_XmlErrorHandler thiz, const char* msg);
                void (*fatalError)(reveila_core_kref_com_reveila_util_xml_XmlErrorHandler thiz, const char* msg);
                void (*warning)(reveila_core_kref_com_reveila_util_xml_XmlErrorHandler thiz, const char* msg);
              } XmlErrorHandler;
              struct {
                struct {
                  reveila_core_KType* (*_type)(void);
                  reveila_core_kref_com_reveila_util_xml_XmlUtil_Companion (*_instance)();
                  const char* (*escapeXml)(reveila_core_kref_com_reveila_util_xml_XmlUtil_Companion thiz, const char* text);
                  reveila_core_kref_com_reveila_util_xml_XmlDocument (*parse)(reveila_core_kref_com_reveila_util_xml_XmlUtil_Companion thiz, const char* xmlContent);
                  reveila_core_kref_com_reveila_util_xml_XmlDocument (*parseFile)(reveila_core_kref_com_reveila_util_xml_XmlUtil_Companion thiz, const char* filePath);
                  const char* (*toXmlString)(reveila_core_kref_com_reveila_util_xml_XmlUtil_Companion thiz, reveila_core_kref_com_reveila_util_xml_XmlDocument doc, reveila_core_KBoolean pretty);
                  const char* (*toXmlString_)(reveila_core_kref_com_reveila_util_xml_XmlUtil_Companion thiz, reveila_core_kref_com_reveila_util_xml_XmlElement element, reveila_core_KBoolean pretty, reveila_core_KInt indent);
                  const char* (*unescapeXml)(reveila_core_kref_com_reveila_util_xml_XmlUtil_Companion thiz, const char* text);
                } Companion;
                reveila_core_KType* (*_type)(void);
              } XmlUtil;
            } xml;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_util_ScoreTracker (*ScoreTracker)(const char* name);
              const char* (*get_name)(reveila_core_kref_com_reveila_util_ScoreTracker thiz);
              void (*applyPoints)(reveila_core_kref_com_reveila_util_ScoreTracker thiz, reveila_core_kref_kotlin_Long points, const char* name);
              const char* (*getBest)(reveila_core_kref_com_reveila_util_ScoreTracker thiz);
              const char* (*getWorst)(reveila_core_kref_com_reveila_util_ScoreTracker thiz);
            } ScoreTracker;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_util_StringUtil (*_instance)();
              const char* (*replace)(reveila_core_kref_com_reveila_util_StringUtil thiz, const char* source, const char* tagLeft, const char* tagRight, reveila_core_kref_kotlin_collections_Map replacements, reveila_core_KBoolean isTrimKey, reveila_core_KBoolean isKeyToLowerCase, const char* escChars);
              const char* (*truncate)(reveila_core_kref_com_reveila_util_StringUtil thiz, const char* srcStr, reveila_core_KInt toLength, const char* suffix);
            } StringUtil;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_util_TimeFormat (*_instance)();
              const char* (*duration)(reveila_core_kref_com_reveila_util_TimeFormat thiz, reveila_core_KLong ms);
              const char* (*timestamp)(reveila_core_kref_com_reveila_util_TimeFormat thiz, reveila_core_KLong ms);
            } TimeFormat;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_util_Uuid (*_instance)();
              const char* (*randomUuid)(reveila_core_kref_com_reveila_util_Uuid thiz);
            } Uuid;
          } util;
        } reveila;
      } com;
    } root;
  } kotlin;
} reveila_core_ExportedSymbols;
extern reveila_core_ExportedSymbols* reveila_core_symbols(void);
#ifdef __cplusplus
}  /* extern "C" */
#endif
#endif  /* KONAN_REVEILA_CORE_H */
