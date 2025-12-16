package pt.iade.games.stepowl.Data

import com.google.gson.annotations.SerializedName

data class ItemPayload(
    // Diz ao Gson que, ao converter para JSON, este campo deve chamar-se "itemId"
    @SerializedName("itemId") val id: String,

    val quantity: Int
)

data class InventoryPayload(
    val playerId: String,
    val items: List<ItemPayload>
)