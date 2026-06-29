package com.aviansh.aifilemanager.domain.ai

import com.aviansh.aifilemanager.domain.data.AIResponse
import com.aviansh.aifilemanager.domain.data.ChatLmMessage
import com.aviansh.aifilemanager.domain.data.ChatLmRole
import com.aviansh.aifilemanager.domain.engines.PythonEngine
import com.aviansh.aifilemanager.domain.engines.ToyboxEngine


class AIEngine(
    private val llm: LLMProvider
) {

    val interaction = arrayListOf<ChatLmMessage>()

    fun startInteraction(prompt:String): AIResponse{

        interaction.clear()

        interaction.add(

            ChatLmMessage(
                ChatLmRole.SYSTEM,
                SystemPrompt.prompt
            )

        )

        interaction.add(

            ChatLmMessage(
                ChatLmRole.USER,
                prompt
            )

        )

        while(true){

            val assistant=askLLM()

            interaction.add(assistant)

            when(val protocol=
                AIProtocolParser.parse(assistant.content)
            ){

                is AIProtocol.Response -> {

                    var response = protocol.aiResponse

                    if (!response.actionable) {

                        if(response.generatorCode!=null)
                            response = response.copy(
                                message = PythonEngine.generateMessage(response.generatorCode)
                            )
                        return response

                    }

                    if(response.generatorCode!=null){

                        val actions = PythonEngine.generateActions(

                            response.generatorCode

                        )

                        return response.copy(

                            actions = actions

                        )
                    }else{
                        return response
                    }

                }

                is AIProtocol.Request->{

                    val output=requestShellOutput(protocol.command)

                    interaction.add(

                        ChatLmMessage(

                            ChatLmRole.USER,

                            """
                            Command Output

                            $output
                            """.trimIndent()

                        )

                    )

                }

            }

        }

    }

    private fun askLLM(): ChatLmMessage{

        return llm.generate(interaction)

    }

    fun requestShellOutput(command:String):String{

        return try{

            ToyboxEngine.execute(command).getOutput()

        }catch(e:Exception){

            "Execution failed: ${e.message}"

        }

    }

}