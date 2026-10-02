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
} reveila_core_kref_com_reveila_crypto_PlatformCryptographer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_crypto_CryptoException;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Throwable;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_crypto_Cryptographer;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_ByteArray;
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
} reveila_core_kref_kotlin_collections_Map;
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
} reveila_core_kref_kotlin_collections_MutableMap;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_Any;
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
} reveila_core_kref_com_reveila_data_QueryRequest;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_data_Sort;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_collections_List;
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
} reveila_core_kref_com_reveila_persistence_VectorMatch;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_FloatArray;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_persistence_VectorStore;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_AbstractGuardedRuntime;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Plugin;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SecurityPerimeter;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_InvocationResult;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_FlightRecorder;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_GuardedRuntime;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_GuardrailResponse;
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
} reveila_core_kref_com_reveila_safety_KillSwitch;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_safety_SafetyStatus;
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
} reveila_core_kref_kotlin_collections_Set;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_service_EchoService;
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
} reveila_core_kref_com_reveila_system_logging_PlatformLogger;
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
} reveila_core_kref_com_reveila_system_ComponentState;
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
} reveila_core_kref_com_reveila_system_Constants;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_DependencyValidator;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Manifest;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_kotlin_collections_MutableList;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Manifest_ExposedMethod;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Manifest_Parameter;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_MetaObject;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_PluginComponent;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Context;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Startable;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Stoppable;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_AbstractComponent;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Proxy;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_Plugin_Companion;
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
} reveila_core_kref_kotlin_Array;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_system_SystemComponent;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_SafeCast;
typedef struct {
  reveila_core_KNativePtr pinned;
} reveila_core_kref_com_reveila_util_io_FormField;
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
              void (*emergencyStopAll)(reveila_core_kref_com_reveila_safety_KillSwitch thiz);
              reveila_core_kref_com_reveila_safety_SafetyStatus (*getStatus)(reveila_core_kref_com_reveila_safety_KillSwitch thiz, const char* agentId);
              reveila_core_KBoolean (*isAuthorized)(reveila_core_kref_com_reveila_safety_KillSwitch thiz, const char* agentId);
            } KillSwitch;
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
          } service;
          struct {
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
                void (*info)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, reveila_core_kref_kotlin_Function0 message);
                void (*severe)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, reveila_core_kref_kotlin_Function0 message, reveila_core_kref_kotlin_Throwable throwable);
                void (*warning)(reveila_core_kref_com_reveila_system_logging_PlatformLogger thiz, reveila_core_kref_kotlin_Function0 message, reveila_core_kref_kotlin_Throwable throwable);
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
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_PluginComponent (*PluginComponent)();
              reveila_core_kref_com_reveila_system_Context (*get_context)(reveila_core_kref_com_reveila_system_PluginComponent thiz);
              void (*set_context)(reveila_core_kref_com_reveila_system_PluginComponent thiz, reveila_core_kref_com_reveila_system_Context set);
              reveila_core_kref_com_reveila_system_Context (*getContext)(reveila_core_kref_com_reveila_system_PluginComponent thiz);
              void (*setContext)(reveila_core_kref_com_reveila_system_PluginComponent thiz, reveila_core_kref_com_reveila_system_Context context);
            } PluginComponent;
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
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_Proxy (*getProxy)(reveila_core_kref_com_reveila_system_Context thiz, const char* name);
            } Context;
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
            } Plugin;
            struct {
              reveila_core_KType* (*_type)(void);
              const char* (*getName)(reveila_core_kref_com_reveila_system_Principal thiz);
            } Principal;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_UserPrincipal (*UserPrincipal)(const char* name);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_system_UserPrincipal thiz, reveila_core_kref_kotlin_Any other);
              const char* (*getName)(reveila_core_kref_com_reveila_system_UserPrincipal thiz);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_system_UserPrincipal thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_system_UserPrincipal thiz);
            } UserPrincipal;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_RolePrincipal (*RolePrincipal)(const char* name);
              reveila_core_KBoolean (*equals)(reveila_core_kref_com_reveila_system_RolePrincipal thiz, reveila_core_kref_kotlin_Any other);
              const char* (*getName)(reveila_core_kref_com_reveila_system_RolePrincipal thiz);
              reveila_core_KInt (*hashCode)(reveila_core_kref_com_reveila_system_RolePrincipal thiz);
              const char* (*toString)(reveila_core_kref_com_reveila_system_RolePrincipal thiz);
            } RolePrincipal;
            struct {
              reveila_core_KType* (*_type)(void);
              const char* (*getName)(reveila_core_kref_com_reveila_system_Proxy thiz);
              reveila_core_kref_kotlin_collections_List (*getRequiredRoles)(reveila_core_kref_com_reveila_system_Proxy thiz);
              reveila_core_kref_kotlin_Any (*invoke)(reveila_core_kref_com_reveila_system_Proxy thiz, const char* methodName, reveila_core_kref_kotlin_Array args);
            } Proxy;
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_system_SystemComponent (*SystemComponent)();
              void (*notifyEvent)(reveila_core_kref_com_reveila_system_SystemComponent thiz, reveila_core_kref_com_reveila_event_EventObject evtObj);
            } SystemComponent;
          } system;
          struct {
            struct {
              reveila_core_KType* (*_type)(void);
              reveila_core_kref_com_reveila_util_SafeCast (*_instance)();
            } SafeCast;
            struct {
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
