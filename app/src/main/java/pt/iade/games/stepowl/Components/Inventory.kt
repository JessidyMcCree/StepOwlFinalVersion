package pt.iade.games.stepowl.Data

data class ItemPayload(
    val itemId: Int,
    val quantity: Int
)

data class InventoryPayload(
    val playerId: String,
    val items: List<ItemPayload>
)