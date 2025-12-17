package pt.iade.games.stepowl

import android.Manifest
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.kittinunf.fuel.httpPost
import com.google.gson.Gson
import pt.iade.games.stepowl.Data.InventoryPayload
import pt.iade.games.stepowl.Data.ItemPayload
import pt.iade.games.stepowl.Data.QuestData
import pt.iade.games.stepowl.ui.theme.StepOwlTheme
import kotlin.text.isNotBlank

// a activity principal. implementa sensoreventlistener porque somos 'old school'
// e gostamos de lidar com sensores manualmente.
class MainActivity : ComponentActivity(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var stepSensor: Sensor? = null

    // injeção do viewmodel usando o delegate 'by viewmodels'
    private val viewModel: GameViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // inicialização dos serviços de sensores
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

        setContent {
            // launcher para pedir permissão de contar passos. sem isto, a app não faz nada.
            val sensorsPermissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted ->
                    if (isGranted) startStepCounting()
                }
            )

            // gestão básica de navegação entre ecrãs (state hoisting)
            var currentScreen by remember { mutableStateOf("home") }

            // efeito lançado uma vez para pedir permissões no arranque
            LaunchedEffect(Unit) {
                sensorsPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
            }

            StepOwlTheme {
                // switch simples de ecrãs
                if (currentScreen == "home") {
                    MainGameScreen(
                        viewModel = viewModel,
                        onNavigateToNextPage = { currentScreen = "other" }
                    )
                } else {
                    // ecrã placeholder, para o que quisere por aqui
                    OtherScreen(onBack = { currentScreen = "home" })
                }
            }
        }
    }

    // liga o 'ouvido' do sensor
    private fun startStepCounting() {
        if (stepSensor == null) return
        sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
    }

    // poupa bateria desligando o sensor quando a app pausa (embora o step counter conte em background por hardware)
    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    // callback do sensor. cada passo conta!
    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_STEP_COUNTER) {
            viewModel.updateSteps(event.values[0])
        }
    }

    // não precisamos disto, mas a interface obriga a implementar
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // Connect App to database to comunicate with unity
    fun sendInventoryToServer(playerId: String, items: List<ItemPayload>) {
        if (items.isEmpty()) return
        val json = Gson().toJson( InventoryPayload(playerId, items))
        "https://stepowlfinalversion.onrender.com/addToInventory/add"
            .httpPost()
            .body (body = json)
            .header( "Content-Type" to "application/json")
            .response { _, _, result ->
              result.fold(
                success = { data -> Log.i(  "INVENTORY",  "Server response: ${String (bytes = data)}") },
                failure = { error -> Log.e( "INVENTORY",  "Error: ${error.message}") }
            )
        }
    }
}

// --- paleta de cores ---
// cores 'hardcoded' baseadas no teu desenho fofinho
val ColorStepsBg = Color(0xFFE8D485) // amarelo torrado
val ColorQuestBg = Color(0xFF6B9AC4) // azul acinzentado
val ColorQuestCard = Color(0xFFFFF8E1) // creme suave
val ColorInvBg = Color(0xFFEFAFAF) // rosa salmão
val ColorMascotBg = Color(0xFFA58AC4) // roxo místico
val ColorTextHand = Color(0xFF5D4037) // castanho 'lápis de cera'

