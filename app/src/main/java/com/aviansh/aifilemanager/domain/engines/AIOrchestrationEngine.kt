package com.aviansh.aifilemanager.domain.engines

import android.util.Log
import com.aviansh.aifilemanager.domain.data.AIResponse
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.TransactionProgress
import com.aviansh.aifilemanager.domain.data.TransactionResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "AIOrchestration"

/**
 * Bridges the AI response layer to the transaction execution layer.
 *
 * Flow:
 *   AI generates [AIResponse] with a generatorCode (Python)
 *   → Chaquopy executes the code  → JSON action list
 *   → [TransactionEngine] snapshots + executes with full rollback support
 *
 * The [executeAIResponse] function returns a cold [Flow] of [TransactionProgress]
 * so the ViewModel can update UI incrementally.
 */
class AIOrchestrationEngine(
    private val transactionEngine: TransactionEngine = TransactionEngine()
) {

    /**
     * Resolves actions from an [AIResponse] and emits progress events.
     *
     * Callers should collect this flow on a coroutine tied to viewModelScope.
     * The flow is dispatched on [Dispatchers.IO] automatically.
     */
    fun executeAIResponse(response: AIResponse): Flow<TransactionProgress> = flow {
        if (!response.actionable) {
            emit(TransactionProgress.Idle)
            return@flow
        }

        Log.e("AI Response", Gson().toJson(response))
        // ── Step 1: resolve actions ──────────────────────────────────────────
        val actions: List<FileAction> = when {
            response.actions != null -> {
                Log.d(TAG, "Using pre-parsed actions (${response.actions.size})")
                response.actions
            }
            response.generatorCode != null -> {
                Log.d(TAG, "Running Chaquopy generator to resolve actions")
                try {
                    PythonEngine.generateActions(response.generatorCode)
                } catch (e: Exception) {
                    Log.e(TAG, "Generator failed", e)
                    emit(TransactionProgress.Failed("Generator error: ${e.message}"))
                    return@flow
                }
            }
            else -> {
                emit(TransactionProgress.Failed("Actionable response has no generator code or actions"))
                return@flow
            }
        }

        if (actions.isEmpty()) {
            emit(TransactionProgress.Idle)
            return@flow
        }

        // ── Step 2: surface pending actions for user confirmation ────────────
        emit(TransactionProgress.Pending(actions))
        // The ViewModel will wait for the user to confirm before calling
        // executeConfirmedActions(). We stop here until that call is made.
    }.flowOn(Dispatchers.IO)

    /**
     * Called after the user confirms the pending action list.
     * Runs the full snapshot + execute + commit/rollback pipeline.
     */
    suspend fun executeConfirmedActions(actions: List<FileAction>): TransactionProgress {
        Log.d(TAG, "Executing ${actions.size} confirmed actions")
        return try {
            when (val result = transactionEngine.run(actions)) {
                is TransactionResult.SUCCESS -> {
                    Log.d(TAG, "Transaction succeeded")
                    TransactionProgress.Succeeded(actions.size)
                }
                is TransactionResult.FAILURE -> {
                    Log.e(TAG, "Transaction failed: ${result.reason}")
                    TransactionProgress.RolledBack(result.reason)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected engine error", e)
            TransactionProgress.Failed(e.message ?: "Unknown error")
        }
    }

    /**
     * Convenience: parse generator code and execute immediately without user confirmation.
     * Use this only for operations that are inherently safe (e.g. read-only analyses).
     */
    suspend fun executeGeneratorDirectly(generatorCode: String): TransactionProgress {
        return try {
            val actions = PythonEngine.generateActions(generatorCode)
            executeConfirmedActions(actions)
        } catch (e: Exception) {
            TransactionProgress.Failed("Generator error: ${e.message}")
        }
    }
}