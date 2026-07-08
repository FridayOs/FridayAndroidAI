package com.dark.tool_neuron.service;

/**
 * Service-side TnEvent stream. The service emits every log / error /
 * cancellation through this callback to every bound client.
 *
 * Field layout mirrors the C sink callback in tn_security.h verbatim so the
 * client can reconstruct a typed TnEvent without parcelable plumbing.
 */
oneway interface ITnEventCallback {

    /**
     * @param kind         0=Log, 1=Error, 2=Cancellation
     * @param level        TnLevel.value (0..5), 0 for non-log kinds
     * @param module       TnModule.value
     * @param code         TnCode.value for kind=Error, else 0
     * @param stage        TnStage.value for kind=Error, else 0
     * @param tag          may be null
     * @param opId         may be null — operation correlation id
     * @param file         call-site source file, may be null
     * @param line         call-site line, 0 if unknown
     * @param func         call-site function, may be null
     * @param message      formatted message
     * @param suggestion   user-actionable fix for kind=Error, may be null
     * @param timestampMs  wall-clock at emit
     * @param tid          emitting Linux thread id
     */
    void onEvent(int kind, int level, int module, int code, int stage,
                 String tag, String opId, String file, int line, String func,
                 String message, String suggestion, long timestampMs, int tid);

    /**
     * Service drained a crash file written by its signal handler in a prior
     * process lifetime. crashJson is the verbatim file contents (see
     * tn_security.cpp crash format).
     */
    void onCrashReplay(String crashJson, String crashFilePath);
}
