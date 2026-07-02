package com.aviansh.aifilemanager.domain.engines

import android.util.Log
import com.aviansh.aifilemanager.domain.data.ParsedAIResponse
import com.aviansh.aifilemanager.domain.data.FileAction
import com.aviansh.aifilemanager.domain.data.TransactionProgress
import com.aviansh.aifilemanager.domain.data.TransactionResult
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "AIOrchestration"


class AIOrchestrationEngine(
    private val transactionEngine: TransactionEngine = TransactionEngine()
) {

    fun executeAIResponse(response: ParsedAIResponse): Flow<TransactionProgress> = flow {
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

        emit(TransactionProgress.Pending(actions))
    }.flowOn(Dispatchers.IO)

    /**
     * Called after the user confirms the pending action list.
     * Runs the full snapshot + execute + commit/rollback pipeline.
     */
    fun executeConfirmedActions(actions: List<FileAction>): TransactionProgress {
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
}