@Composable
fun MainGameScreen(
    viewModel: GameViewModel,
    onNavigateToNextPage: () -> Unit
) {
    val state = viewModel.gameState
    val currentSteps = viewModel.currentSensorSteps
    val context = LocalContext.current

    var showIdDialog by remember { mutableStateOf(false) }
    var unityIdInput by remember { mutableStateOf("") }

// Se o diálogo estiver aberto, mostra o AlertDialog
    if (showIdDialog) {
        AlertDialog(
            onDismissRequest = { showIdDialog = false },
            title = { Text("Insere o teu ID do Unity") },
            text = {
                OutlinedTextField(
                    value = unityIdInput,
                    onValueChange = { unityIdInput = it },
                    label = { Text("Unity ID") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    if (unityIdInput.isNotBlank()) {
                        // Converte os itens do inventário para o formato ItemPayload
                        val itemsPayload = state.inventoryData.items.map {item ->
                            ItemPayload( item.id.toInt() ,item.quantity)
                        }

                        // 2. Envia os dados para o servidor
                        (context as? MainActivity)?.sendInventoryToServer(unityIdInput, itemsPayload)

                        // 3. Limpa o inventário na app
                        viewModel.clearInventory()

                        // 4. Fecha o diálogo e avisa o utilizador que tudo correu bem
                        showIdDialog = false
                        Toast.makeText(context, "Synchronised inventory!", Toast.LENGTH_LONG).show()

                    } else {
                        Toast.makeText(context, "Please insert an ID", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Enviar")
                }
            },
            dismissButton = {
                Button(onClick = { showIdDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE6E6FA)) // fundo lilás para dar ambiente
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // secção do topo: passos e botão de navegação
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // display de passos
            Box(
                modifier = Modifier
                    .weight(0.7f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(ColorStepsBg)
                    .border(2.dp, ColorTextHand, RoundedCornerShape(10.dp))
                    .padding(16.dp)
            ) {
                Column {
                    Text("Steps", color = ColorTextHand, fontWeight = FontWeight.Bold)
                    // lógica de visualização: se há quest, mostra progresso relativo. se não, mostra 0.
                    val stepsToShow = if(state.activeQuest != null) {
                        (currentSteps - state.stepsAtQuestStart).toInt().coerceAtLeast(0)
                    } else {
                        0
                    }
                    Text("$stepsToShow", fontSize = 24.sp, color = ColorTextHand)
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // botão 'go >' (antigo level display)
            Box(
                modifier = Modifier
                    .weight(0.3f)
                    .height(80.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFFFF8A65))
                    .border(2.dp, ColorTextHand, RoundedCornerShape(10.dp))
                    .clickable { showIdDialog = true }, // Ação alterada aqui!
                contentAlignment = Alignment.Center
            ) {
                Text("Sync", textAlign = TextAlign.Center, color = ColorTextHand, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
            // --- FIM DA ALTERAÇÃO DO BOTÃO ---
        }

        Spacer(modifier = Modifier.height(20.dp))

        // secção do meio: display das quests
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f) // ocupa o espaço que sobrar no meio
                .clip(RoundedCornerShape(20.dp))
                .background(ColorQuestBg)
                .border(3.dp, ColorTextHand, RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Quests", fontSize = 28.sp, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))

                if (state.activeQuest != null) {
                    // se temos uma quest ativa, mostramos o cartão de progresso
                    ActiveQuestCard(
                        quest = state.activeQuest,
                        currentSensorSteps = currentSteps,
                        startSensorSteps = state.stepsAtQuestStart,
                        onClaim = {
                            // verifica se cabe no inventário (considerando stacking)
                            val isStackable = state.inventoryData.items.any { it.name == state.activeQuest!!.rewardItem.name }
                            if(state.inventoryData.items.size >= 10 && !isStackable) {
                                Toast.makeText(context, "Inventory Full! Delete items first.", Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.claimReward()
                            }
                        },
                        onCancel = {
                            viewModel.cancelQuest()
                        },
                        onDebugFinish = {
                            viewModel.debugForceCompleteQuest()
                        }
                    )
                } else {
                    // se não, mostramos o menu de escolha
                    if (state.inventoryData.items.size >= 10) {
                        Text("Inventory Full! (Only stackable items accepted)", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.availableQuests.forEach { quest ->
                            QuestSelectionCard(quest = quest, onClick = {
                                val isStackable = state.inventoryData.items.any { it.name == quest.rewardItem.name }

                                // lógica de validação de espaço
                                if (state.inventoryData.items.size < 10 || isStackable) {
                                    viewModel.selectQuest(quest)
                                } else {
                                    Toast.makeText(context, "Inventory is Full!", Toast.LENGTH_SHORT).show()
                                }
                            })
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // secção inferior: inventário e mascote
        Row(modifier = Modifier.height(250.dp)) {
            // grelha de inventário
            Box(
                modifier = Modifier
                    .weight(0.5f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(15.dp))
                    .background(ColorInvBg)
                    .border(2.dp, ColorTextHand, RoundedCornerShape(15.dp))
                    .padding(10.dp)
            ) {
                Column {
                    Text("Inv (${state.inventoryData.items.size}/10)", color = ColorTextHand, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(5.dp))

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // itens reais
                        items(state.inventoryData.items) { item ->
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(Color.White, RoundedCornerShape(4.dp))
                                    .border(1.dp, ColorTextHand, RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                // usamos a primeira letra como ícone, porque design gráfico é difícil
                                Text(item.name.take(1), fontWeight = FontWeight.Bold, color = ColorTextHand)

                                // badge de quantidade (stack)
                                if (item.quantity > 1) {
                                    Text(
                                        text = "x${item.quantity}",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ColorTextHand,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(2.dp)
                                    )
                                }
                            }
                        }
                        // slots vazios para encher a grelha
                        items(10 - state.inventoryData.items.size) {
                            Box(
                                modifier = Modifier
                                    .aspectRatio(1f)
                                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                                    .border(1.dp, ColorTextHand.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // área da mascote 'nok nok'
            var isMascotHappy by remember { mutableStateOf(false) }

            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50.dp, 50.dp, 20.dp, 20.dp))
                    .background(ColorMascotBg)
                    .border(2.dp, ColorTextHand, RoundedCornerShape(50.dp, 50.dp, 20.dp, 20.dp))
                    .clickable {
                        // interação de toque simples
                        isMascotHappy = !isMascotHappy
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Text(
                        text = "Nok Nok",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )

                    // alterna entre sprites baseado no estado
                    val imageRes = if (isMascotHappy) R.drawable.mascot_normal else R.drawable.mascot_happy

                    Image(
                        painter = painterResource(id = imageRes),
                        contentDescription = "Mascot Owl",
                        modifier = Modifier.fillMaxSize(0.8f)
                    )
                }
            }
        }
    }
}

// ecrã de teste para navegação
@Composable
fun OtherScreen(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE0E0E0)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Outra Página",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "(Conteúdo a ser desenvolvido por outra pessoa)",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(onClick = onBack) {
                Text("Voltar ao Jogo")
            }
        }
    }
}

// componente para exibir uma quest selecionável
@Composable
fun QuestSelectionCard(quest: QuestData, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = ColorQuestCard),
        border = BorderStroke(2.dp, ColorTextHand),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(quest.description, fontWeight = FontWeight.Bold, color = ColorTextHand)
                Text("${quest.targetSteps} steps", fontSize = 12.sp, color = Color.Gray)
            }
            Text(quest.rarity.label, fontSize = 10.sp, color = Color.Magenta)
        }
    }
}

// componente para a quest ativa, com barra de progresso e botões
@Composable
fun ActiveQuestCard(
    quest: QuestData,
    currentSensorSteps: Float,
    startSensorSteps: Float,
    onClaim: () -> Unit,
    onCancel: () -> Unit,
    onDebugFinish: () -> Unit
) {
    // matemática defensiva para evitar números negativos
    val stepsDone = (currentSensorSteps - startSensorSteps).coerceAtLeast(0f)
    val progress = (stepsDone / quest.targetSteps).coerceIn(0f, 1f)
    val isFinished = stepsDone >= quest.targetSteps

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(3.dp, if(isFinished) Color.Green else ColorTextHand),
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {

            Column(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(quest.description, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = ColorTextHand)
                Spacer(modifier = Modifier.height(10.dp))

                Text("${stepsDone.toInt()} / ${quest.targetSteps}", fontSize = 24.sp, fontWeight = FontWeight.Black)

                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = if(isFinished) Color.Green else Color(0xFFFF8A65),
                    trackColor = Color.LightGray,
                )

                Spacer(modifier = Modifier.height(15.dp))

                if (isFinished) {
                    Button(
                        onClick = onClaim,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Green)
                    ) {
                        Text("CLAIM: ${quest.rewardItem.name}", color = ColorTextHand)
                    }
                } else {
                    Text("Keep Walking...", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                }
            }

            // botões de controlo (só aparecem se a quest não estiver acabada)
            if (!isFinished) {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // botão debug, para quem tem preguiça de andar eu
                    Button(
                        onClick = onDebugFinish,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text("DEBUG", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }

                    // botão cancelar, caso a quest seja impossível ou apenas não a queira mais
                    Button(
                        onClick = onCancel,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFCDD2)),
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(36.dp)
                    ) {
                        Text("X", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}