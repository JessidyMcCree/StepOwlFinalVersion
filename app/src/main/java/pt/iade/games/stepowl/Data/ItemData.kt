package pt.iade.games.stepowl.Data

import java.util.UUID

// definimos a raridade dos itens e das quests aqui.
// usamos um sistema de 'pesos' (weights) em vez de percentagens diretas
// para facilitar o cálculo probabilístico. se a soma não der 100, a matemática não explode.
enum class Rarity(val weight: Double, val label: String) {
    COMMON(30.0, "Comum"),
    UNCOMMON(15.0, "Incomum"),
    RARE(5.0, "Raro"),
    LEGENDARY(0.5, "Lendário")
}

// estrutura de dados que representa um item no jogo.
// mantemos isto simples (data class) para o kotlin tratar dos equals/hashcode por nós.
data class ItemData(
    // gera um id único automaticamente
    val id: Int,
    val name: String,
    val rarity: Rarity,
    // quantidade do item no inventário.
    // começamos com 1, mas permitimos empilhar
    var quantity: Int = 1
)

enum class Items(val itemName: String, val id: Int){
    Dandelion("Dandelion", 8),
    Graveto("Graveto", 1),
    Migalha("Migalha", 2),
    Folha("Folha", 3),
    MapaVelho("Mapa Velho", 4),
    Recibo("Recibo", 5),
    BotaSuja("Bota Suja", 6),
    AmuletoDePrata("Amuleto de Prata", 7),
    Osso("Osso", 0),
    CoroaDourada("Coroa Dourada", 9)
}