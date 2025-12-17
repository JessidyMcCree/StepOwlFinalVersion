package pt.iade.games.stepowl.Components

import pt.iade.games.stepowl.Data.Rarity

// objeto singleton que serve de 'base de dados' estática para as missões possíveis.
// numa app real isto viria de uma api, mas aqui hardcodamos com amor.
object Quests {

    // template interno apenas para facilitar a criação da lista abaixo
    data class QuestTemplate(val description: String, val steps: Int, val itemName: String, val rarity: Rarity)

    // a pool de onde o rng (random number generator) vai pescar as missões.
    // ajusta-se aqui para balancear a economia do jogo
    val pool = listOf(
        // tier comum
        QuestTemplate("Daily Stroll", 50, "Dandelion", Rarity.COMMON),
        QuestTemplate("Find the Herbs!", 50, "White Lily", Rarity.COMMON),//White Lily
        QuestTemplate("A walk in the Park!!", 50, "Sunflower", Rarity.COMMON),//


        // tier incomum
       // QuestTemplate("Urban Explorer", 100, "Mapa Velho", Rarity.UNCOMMON),
       // QuestTemplate("Lets go Shopping!", 100, "Recibo", Rarity.UNCOMMON),
       // QuestTemplate("Go up the hill!", 100, "Bota Suja", Rarity.UNCOMMON),

        // tier raro
        QuestTemplate("Go Mining!", 500, "Crystal Purple", Rarity.RARE),
        QuestTemplate("Pick some Flowers!", 500, "Rose", Rarity.RARE),
        QuestTemplate("Lets go fishing!", 500, "Pearl", Rarity.RARE),


        // tier lendário
        QuestTemplate("Rob the Fairy!", 1000, "Fairy Dust", Rarity.LEGENDARY),
        QuestTemplate("THE NELIO QUEST! Get the gold Dust!", 10000, "Gold Dust", Rarity.LEGENDARY)
    )
}