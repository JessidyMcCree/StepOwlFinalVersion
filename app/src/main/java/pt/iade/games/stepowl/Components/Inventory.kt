package pt.iade.games.stepowl.Data

data class ItemPayload(val name: String, val quantity: Int)

data class InventoryPayload(
    val playerId: String,
    val items: List<ItemPayload>
)