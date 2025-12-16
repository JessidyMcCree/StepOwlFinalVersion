package pt.iade.games.stepowl

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import pt.iade.games.stepowl.Components.Quests
import pt.iade.games.stepowl.Data.*
import kotlin.random.Random

// estado global da ui. agrupamos tudo numa data class para garantir
// que a ui reage atomicamente a mudanças de estado (single source of truth).
data class GameState(
    val inventoryData: InventoryData = InventoryData(),
    val activeQuest: QuestData? = null,
    val availableQuests: List<QuestData> = emptyList(),
    // guardamos quantos passos o sensor tinha quando a quest começou.
    // a matemática é: passos_atuais - passos_inicio = progresso.
    val stepsAtQuestStart: Float = 0f
)

class GameViewModel(application: Application) : AndroidViewModel(application) {

    // expomos o estado como mutável para o compose, mas o set é privado
    // para ninguém fazer asneiras fora desta classe.
    var gameState by mutableStateOf(GameState())
        private set

    // passos 'raw' do sensor desde o último boot do telemóvel
    var currentSensorSteps by mutableStateOf(0f)

    // offset para lidar com o facto do sensor não reiniciar quando abrimos a app
    private var initialSessionSteps = -1f

    // persistência simples usando sharedpreferences.
    // room seria overkill para guardar meia dúzia de jsons.
    private val prefs: SharedPreferences = application.getSharedPreferences("StepOwlPrefs", Context.MODE_PRIVATE)

    init {
        // tentamos carregar o jogo salvo assim que o viewmodel nasce
        loadGame()

        // se o jogador estiver 'seco' de missões, geramos novas para ele não ficar a olhar para o vazio
        if (gameState.availableQuests.isEmpty() && gameState.activeQuest == null) {
            generateNewQuests()
        }
    }

    // chamado sempre que o sensor de hardware deteta movimento
    fun updateSteps(sensorValue: Float) {
        // calibração inicial da sessão
        if (initialSessionSteps < 0) {
            initialSessionSteps = sensorValue
        }
        currentSensorSteps = sensorValue

        // verifica se os passos dados foram suficientes para completar a missão
        checkQuestProgress(sensorValue)
    }

    // lógica central de progresso
    private fun checkQuestProgress(currentTotalSensorValue: Float) {
        val active = gameState.activeQuest ?: return

        // cálculo do delta de passos
        val stepsDoneInQuest = currentTotalSensorValue - gameState.stepsAtQuestStart

        // se atingiu o alvo e ainda não estava marcada como completa...
        if (stepsDoneInQuest >= active.targetSteps && !active.isCompleted) {
            // ...marca como completa e salva imediatamente. não queremos perder progresso se a bateria morrer.
            val updatedQuest = active.copy(isCompleted = true)
            gameState = gameState.copy(activeQuest = updatedQuest)
            saveGame()
        }
    }

    // lógica de aceitar uma missão
    fun selectQuest(quest: QuestData) {
        // se o item já existe (é stackable), permitimos aceitar mesmo com slots cheios.
        val isStackable = gameState.inventoryData.items.any { it.name == quest.rewardItem.name }

        // se está cheio e não dá para empilhar, aborta a missão.
        if (gameState.inventoryData.items.size >= 10 && !isStackable) return

        // define a nova quest e regista o 'timestamp' dos passos atuais
        gameState = gameState.copy(
            activeQuest = quest,
            availableQuests = emptyList(), // limpa as outras opções para não haver indecisões
            stepsAtQuestStart = currentSensorSteps
        )
        saveGame()
    }

    // permite ao utilizador desistir se a missão for demasiado difícil (ou chata)
    fun cancelQuest() {
        if (gameState.activeQuest != null) {
            gameState = gameState.copy(
                activeQuest = null,
                stepsAtQuestStart = 0f
            )
            // gera novas opções para o utilizador não ficar triste
            generateNewQuests()
            saveGame()
        }
    }

    // função de batota para testes. não digam ao cliente que isto existe.
    fun debugForceCompleteQuest() {
        val active = gameState.activeQuest ?: return

        // engana a matemática recuando o ponto de partida
        val newStartSteps = currentSensorSteps - active.targetSteps
        val completedQuest = active.copy(isCompleted = true)

        gameState = gameState.copy(
            stepsAtQuestStart = newStartSteps,
            activeQuest = completedQuest
        )
        saveGame()
    }

    // lógica de reclamar a recompensa
    fun claimReward() {
        val active = gameState.activeQuest ?: return
        if (!active.isCompleted) return

        val rewardName = active.rewardItem.name

        // procura se já temos este item no inventário
        val existingItemIndex = gameState.inventoryData.items.indexOfFirst { it.name == rewardName }

        if (existingItemIndex != -1) {
            // caso de sucesso: já existe, vamos só incrementar o contador.
            // kotlin lists são imutáveis, por isso temos de criar uma cópia mutável
            val currentList = gameState.inventoryData.items.toMutableList()
            val existingItem = currentList[existingItemIndex]

            // atualiza a quantidade (+1)
            currentList[existingItemIndex] = existingItem.copy(quantity = existingItem.quantity + 1)

            gameState = gameState.copy(
                inventoryData = InventoryData(currentList),
                activeQuest = null
            )
            generateNewQuests()
            saveGame()

        } else {
            // caso normal: item novo. verifica se há espaço físico no inventário virtual
            if (gameState.inventoryData.items.size < 10) {
                val newItem = active.rewardItem
                newItem.quantity = 1 // garante que começa com 1

                val newInventoryList = gameState.inventoryData.items + newItem

                gameState = gameState.copy(
                    inventoryData = InventoryData(newInventoryList),
                    activeQuest = null
                )
                generateNewQuests()
                saveGame()
            }
        }
    }

