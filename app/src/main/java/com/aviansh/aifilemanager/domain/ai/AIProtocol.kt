package com.aviansh.aifilemanager.domain.ai

import com.aviansh.aifilemanager.domain.data.*
import org.json.JSONObject

sealed class AIProtocol {

    data class Response(
        val aiResponse: AIResponse
    ): AIProtocol()

    data class Request(
        val command: String
    ): AIProtocol()
}

object AIProtocolParser {

    fun parse(json: String): AIProtocol {

        val obj = JSONObject(json)

        return when(obj.getString("type")){

            "request" -> {

                val command=obj.getString("command")

                AIProtocol.Request(command)

            }

            "response" -> {

                val actionable=obj.getBoolean("actionable")

                val actions= arrayListOf<FileAction>()

                if(actionable){

                    val arr=obj.getJSONArray("actions")

                    for(i in 0 until arr.length()){

                        val actionObj=arr.getJSONObject(i)

                        val type=when(actionObj.getString("action").lowercase()){

                            "move"->FileActionType.MOVE

                            "copy"->FileActionType.COPY

                            "delete"->FileActionType.DELETE

                            "create"->FileActionType.CREATE

                            else->throw IllegalArgumentException("Unknown action")
                        }

                        actions.add(
                            FileAction(
                                type=type,
                                sourcePath=actionObj.getString("source"),
                                destinationPath=
                                if(actionObj.has("destination"))
                                    actionObj.getString("destination")
                                else null
                            )
                        )

                    }

                }

                AIProtocol.Response(

                    AIResponse(

                        actionable=actionable,

                        message=
                        if(obj.has("message"))
                            obj.getString("message")
                        else null,

                        actions=actions

                    )

                )

            }

            else->throw IllegalArgumentException("Unknown protocol")

        }

    }

}