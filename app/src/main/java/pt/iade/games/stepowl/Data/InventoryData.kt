package pt.iade.games.stepowl.Data

// wrapper para a lista de itens.
// parece redundante agora, mas se um dia quisermos adicionar slots máximos
// e porque ja estava aqui
data class InventoryData(
    // a lista imutável de itens. a gestão de adicionar/remover é feita no viewmodel
    val items: List<ItemData> = emptyList()
)