    // gera 3 novas missões baseadas nas probabilidades
    private fun generateNewQuests() {
        val newQuests = mutableListOf<QuestData>()
        repeat(3) {
            val template = pickRandomQuestTemplate()
            newQuests.add(
                QuestData(
                    description = template.description,
                    targetSteps = template.steps,
                    rewardItem = ItemData(name = template.itemName, rarity = template.rarity),
                    rarity = template.rarity
                )
            )
        }
        gameState = gameState.copy(availableQuests = newQuests)
        saveGame()
    }

    // algoritmo de seleção ponderada (weighted random selection)
    private fun pickRandomQuestTemplate(): Quests.QuestTemplate {
        val totalWeight = Quests.pool.sumOf { it.rarity.weight }
        val randomValue = Random.nextDouble() * totalWeight
        var currentSum = 0.0

        for (quest in Quests.pool) {
            currentSum += quest.rarity.weight
            if (randomValue <= currentSum) {
                return quest
            }
        }
        // fallback de segurança, caso a matemática falhe (nunca acontece, mas...)
        return Quests.pool.first()
    }

    // --- persistência manual de dados ---
    // usamos jsonobject nativo para evitar dependências externas pesadas como gson ou moshi.
    // é mais trabalhoso, mas mantém o apk leve.

    private fun saveGame() {
        try {
            val json = JSONObject()
            json.put("stepsAtQuestStart", gameState.stepsAtQuestStart)

            // serializa o inventário, incluindo a quantidade
            val invArray = JSONArray()
            gameState.inventoryData.items.forEach { item ->
                val itemObj = JSONObject()
                itemObj.put("id", item.id)
                itemObj.put("name", item.name)
                itemObj.put("rarity", item.rarity.name)
                itemObj.put("quantity", item.quantity) // importante: salvar a pilha
                invArray.put(itemObj)
            }
            json.put("inventory", invArray)

            if (gameState.activeQuest != null) {
                json.put("activeQuest", questToJson(gameState.activeQuest!!))
            }

            val questsArray = JSONArray()
            gameState.availableQuests.forEach { quest ->
                questsArray.put(questToJson(quest))
            }
            json.put("availableQuests", questsArray)

            // commit assíncrono com apply()
            prefs.edit().putString("GAME_STATE_V2", json.toString()).apply()
        } catch (e: Exception) {
            Log.e("GAME_SAVE", "erro crítico ao salvar o jogo", e)
        }
    }

    private fun loadGame() {
        val jsonString = prefs.getString("GAME_STATE_V2", null) ?: return
        try {
            val json = JSONObject(jsonString)
            val savedStepsAtStart = json.optDouble("stepsAtQuestStart", 0.0).toFloat()

            // deserializa o inventário
            val loadedItems = mutableListOf<ItemData>()
            val invArray = json.optJSONArray("inventory")
            if (invArray != null) {
                for (i in 0 until invArray.length()) {
                    val obj = invArray.getJSONObject(i)
                    loadedItems.add(
                        ItemData(
                            id = obj.getString("id"),
                            name = obj.getString("name"),
                            rarity = Rarity.valueOf(obj.getString("rarity")),
                            quantity = obj.optInt("quantity", 1) // recupera a quantidade ou assume 1
                        )
                    )
                }
            }

            // reconstrói a quest ativa
            var loadedActiveQuest: QuestData? = null
            if (json.has("activeQuest")) {
                loadedActiveQuest = jsonToQuest(json.getJSONObject("activeQuest"))
            }

            // reconstrói as quests disponíveis
            val loadedAvailableQuests = mutableListOf<QuestData>()
            val questsArray = json.optJSONArray("availableQuests")
            if (questsArray != null) {
                for (i in 0 until questsArray.length()) {
                    loadedAvailableQuests.add(jsonToQuest(questsArray.getJSONObject(i)))
                }
            }

            // atualiza o estado final
            gameState = GameState(
                inventoryData = InventoryData(loadedItems),
                activeQuest = loadedActiveQuest,
                availableQuests = loadedAvailableQuests,
                stepsAtQuestStart = savedStepsAtStart
            )

        } catch (e: Exception) {
            Log.e("GAME_LOAD", "erro ao carregar o save, dados possivelmente corrompidos", e)
        }
    }

    // helpers para converter objetos complexos em json
    private fun questToJson(quest: QuestData): JSONObject {
        val obj = JSONObject()
        obj.put("id", quest.id)
        obj.put("description", quest.description)
        obj.put("targetSteps", quest.targetSteps)
        obj.put("rarity", quest.rarity.name)
        obj.put("isCompleted", quest.isCompleted)

        val itemObj = JSONObject()
        itemObj.put("id", quest.rewardItem.id)
        itemObj.put("name", quest.rewardItem.name)
        itemObj.put("rarity", quest.rewardItem.rarity.name)
        // a recompensa base é sempre 1, a lógica de stack é feita no inventário
        obj.put("rewardItem", itemObj)

        return obj
    }

    private fun jsonToQuest(obj: JSONObject): QuestData {
        val itemObj = obj.getJSONObject("rewardItem")
        val reward = ItemData(
            id = itemObj.getString("id"),
            name = itemObj.getString("name"),
            rarity = Rarity.valueOf(itemObj.getString("rarity")),
            quantity = 1
        )

        return QuestData(
            id = obj.getString("id"),
            description = obj.getString("description"),
            targetSteps = obj.getInt("targetSteps"),
            rarity = Rarity.valueOf(obj.getString("rarity")),
            isCompleted = obj.getBoolean("isCompleted"),
            rewardItem = reward
        )
    }
}