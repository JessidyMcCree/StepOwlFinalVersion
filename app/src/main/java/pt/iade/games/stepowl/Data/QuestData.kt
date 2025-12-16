package pt.iade.games.stepowl.Data

import java.util.UUID

// estrutura que define uma missão ativa ou disponível.
// encapsula tudo o que o jogador precisa de saber para suar a camisola.
data class QuestData(
    // identificador único para não misturarmos as missões
    val id: String = UUID.randomUUID().toString(),
    val description: String,
    // o objetivo a atingir. no pain no gain (ou loot)
    val targetSteps: Int,
    // o prémio que o jogador recebe a cenoura na ponta da cana
    val rewardItem: ItemData,
    val rarity: Rarity,
    // flag para controlar se a missão já foi concluída mas ainda não foi reclamada
    var isCompleted: Boolean = false
)