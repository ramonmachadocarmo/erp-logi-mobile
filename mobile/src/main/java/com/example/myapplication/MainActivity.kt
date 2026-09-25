package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.myapplication.shared.AppContainer
import com.example.myapplication.shared.domain.model.RoutePlan
import com.example.myapplication.shared.domain.model.Stop
import com.example.myapplication.shared.domain.model.Vehicle
import com.example.myapplication.shared.presentation.RouteFlowController
import com.example.myapplication.shared.presentation.RouteUiState
import com.example.myapplication.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = AppContainer(this)

        setContent {
            var state by remember { mutableStateOf<RouteUiState>(RouteUiState.Restoring) }
            val controller = remember {
                RouteFlowController(container, lifecycleScope) { newState -> state = newState }
                    .also { it.start() }
            }

            MyApplicationTheme {
                RoutePlannerApp(state = state, controller = controller)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePlannerApp(state: RouteUiState, controller: RouteFlowController) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Rotas", fontWeight = FontWeight.Bold, color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary),
            )
        },
    ) { innerPadding ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (state) {
                is RouteUiState.Restoring -> LoadingView()
                is RouteUiState.LoginRequired -> LoginView(state, onSubmit = controller::login)
                is RouteUiState.NoAccess -> NoAccessView(state.userName, onLogout = controller::logout)
                is RouteUiState.VehiclePicker -> VehiclePickerView(state, onSelect = controller::selectVehicle, onLogout = controller::logout)
                is RouteUiState.NoRouteToday -> NoRouteView(state, onRefresh = controller::refresh, onChangeVehicle = controller::changeVehicle)
                is RouteUiState.RouteOptions -> RouteOptionsView(state, onSelect = controller::selectOption)
                is RouteUiState.ActiveRoute -> ActiveRouteView(
                    state = state,
                    onConfirmArrival = controller::confirmArrival,
                    onFailDelivery = controller::failDelivery,
                    onChangeVehicle = controller::changeVehicle,
                )
                is RouteUiState.RouteCompleted -> CompletedView(state, onChangeVehicle = controller::changeVehicle)
                is RouteUiState.Error -> ErrorView(state.message)
            }
        }
    }
}

@Composable
fun LoadingView() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
fun ErrorView(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = message, color = MaterialTheme.colorScheme.error)
    }
}

@Composable
fun LoginView(state: RouteUiState.LoginRequired, onSubmit: (String, String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(text = "Entrar", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Mesmo login do ERP — seu perfil precisa ter acesso a Rotas e Entregas.",
            fontSize = 13.sp,
            color = Color.Gray,
        )
        Spacer(modifier = Modifier.height(24.dp))
        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-mail") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
            singleLine = true,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Senha") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
        )
        state.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = { onSubmit(email.trim(), password) },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.busy && email.isNotBlank() && password.isNotBlank(),
        ) {
            Text(text = if (state.busy) "Entrando..." else "Entrar")
        }
    }
}

@Composable
fun NoAccessView(userName: String, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(text = "Sem acesso", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "$userName, seu perfil não tem acesso a Rotas e Entregas. Fale com o administrador.")
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("Sair") }
    }
}

@Composable
fun VehiclePickerView(state: RouteUiState.VehiclePicker, onSelect: (Vehicle) -> Unit, onLogout: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Olá, ${state.userName}", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(text = "Escolha o veículo de hoje:", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        if (state.busy) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        state.error?.let { error ->
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.vehicles.size) { i ->
                val vehicle = state.vehicles[i]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = vehicle.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                        Button(onClick = { onSelect(vehicle) }) { Text("Selecionar") }
                    }
                }
            }
        }
        TextButton(onClick = onLogout) { Text("Sair") }
    }
}

@Composable
fun NoRouteView(state: RouteUiState.NoRouteToday, onRefresh: () -> Unit, onChangeVehicle: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(text = "Nenhuma rota hoje", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Não há rota planejada para ${state.vehicle.label} hoje.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth()) { Text("Atualizar") }
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedButton(onClick = onChangeVehicle, modifier = Modifier.fillMaxWidth()) { Text("Trocar de veículo") }
    }
}

@Composable
fun RouteOptionsView(state: RouteUiState.RouteOptions, onSelect: (com.example.myapplication.shared.domain.model.RouteOption) -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "Escolha a rota", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text(text = "${state.plan.stops.size} paradas — selecione uma opção:", fontSize = 14.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        if (state.busy) {
            Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(state.plan.options.size) { i ->
                val option = state.plan.options[i]
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(text = option.label, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(text = "⏱ ${(option.durationS / 60).toInt()} min   •   📏 ${"%.1f".format(option.distanceM / 1000)} km   •   ${option.stops.size} paradas")
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { onSelect(option) }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
                            Text("Usar esta rota")
                        }
                    }
                }
            }
        }
    }
}

private fun cachedAtLabel(millis: Long?): String =
    if (millis == null) "" else java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(java.util.Date(millis))

@Composable
fun ActiveRouteView(
    state: RouteUiState.ActiveRoute,
    onConfirmArrival: () -> Unit,
    onFailDelivery: (String) -> Unit,
    onChangeVehicle: () -> Unit,
) {
    val plan: RoutePlan = state.plan
    var failNote by remember { mutableStateOf("") }
    var showFailField by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(text = "${plan.vehicleCode} ${plan.vehicleName}".trim(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(text = "Total: ${plan.totalTimeMinutes} min • ${"%.1f".format(plan.totalDistanceKm)} km")
            }
        }
        if (plan.isFromCache) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Sem conexão — exibindo rota salva às ${cachedAtLabel(plan.cachedAtMillis)}",
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 13.sp,
            )
        }
        if (plan.pendingSyncCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${plan.pendingSyncCount} envio(s) aguardando conexão — serão enviados automaticamente",
                color = MaterialTheme.colorScheme.tertiary,
                fontSize = 13.sp,
            )
        }
        if (plan.rejectedSyncCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${plan.rejectedSyncCount} envio(s) recusado(s) pelo servidor — verifique com a central",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
            )
        }
        state.error?.let { error ->
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = error, color = MaterialTheme.colorScheme.error)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Paradas restantes (${plan.stops.size}):", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(plan.stops) { index, stop: Stop ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "${index + 1}. ${stop.customerName}${if (index == 0) "  🎯" else ""}",
                            fontWeight = FontWeight.Bold,
                        )
                        Text(text = stop.address.formatted(), fontSize = 13.sp, color = Color.Gray)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (showFailField) {
            OutlinedTextField(
                value = failNote,
                onValueChange = { failNote = it },
                label = { Text("Motivo da falha") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onFailDelivery(failNote); showFailField = false; failNote = "" },
                    enabled = !state.busy,
                ) { Text("Registrar falha") }
                OutlinedButton(onClick = { showFailField = false }) { Text("Cancelar") }
            }
        } else {
            Button(onClick = onConfirmArrival, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && plan.stops.isNotEmpty()) {
                Text(if (state.busy) "Confirmando..." else "✅ Confirmar chegada")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = { showFailField = true }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy && plan.stops.isNotEmpty()) {
                Text("Não consegui entregar")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onChangeVehicle) { Text("Trocar de veículo") }
    }
}

@Composable
fun CompletedView(state: RouteUiState.RouteCompleted, onChangeVehicle: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(text = "🎉 Rota concluída!", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "Todas as entregas de ${state.vehicle.label} foram finalizadas.")
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onChangeVehicle, modifier = Modifier.fillMaxWidth()) { Text("Trocar de veículo") }
    }
}
