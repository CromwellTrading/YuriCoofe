package com.cafeteria.gestion.ui

import android.net.Uri
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cafeteria.gestion.data.*
import com.cafeteria.gestion.data.local.ProductEntity
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.FormatStyle
import java.util.Locale
import kotlin.math.max

@Composable private fun accentColor() = MaterialTheme.colorScheme.primary
@Composable private fun accentContainerColor() = MaterialTheme.colorScheme.primaryContainer
@Composable private fun successColor() = MaterialTheme.colorScheme.secondary
@Composable private fun warningColor() = MaterialTheme.colorScheme.tertiary

/** Exact CUP aggregation: overflow is surfaced instead of wrapping into a false total. */
private fun sumCupExactly(sales: Iterable<SaleSummary>): Long? = try {
    sales.fold(0L) { total, sale -> Math.addExact(total, sale.totalCUP) }
} catch (_: ArithmeticException) {
    null
}

private fun exactCupLabel(serverTotal: Long?, localTotal: Long?): String =
    (serverTotal ?: localTotal)?.let(::formatCup) ?: "Fuera de rango"

@Composable
fun CafeteriaApp(viewModel: CafeteriaViewModel) {
    val state by viewModel.state.collectAsState()
    val systemDark = isSystemInDarkTheme()
    val dark = when (state.appearance) {
        "DARK" -> true
        "LIGHT" -> false
        else -> systemDark
    }
    val coffeePalette = state.palette == "COFFEE"
    val primary = when (state.palette) {
        "COFFEE" -> if (dark) Color(0xFFD7AD83) else Color(0xFF70472F)
        "GREEN" -> Color(0xFF23815E)
        "BLUE" -> Color(0xFF3267B7)
        "ORANGE" -> Color(0xFFC16A2B)
        "ROSE" -> Color(0xFFB54B78)
        else -> Color(0xFF6250D8)
    }
    val primaryContainer = when (state.palette) {
        "COFFEE" -> if (dark) Color(0xFF4B3426) else Color(0xFFEAD9C7)
        "GREEN" -> if (dark) Color(0xFF244C3D) else Color(0xFFDDF3E9)
        "BLUE" -> if (dark) Color(0xFF263F66) else Color(0xFFE1EBFF)
        "ORANGE" -> if (dark) Color(0xFF5C3D27) else Color(0xFFFFE9D8)
        "ROSE" -> if (dark) Color(0xFF5A3043) else Color(0xFFFFE2EC)
        else -> if (dark) Color(0xFF40366D) else Color(0xFFECE8FF)
    }
    val scheme = if (coffeePalette && dark) {
        darkColorScheme(
            primary = Color(0xFFD7AD83), onPrimary = Color(0xFF2B1D14),
            primaryContainer = Color(0xFF4B3426), onPrimaryContainer = Color(0xFFF5E6D7),
            background = Color(0xFF17120F), surface = Color(0xFF241C17),
            onSurface = Color(0xFFF6ECE3), surfaceVariant = Color(0xFF352920),
            onSurfaceVariant = Color(0xFFCBB9A8), secondary = Color(0xFFD6A779),
            tertiary = Color(0xFFC78D61), error = Color(0xFFFFB4AB)
        )
    } else if (coffeePalette) {
        lightColorScheme(
            primary = Color(0xFF70472F), onPrimary = Color(0xFFFFF8F1),
            primaryContainer = Color(0xFFEAD9C7), onPrimaryContainer = Color(0xFF3D291D),
            background = Color(0xFFF6F0E8), surface = Color(0xFFFFFCF8),
            onSurface = Color(0xFF30251F), surfaceVariant = Color(0xFFEDE3D8),
            onSurfaceVariant = Color(0xFF79695D), secondary = Color(0xFFA87443),
            tertiary = Color(0xFF8E6448), error = Color(0xFFB3261E)
        )
    } else if (dark) {
        darkColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = Color(0xFFF6F1FF),
            background = Color(0xFF111119),
            surface = Color(0xFF1C1B25),
            onSurface = Color(0xFFF2F0F8),
            surfaceVariant = Color(0xFF2A2935),
            onSurfaceVariant = Color(0xFFBDB9CB),
            secondary = Color(0xFF78C5A5),
            error = Color(0xFFFFB4AB)
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = Color.White,
            primaryContainer = primaryContainer,
            onPrimaryContainer = Color(0xFF302568),
            background = Color(0xFFF8F7FC),
            surface = Color.White,
            onSurface = Color(0xFF232235),
            surfaceVariant = Color(0xFFF0EEF5),
            onSurfaceVariant = Color(0xFF777589),
            secondary = Color(0xFF3E8C73),
            error = Color(0xFFB3261E)
        )
    }
    MaterialTheme(colorScheme = scheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = scheme.background) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = scheme.primary) }
                else -> MainShell(state, viewModel)
            }
        }
    }
}

@Composable
private fun CustomerAuthScreen(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    var registerMode by remember { mutableStateOf(false) }
    var nameOrUser by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showStationSetup by remember { mutableStateOf(false) }
    var stationCode by remember { mutableStateOf("") }
    val colors = MaterialTheme.colorScheme

    Box(Modifier.fillMaxSize().background(colors.background)) {
        Box(
            Modifier.fillMaxWidth().height(310.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF2C1B13), Color(0xFF65432D), Color(0xFF9A704C))))
        )
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier.size(76.dp).clip(RoundedCornerShape(26.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Storefront, null, tint = Color(0xFFFFE8CF), modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.height(14.dp))
            Text("YuriCoofe", color = Color(0xFFFFF4E7), fontSize = 31.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.2.sp)
            Spacer(Modifier.height(7.dp))
            Text("Una pausa rica empieza aquí.", color = Color(0xFFE8D4C0), fontSize = 14.sp)
            Spacer(Modifier.height(30.dp))

            Card(
                modifier = Modifier.fillMaxWidth().animateContentSize(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.45f))
            ) {
                Column(Modifier.fillMaxWidth().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        if (registerMode) "Crea tu cuenta" else "Bienvenido",
                        fontSize = 25.sp, fontWeight = FontWeight.Bold, color = colors.onSurface
                    )
                    Text(
                        if (registerMode) "Regístrate con tu nombre y una contraseña para guardar tus preferencias y consultar la cafetería."
                        else "Entra con tu nombre o usuario y descubre lo que tenemos preparado.",
                        color = colors.onSurfaceVariant, fontSize = 13.sp, lineHeight = 19.sp
                    )
                    OutlinedTextField(
                        value = nameOrUser, onValueChange = { nameOrUser = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text(if (registerMode) "Tu nombre" else "Nombre o usuario") },
                        placeholder = { Text(if (registerMode) "Ej. María Rodríguez" else "Escribe tu nombre o usuario") },
                        singleLine = true, shape = RoundedCornerShape(17.dp)
                    )
                    OutlinedTextField(
                        value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                        label = { Text("Contraseña") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(17.dp)
                    )
                    if (registerMode) {
                        OutlinedTextField(
                            value = confirmPassword, onValueChange = { confirmPassword = it }, modifier = Modifier.fillMaxWidth(),
                            label = { Text("Repite la contraseña") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            shape = RoundedCornerShape(17.dp)
                        )
                        Text(
                            "Usa al menos 10 caracteres. Si no tienes internet, tu cuenta quedará guardada en este teléfono y se sincronizará al conectarte.",
                            color = colors.onSurfaceVariant, fontSize = 11.sp, lineHeight = 16.sp
                        )
                    }
                    if (!state.apiConfigured) {
                        InfoPanel(
                            title = "Modo local",
                            message = "El acceso del equipo requiere conexión con el servicio. Las cuentas nuevas de clientes pueden guardarse en este teléfono sin conexión.",
                            type = PanelType.INFO
                        )
                    } else if (!state.online && !registerMode) {
                        InfoPanel(
                            title = "Sin conexión",
                            message = "Podrás entrar con una cuenta de cliente guardada previamente en este teléfono. El acceso del equipo necesita validar sus credenciales con el servidor.",
                            type = PanelType.INFO
                        )
                    }
                    if (!state.error.isNullOrBlank()) InfoPanel("No se pudo continuar", state.error, PanelType.ERROR)
                    if (!state.message.isNullOrBlank()) InfoPanel("Información", state.message, PanelType.SUCCESS)
                    Button(
                        onClick = {
                            if (registerMode) {
                                when {
                                    password.length < 10 -> vm.showError("La contraseña debe tener al menos 10 caracteres.")
                                    password != confirmPassword -> vm.showError("Las contraseñas no coinciden.")
                                    else -> vm.registerCustomer(nameOrUser, password)
                                }
                            } else vm.login(nameOrUser, password)
                        },
                        enabled = !state.busy && nameOrUser.trim().isNotEmpty() && password.isNotEmpty() && (!registerMode || confirmPassword.isNotEmpty()),
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(18.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 5.dp, pressedElevation = 1.dp)
                    ) {
                        if (state.busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                        else Text(if (registerMode) "Crear cuenta" else "Entrar", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    }
                    TextButton(onClick = {
                        registerMode = !registerMode
                        password = ""
                        confirmPassword = ""
                        vm.clearMessage()
                    }, modifier = Modifier.fillMaxWidth(), enabled = !state.busy) {
                        Text(if (registerMode) "Ya tengo una cuenta · Iniciar sesión" else "¿Primera vez aquí? Crear cuenta", color = colors.primary)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfaceVariant)
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Estación local de la cafetería", fontWeight = FontWeight.SemiBold)
                    Text("Este modo solo se activa con un código generado por Yuri. No es una cuenta de trabajadora ni permite administrar o vender.", fontSize = 12.sp, color = colors.onSurfaceVariant)
                    TextButton(onClick = { showStationSetup = !showStationSetup }) {
                        Text(if (showStationSetup) "Ocultar configuración" else "Configurar este dispositivo como estación")
                    }
                    if (showStationSetup) {
                        OutlinedTextField(
                            value = stationCode, onValueChange = { stationCode = it.trim() },
                            label = { Text("Código de vinculación de Yuri") },
                            placeholder = { Text("YCST1|...") }, singleLine = true,
                            modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
                        )
                        Button(
                            onClick = { vm.activateStation(stationCode) },
                            enabled = !state.busy && stationCode.startsWith("YCST1|") && stationCode.length > 40,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Activar estación local") }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Un solo acceso para todos.", color = colors.onSurfaceVariant, fontSize = 12.sp)
        }
    }
}

private data class NavEntry(val screen: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    val session = state.session
    if (session == null) {
        CustomerAuthScreen(state, vm)
        return
    }
    if (session.role == Roles.LOCAL_STATION) {
        StationScreen(state, vm)
        return
    }
    val nav = when (session.role) {
        Roles.OWNER -> listOf(
            NavEntry(SCREEN_HOME, "Inicio", Icons.Outlined.Home),
            NavEntry(SCREEN_PRODUCTS, "Inventario", Icons.Outlined.Inventory2),
            NavEntry(SCREEN_SALES, "Ventas", Icons.Outlined.History),
            NavEntry(SCREEN_ANNOUNCEMENTS, "Avisos", Icons.Outlined.Campaign),
            NavEntry(SCREEN_TEAM, "Equipo", Icons.Outlined.Group)
        )
        Roles.WORKER -> listOf(
            NavEntry(SCREEN_SELL, "Vender", Icons.Outlined.PointOfSale),
            NavEntry(SCREEN_SALES, "Mis ventas", Icons.Outlined.History),
            NavEntry(SCREEN_PRODUCTS, "Productos", Icons.Outlined.Inventory2)
        )
        else -> listOf(
            NavEntry(SCREEN_CUSTOMER_CATALOG, "Catálogo", Icons.Outlined.Storefront),
            NavEntry(SCREEN_ANNOUNCEMENTS, "Avisos", Icons.Outlined.NotificationsActive)
        )
    }
    val snacks = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snacks.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(state.error) { state.error?.let { snacks.showSnackbar(it); vm.clearMessage() } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("YuriCoofe", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            when (session.role) { Roles.OWNER -> "Administración"; Roles.WORKER -> "Punto de venta"; else -> "Tu cafetería" },
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    if (state.pendingCount > 0) {
                        Badge(containerColor = warningColor(), contentColor = Color.White) { Text(state.pendingCount.toString()) }
                        Spacer(Modifier.width(3.dp))
                    }
                    IconButton(onClick = vm::syncNow) {
                        Icon(if (state.online) Icons.Outlined.CloudSync else Icons.Outlined.CloudOff, contentDescription = "Sincronizar", tint = if (state.online) accentColor() else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { vm.setScreen(SCREEN_SETTINGS) }) { Icon(Icons.Outlined.Settings, contentDescription = "Ajustes") }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                nav.forEach { entry ->
                    NavigationBarItem(
                        selected = state.screen == entry.screen,
                        onClick = { vm.setScreen(entry.screen) },
                        icon = { Icon(entry.icon, contentDescription = entry.label) },
                        label = { Text(entry.label, maxLines = 1, fontSize = 10.sp) },
                        alwaysShowLabel = true
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snacks) }
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding)) {
            if (!state.online || state.pendingCount > 0) {
                Row(
                    Modifier.fillMaxWidth().background(if (state.pendingCount > 0) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (state.pendingCount > 0) Icons.Outlined.CloudSync else Icons.Outlined.CloudOff, null, tint = if (state.pendingCount > 0) warningColor() else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.pendingCount > 0) "${state.pendingCount} operación(es) esperan sincronización o revisión. Los datos se conservan en este teléfono."
                        else "Sin conexión. Puedes consultar los datos guardados; las ventas nuevas se conservarán en este teléfono.",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f)
                    )
                    if (state.pendingCount > 0) TextButton(onClick = { vm.setScreen(if (session.role == Roles.OWNER) SCREEN_PRODUCTS else SCREEN_SALES) }) { Text("Ver", color = accentColor()) }
                }
            }
            when (state.screen) {
                SCREEN_HOME -> DashboardScreen(state, vm)
                SCREEN_PRODUCTS -> ProductsScreen(state, vm, session.role)
                SCREEN_SELL -> SellScreen(state, vm)
                SCREEN_SALES -> SalesScreen(state, vm)
                SCREEN_REPORTS -> ReportsScreen(state, vm)
                SCREEN_ANNOUNCEMENTS -> AnnouncementsScreen(state, vm)
                SCREEN_TEAM -> TeamScreen(state, vm)
                SCREEN_CUSTOMER_CATALOG -> CustomerCatalogScreen(state, vm)
                SCREEN_SETTINGS -> SettingsScreen(state, vm)
                else -> if (session.role == Roles.CUSTOMER) CustomerCatalogScreen(state, vm) else DashboardScreen(state, vm)
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    val session = state.session
    val isCustomer = session?.role == Roles.CUSTOMER
    var transferCode by remember { mutableStateOf("") }
    LaunchedEffect(session?.role) {
        if (session?.role == Roles.OWNER) vm.loadStationPairingCode()
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportTransferPackage(uri, transferCode)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importTransferPackage(uri, transferCode)
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("Ajustes", fontSize = 26.sp, fontWeight = FontWeight.Bold)
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Settings, null, tint = accentColor(), modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(session?.displayName.orEmpty(), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(when (session?.role) { Roles.OWNER -> "Administradora"; Roles.WORKER -> "Trabajadora"; else -> "Cliente" }, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                }
                if (isCustomer && session?.accessToken.isNullOrBlank()) {
                    InfoPanel("Perfil guardado en este teléfono", "Tu cuenta se validará y sincronizará con el servidor cuando haya conexión.", PanelType.INFO)
                }
                OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.Logout, null); Spacer(Modifier.width(8.dp)); Text("Cerrar sesión")
                }
            }
        }

        if (session?.role == Roles.OWNER) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Estación local", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Genera una identidad segura para el dispositivo que permanecerá en la cafetería. Si hay conexión, Yuri la registra en el servidor; sin conexión queda pendiente y se registra al recuperar internet.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    OutlinedButton(onClick = vm::createStationPairingCode, modifier = Modifier.fillMaxWidth()) {
                        Text(if (state.stationPairingCode == null) "Crear código de estación" else "Crear otra estación")
                    }
                    state.stationPairingCode?.let { code ->
                        SelectionContainer {
                            Text(code, modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)).padding(12.dp), fontSize = 12.sp)
                        }
                        Text("Comparte este código solo con el dispositivo autorizado. La clave permite cifrar y descifrar las entregas de esta cafetería. No la compartas fuera de los dispositivos autorizados.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = vm::revokeStationPairingCode, modifier = Modifier.fillMaxWidth()) { Text("Revocar esta estación") }
                    }
                    if (state.online && state.localStations.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Text("Dispositivos vinculados", fontWeight = FontWeight.Medium)
                        state.localStations.forEach { station ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(station.label, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                    Text("${if (station.status == "ACTIVE") "Activa" else "Revocada"} · última conexión: ${station.lastSeenAt ?: "sin conexión registrada"}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("Última recepción: ${station.lastReceivedAt ?: "ninguna"} · operaciones guardadas: ${station.storedOperationCount}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                if (station.status == "ACTIVE") {
                                    TextButton(onClick = { vm.revokeManagedStation(station.id) }) { Text("Revocar", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (session?.role == Roles.OWNER || session?.role == Roles.WORKER) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Transferencia offline", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Los paquetes incluyen ventas pendientes y, desde administración, cambios de productos e inventario. Se cifran antes de guardarlos como archivo. Usa el mismo código en origen y destino.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(
                        value = transferCode, onValueChange = { transferCode = it },
                        label = { Text("Código de estación / transferencia") },
                        placeholder = { Text("Pega el código YCST1 generado por Yuri") },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                    OutlinedButton(
                        onClick = { exportLauncher.launch("YuriCoofe-${System.currentTimeMillis()}.ycf") },
                        enabled = transferCode.isNotBlank(), modifier = Modifier.fillMaxWidth()
                    ) { Text("Exportar paquete cifrado") }
                    OutlinedButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        enabled = transferCode.isNotBlank(), modifier = Modifier.fillMaxWidth()
                    ) { Text("Importar paquete recibido") }
                    Text("Después puedes compartir el archivo por Bluetooth, Nearby Share, USB o cualquier medio físico disponible. Los datos originales no se borran al exportar.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.DarkMode, null, tint = accentColor())
                    Spacer(Modifier.width(8.dp))
                    Text("Apariencia", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
                Text("Elige cómo se muestra la aplicación.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                    listOf("SYSTEM" to "Sistema", "LIGHT" to "Claro", "DARK" to "Oscuro").forEach { (value, label) ->
                        FilterChip(selected = state.appearance == value, onClick = { vm.setAppearance(value) }, label = { Text(label, fontSize = 12.sp) })
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Palette, null, tint = accentColor())
                    Spacer(Modifier.width(8.dp))
                    Text("Color principal", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                }
                val palettes = listOf(
                    Triple("COFFEE", "Café", Color(0xFF70472F)),
                    Triple("PURPLE", "Violeta", Color(0xFF6250D8)),
                    Triple("GREEN", "Verde", Color(0xFF23815E)),
                    Triple("BLUE", "Azul", Color(0xFF3267B7)),
                    Triple("ORANGE", "Naranja", Color(0xFFC16A2B)),
                    Triple("ROSE", "Rosa", Color(0xFFB54B78))
                )
                palettes.forEach { (value, label, color) ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { vm.setPalette(value) }.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(22.dp).clip(CircleShape).background(color))
                        Spacer(Modifier.width(10.dp))
                        Text(label, modifier = Modifier.weight(1f))
                        RadioButton(selected = state.palette == value, onClick = { vm.setPalette(value) })
                    }
                }
            }
        }

        if (session?.role == Roles.OWNER) {
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Administración", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    OutlinedButton(onClick = { vm.setScreen(SCREEN_TEAM) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Group, null); Spacer(Modifier.width(8.dp)); Text("Gestionar trabajadoras")
                    }
                    OutlinedButton(onClick = { vm.setScreen(SCREEN_ANNOUNCEMENTS) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Campaign, null); Spacer(Modifier.width(8.dp)); Text("Ofertas y avisos")
                    }
                    OutlinedButton(onClick = { vm.retryProductChanges() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.CloudSync, null); Spacer(Modifier.width(8.dp)); Text("Reintentar cambios del catálogo")
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Conexión y sincronización", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (state.online) Icons.Outlined.CloudSync else Icons.Outlined.CloudOff, null, tint = if (state.online) successColor() else warningColor())
                    Spacer(Modifier.width(8.dp))
                    Text(if (state.online) "Con conexión de red" else "Sin conexión; los datos nuevos se guardan localmente", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text("Operaciones pendientes: ${state.pendingCount}", fontWeight = FontWeight.Medium)
                OutlinedButton(onClick = vm::syncNow, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.CloudSync, null); Spacer(Modifier.width(8.dp)); Text("Sincronizar ahora")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StationScreen(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    val stats = state.stationStats
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.importTransferPackage(uri, "")
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) vm.exportStationCollection(uri)
    }
    val snacks = remember { SnackbarHostState() }
    LaunchedEffect(state.message) { state.message?.let { snacks.showSnackbar(it); vm.clearMessage() } }
    LaunchedEffect(state.error) { state.error?.let { snacks.showSnackbar(it); vm.clearMessage() } }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("YuriCoofe · Estación", fontWeight = FontWeight.Bold)
                        Text("Bóveda local · sin acceso administrativo", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                actions = { IconButton(onClick = vm::refreshAll) { Icon(Icons.Outlined.Refresh, "Actualizar estado") } }
            )
        },
        snackbarHost = { SnackbarHost(snacks) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            InfoPanel("Estación local activa", "Este dispositivo no vende, no administra productos ni modifica el inventario oficial. Solo recibe paquetes cifrados, los conserva y permite entregarlos a otro dispositivo.", PanelType.INFO)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Paquetes", (stats?.receivedBundles ?: 0).toString(), Icons.Outlined.Inventory2, Modifier.weight(1f))
                SummaryCard("Ventas guardadas", (stats?.storedSales ?: 0).toString(), Icons.Outlined.History, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Cambios producto", (stats?.storedProductChanges ?: 0).toString(), Icons.Outlined.Edit, Modifier.weight(1f))
                SummaryCard("Movimientos stock", (stats?.storedInventoryChanges ?: 0).toString(), Icons.Outlined.Assessment, Modifier.weight(1f))
            }
            Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Recepción de datos", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Recibe el archivo cifrado que entregue una trabajadora u otro dispositivo. La estación verifica el código y el contenido antes de guardarlo.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = { importLauncher.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) { Text("Recibir paquete cifrado") }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Entrega acumulada", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                    Text("Agrupa las operaciones recibidas sin duplicar identificadores. Los paquetes originales permanecen guardados incluso después de exportar.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(
                        onClick = { exportLauncher.launch("YuriCoofe-estacion-${System.currentTimeMillis()}.ycf") },
                        enabled = (stats?.receivedBundles ?: 0) > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Exportar todo lo acumulado") }
                }
            }
            Text("Última recepción: ${stats?.latestReceipt ?: "Todavía no se ha recibido ningún paquete"}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("La estación no envía operaciones directamente al servidor. Lleva el paquete consolidado a Yuri o a un dispositivo OWNER autorizado; los cambios se validarán allí.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = vm::logout, modifier = Modifier.fillMaxWidth()) { Text("Cerrar estación en este dispositivo") }
        }
    }
}

@Composable
private fun DashboardScreen(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    val businessZone = remember(state.session?.businessTimeZone) {
        runCatching { ZoneId.of(state.session?.businessTimeZone ?: "America/Havana") }.getOrDefault(ZoneId.of("America/Havana"))
    }
    val today = LocalDate.now(businessZone).toString()
    val salesToday = state.sales.filter { sale -> runCatching { OffsetDateTime.parse(sale.soldAt).atZoneSameInstant(businessZone).toLocalDate().toString() == today }.getOrDefault(false) }
    val cash = sumCupExactly(salesToday.filter { it.paymentMethod == PaymentMethods.CASH_CUP })
    val transfer = sumCupExactly(salesToday.filter { it.paymentMethod == PaymentMethods.TRANSFER_CUP })
    val lowStock = state.products.count { !it.stockIsApproximate && !(it.saleMode == SaleModes.SUBUNIT && it.subunitsPerUnit == null && it.stockUnits == 0L) && it.stockUnits <= it.minimumStock }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Hola, ${state.session?.displayName?.substringBefore(' ') ?: ""}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Resumen del negocio · $today", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            IconButton(onClick = vm::refreshAll) { Icon(Icons.Outlined.Refresh, "Actualizar") }
        }
        if (state.session?.role == Roles.OWNER) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Efectivo CUP", if (state.dashboard != null) exactCupLabel(state.dashboard.cashCup, null) else exactCupLabel(null, cash), Icons.Outlined.PointOfSale, Modifier.weight(1f))
                SummaryCard("Transferencia", if (state.dashboard != null) exactCupLabel(state.dashboard.transferCup, null) else exactCupLabel(null, transfer), Icons.Outlined.CloudSync, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Operaciones", (state.dashboard?.saleCount ?: salesToday.size).toString(), Icons.Outlined.Assessment, Modifier.weight(1f))
                SummaryCard("Stock bajo", (state.dashboard?.lowStockCount ?: lowStock).toString(), Icons.Outlined.WarningAmber, Modifier.weight(1f), caution = lowStock > 0)
            }
            Text("Accesos rápidos", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("Añadir producto", Icons.Outlined.Add, Modifier.weight(1f)) { vm.setScreen(SCREEN_PRODUCTS) }
                QuickAction("Ver ventas", Icons.Outlined.History, Modifier.weight(1f)) { vm.setScreen(SCREEN_SALES) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QuickAction("Estadísticas", Icons.Outlined.Assessment, Modifier.weight(1f)) { vm.setScreen(SCREEN_REPORTS) }
                QuickAction("Crear aviso", Icons.Outlined.Campaign, Modifier.weight(1f)) { vm.setScreen(SCREEN_ANNOUNCEMENTS) }
            }
            Text("Últimos movimientos", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            if (salesToday.isEmpty()) EmptyPanel("Todavía no hay ventas para hoy", "Las ventas aparecerán aquí cuando se registren y/o se cargue el historial del servidor.")
            else salesToday.take(5).forEach { SaleRow(it, state.session?.businessTimeZone) }
        } else {
            Text("Tus productos", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text("Consulta lo que está disponible y revisa los cambios recientes.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Button(onClick = { vm.setScreen(if (state.session?.role == Roles.WORKER) SCREEN_SELL else SCREEN_CUSTOMER_CATALOG) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.session?.role == Roles.WORKER) "Registrar una venta" else "Ver catálogo")
            }
            if (state.pendingCount > 0) InfoPanel("Ventas pendientes", "${state.pendingCount} venta(s) están guardadas localmente y se enviarán al recuperar conexión.", PanelType.INFO)
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, caution: Boolean = false) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(if (caution) MaterialTheme.colorScheme.tertiaryContainer else accentContainerColor()), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (caution) MaterialTheme.colorScheme.onTertiaryContainer else accentColor(), modifier = Modifier.size(19.dp))
            }
            Text(title, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Icon(icon, null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, maxLines = 2)
    }
}

@Composable
private fun ProductsScreen(state: CafeteriaUiState, vm: CafeteriaViewModel, role: String) {
    var showCreate by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProductEntity?>(null) }
    var adjusting by remember { mutableStateOf<ProductEntity?>(null) }
    var counting by remember { mutableStateOf<ProductEntity?>(null) }
    var deactivating by remember { mutableStateOf<ProductEntity?>(null) }
    var renamingCategory by remember { mutableStateOf(false) }
    var selectedSale by remember { mutableStateOf<ProductEntity?>(null) }
    val owner = role == Roles.OWNER
    val activeProducts = state.products.filter { it.active }
    val categories = listOf("Todas") + activeProducts.map { it.categoryName }.filter { it.isNotBlank() }.distinct().sorted()
    val filtered = activeProducts.filter { product ->
        (state.search.isBlank() || product.name.contains(state.search, true) || product.categoryName.contains(state.search, true)) &&
            (state.category == "Todas" || product.categoryName == state.category) &&
            (owner || product.publicVisible || role == Roles.WORKER)
    }.sortedWith(when (state.sortBy) {
        "Precio" -> compareBy<ProductEntity> { it.unitPriceCup ?: it.subunitPriceCup ?: Long.MAX_VALUE }
        "Stock" -> compareBy<ProductEntity> { it.stockUnits }
        "Fecha" -> compareBy<ProductEntity> { it.updatedAt }
        else -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }.let { if (state.ascending) it else it.reversed() })

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (owner) "Inventario" else if (state.screen == SCREEN_SELL) "Registrar venta" else "Productos", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("${activeProducts.size} productos activos", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = vm::refreshProducts) { Icon(Icons.Outlined.Refresh, "Actualizar") }
            if (owner) Button(onClick = { showCreate = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Icon(Icons.Outlined.Add, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Añadir")
            }
        }
        OutlinedTextField(
            value = state.search, onValueChange = vm::setSearch, modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, label = { Text("Buscar producto") }, singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Categoría", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { category -> FilterChip(selected = state.category == category, onClick = { vm.setCategory(category) }, label = { Text(category, maxLines = 1) }) }
            }
            if (owner && categories.size > 1) {
                TextButton(onClick = { renamingCategory = true }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text("Editar", fontSize = 11.sp)
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ordenar por", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            SortSelector(state.sortBy, vm::setSort)
            IconButton(onClick = vm::toggleSortDirection) { Icon(if (state.ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, if (state.ascending) "Ascendente" else "Descendente") }
            Text(if (state.ascending) "Ascendente" else "Descendente", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
        }
        if (filtered.isEmpty()) {
            EmptyPanel(if (state.products.isEmpty()) "No hay productos cargados" else "No se encontraron productos", if (state.products.isEmpty()) "Conecta la app al servicio y actualiza el catálogo. No se han añadido artículos de demostración." else "Prueba otro nombre o categoría.")
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(filtered, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        owner = owner,
                        onClick = { if (owner) editing = product else selectedSale = product },
                        onEdit = { editing = product },
                        onAdjust = { adjusting = product },
                        onCount = { counting = product },
                        onDeactivate = { deactivating = product },
                        onSell = { selectedSale = product }
                    )
                }
            }
        }
    }
    if (showCreate) ProductEditorDialog(null, state.busy, onDismiss = { showCreate = false }, onSave = { draft -> vm.saveProduct(draft) { showCreate = false } })
    editing?.let { product -> ProductEditorDialog(product, state.busy, onDismiss = { editing = null }, onSave = { draft -> vm.saveProduct(draft) { editing = null } }) }
    adjusting?.let { product -> InventoryAdjustDialog(product, state.busy, onDismiss = { adjusting = null }, onSave = { u, s, r -> vm.adjustInventory(product.id, u, s, r) { adjusting = null } }) }
    counting?.let { product -> PhysicalCountDialog(product, state.busy, onDismiss = { counting = null }, onSave = { u, s, r -> vm.recordPhysicalCount(product.id, u, s, r) { counting = null } }) }
    if (renamingCategory) CategoryRenameDialog(
        categories = categories.drop(1), busy = state.busy,
        onDismiss = { renamingCategory = false },
        onSave = { old, new -> vm.renameCategory(old, new) { renamingCategory = false } }
    )
    deactivating?.let { product ->
        AlertDialog(
            onDismissRequest = { if (!state.busy) deactivating = null },
            title = { Text("Desactivar producto") },
            text = { Text("¿Desactivar ${product.name}? No aparecerá en ventas ni en el catálogo público. Las ventas anteriores se conservan y la baja se sincronizará cuando haya internet.") },
            confirmButton = { TextButton(enabled = !state.busy, onClick = { vm.deactivateProduct(product.id) { deactivating = null } }) { Text("Desactivar") } },
            dismissButton = { TextButton(onClick = { deactivating = null }, enabled = !state.busy) { Text("Cancelar") } }
        )
    }
    selectedSale?.let { product -> SellDialog(product, state.busy, onDismiss = { selectedSale = null }, onConfirm = { u, s, method -> vm.recordSale(product, u, s, method) { selectedSale = null } }) }
}

@Composable
private fun SortSelector(value: String, onChange: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { expanded = true }, shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) { Text(value, fontSize = 12.sp) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            listOf("Nombre", "Fecha", "Precio", "Stock").forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { onChange(item); expanded = false }) }
        }
    }
}


@Composable
private fun ProductCard(
    product: ProductEntity,
    owner: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
    onCount: () -> Unit,
    onDeactivate: () -> Unit,
    onSell: () -> Unit
) {
    val untrackedPortionOnly = product.saleMode == SaleModes.SUBUNIT && product.subunitsPerUnit == null && product.stockUnits == 0L
    val low = !product.stockIsApproximate && !untrackedPortionOnly && product.stockUnits <= product.minimumStock
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.Top) {
            ProductImage(product.imageUrl, Modifier.size(82.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(product.categoryName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                if (product.unitPriceCup != null) Text("${formatCup(product.unitPriceCup)} / ${product.unitName}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                else if (product.subunitPriceCup != null) Text("${formatCup(product.subunitPriceCup)} / ${product.subunitName ?: "subunidad"}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                product.subunitPriceCup?.let { Text("${formatCup(it)} / ${product.subunitName ?: "subunidad"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                product.subunitPriceTransfer?.let { Text("${formatCup(it)} por transferencia · ${product.subunitName ?: "subunidad"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                product.unitPriceTransfer?.let { Text("${formatCup(it)} por transferencia", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                val stockDescription = when {
                    product.stockIsApproximate -> "Disponibilidad pública · actualiza para ver existencias exactas"
                    untrackedPortionOnly -> "Venta por ${product.subunitName ?: "porciones"} · sin conteo de stock"
                    else -> stockLabel(product)
                }
                Text(stockDescription, color = if (low) warningColor() else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                if (low && owner) Text("Stock bajo", color = warningColor(), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (product.subunitsPerUnit == null && product.saleMode == SaleModes.BOTH) Text("Las subunidades no descuentan cajas sin equivalencia definida.", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (owner) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onEdit, enabled = !product.stockIsApproximate) { Icon(Icons.Outlined.Edit, "Editar producto", tint = accentColor()) }
                    IconButton(onClick = onAdjust, enabled = !product.stockIsApproximate) { Icon(Icons.Outlined.Add, "Ajustar inventario", tint = successColor()) }
                    IconButton(onClick = onCount, enabled = !product.stockIsApproximate) { Icon(Icons.Outlined.CheckCircle, "Registrar conteo físico", tint = accentColor()) }
                    IconButton(onClick = onDeactivate) { Icon(Icons.Outlined.DeleteOutline, "Desactivar producto", tint = MaterialTheme.colorScheme.error) }
                }
            } else {
                IconButton(onClick = onSell, enabled = !product.stockIsApproximate) { Icon(Icons.Outlined.PointOfSale, "Registrar venta", tint = accentColor()) }
            }
        }
    }
}

@Composable
private fun ProductImage(url: String?, modifier: Modifier = Modifier) {
    if (!url.isNullOrBlank()) {
        AsyncImage(model = url, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.clip(RoundedCornerShape(14.dp)).background(accentContainerColor()), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Inventory2, null, tint = accentColor(), modifier = Modifier.size(32.dp))
        }
    }
}

private fun stockLabel(product: ProductEntity): String {
    if (product.stockIsApproximate) return "Inventario exacto no descargado"
    val eq = product.subunitsPerUnit?.takeIf { it > 0 }
    return if (eq != null && product.stockSubunits != null) {
        "Stock: ${product.stockUnits} ${product.unitName} + ${product.stockSubunits} ${product.subunitName ?: "sueltas"}"
    } else "Stock: ${product.stockUnits} ${product.unitName}"
}

@Composable
private fun ProductEditorDialog(product: ProductEntity?, busy: Boolean, onDismiss: () -> Unit, onSave: (ProductDraft) -> Unit) {
    var name by remember { mutableStateOf(product?.name.orEmpty()) }
    var description by remember { mutableStateOf(product?.description.orEmpty()) }
    var category by remember { mutableStateOf(product?.categoryName.orEmpty()) }
    var unitName by remember { mutableStateOf(product?.unitName ?: "unidad") }
    var subunitName by remember { mutableStateOf(product?.subunitName.orEmpty()) }
    var mode by remember { mutableStateOf(product?.saleMode ?: SaleModes.UNIT) }
    var eq by remember { mutableStateOf(product?.subunitsPerUnit?.toString().orEmpty()) }
    var unitCup by remember { mutableStateOf(product?.unitPriceCup?.toString().orEmpty()) }
    var unitTransfer by remember { mutableStateOf(product?.unitPriceTransfer?.toString().orEmpty()) }
    var subCup by remember { mutableStateOf(product?.subunitPriceCup?.toString().orEmpty()) }
    var subTransfer by remember { mutableStateOf(product?.subunitPriceTransfer?.toString().orEmpty()) }
    var units by remember { mutableStateOf(product?.stockUnits?.toString() ?: "0") }
    var subunits by remember { mutableStateOf(product?.stockSubunits?.toString().orEmpty()) }
    var minimum by remember { mutableStateOf(product?.minimumStock?.toString() ?: "0") }
    var visible by remember { mutableStateOf(product?.publicVisible ?: true) }
    var imageUri by remember { mutableStateOf<String?>(null) }
    var validation by remember { mutableStateOf<String?>(null) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> imageUri = uri?.toString() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Añadir producto" else "Editar producto", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 590.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (imageUri != null) {
                    AsyncImage(model = imageUri, contentDescription = "Imagen seleccionada", modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                } else if (!product?.imageUrl.isNullOrBlank()) {
                    AsyncImage(model = product?.imageUrl, contentDescription = "Imagen del producto", modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)), contentScale = ContentScale.Crop)
                }
                OutlinedButton(onClick = { imagePicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Text(if (imageUri != null || !product?.imageUrl.isNullOrBlank()) "Cambiar fotografía" else "Elegir fotografía") }
                OutlinedTextField(name, { name = it }, label = { Text("Nombre del producto *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(category, { category = it }, label = { Text("Categoría *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Text("Formato de venta", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = mode == SaleModes.UNIT, onClick = { mode = SaleModes.UNIT }, label = { Text("Unidad") })
                    FilterChip(selected = mode == SaleModes.SUBUNIT, onClick = {
                        mode = SaleModes.SUBUNIT
                        if (unitName == "unidad") unitName = "recipiente"
                        if (subunitName.isBlank()) subunitName = "vaso"
                    }, label = { Text("Por porciones") })
                    FilterChip(selected = mode == SaleModes.BOTH, onClick = {
                        mode = SaleModes.BOTH
                        if (unitName == "preparación") unitName = "recipiente"
                        if (subunitName.isBlank()) subunitName = "porción"
                    }, label = { Text("Ambos formatos") })
                }
                if (mode == SaleModes.UNIT) {
                    OutlinedTextField(unitName, { unitName = it }, label = { Text("Unidad de venta") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                } else if (mode == SaleModes.SUBUNIT) {
                    OutlinedTextField(subunitName, { subunitName = it }, label = { Text("¿Qué se vende? (vaso, taza, bola…) *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                    OutlinedTextField(eq, { eq = it }, label = { Text("Porciones por recipiente (opcional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    if (eq.isBlank()) Text("Ideal para café o chocolate preparado: registra cada vaso vendido y su importe sin inventar un stock de recipientes.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(unitName, { unitName = it }, label = { Text("Unidad / envase") }, modifier = Modifier.weight(1f), singleLine = true)
                        OutlinedTextField(subunitName, { subunitName = it }, label = { Text("Subunidad") }, modifier = Modifier.weight(1f), singleLine = true)
                    }
                    OutlinedTextField(eq, { eq = it }, label = { Text("Subunidades por unidad (si se conoce)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                }
                Text("Precios en CUP", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (mode != SaleModes.SUBUNIT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(unitCup, { unitCup = it }, label = { Text("Unidad · efectivo *") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(unitTransfer, { unitTransfer = it }, label = { Text("Unidad · transferencia") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                if (mode != SaleModes.UNIT) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(subCup, { subCup = it }, label = { Text("Subunidad · efectivo") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        OutlinedTextField(subTransfer, { subTransfer = it }, label = { Text("Subunidad · transferencia") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    }
                }
                Text(if (product == null) "Existencias iniciales" else "Existencias actuales: ${stockLabel(product)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (product == null) {
                    if (mode == SaleModes.SUBUNIT && eq.isBlank()) {
                        OutlinedTextField(units, { units = it }, label = { Text("Recipientes disponibles (0 si no se controlan)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        Text("Ejemplo: chocolate por vasos puede dejarse en 0. Para helado vendido por bolas puedes indicar los recipientes que hay, aunque no se conozca cuántas bolas rinde cada uno.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    } else if (eq.toIntOrNull()?.let { it > 0 } == true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(units, { units = it }, label = { Text(unitName.ifBlank { "Unidades" }) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(subunits, { subunits = it }, label = { Text(subunitName.ifBlank { "Subunidades sueltas" }) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    } else {
                        OutlinedTextField(units, { units = it }, label = { Text(unitName.ifBlank { "Unidades / recipientes" }) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        if (mode != SaleModes.UNIT) Text("Como no se conoce la equivalencia, las ventas por porciones se registrarán sin descontar recipientes automáticamente.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(minimum, { minimum = it }, label = { Text("Avisar con stock ≤") }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = visible, onClick = { visible = true })
                            Text("Visible al cliente", fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = !visible, onClick = { visible = false })
                            Text("Oculto", fontSize = 12.sp)
                        }
                    }
                }
                if (product != null) Text("El inventario existente se cambia mediante ‘Ajustar inventario’, para conservar un motivo e historial del movimiento.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                validation?.let { InfoPanel("Revisa los datos", it, PanelType.ERROR) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val pUnit = unitCup.takeIf { it.isNotBlank() }?.toLongOrNull()
                val pTransfer = unitTransfer.takeIf { it.isNotBlank() }?.toLongOrNull()
                val pSub = subCup.takeIf { it.isNotBlank() }?.toLongOrNull()
                val pSubTransfer = subTransfer.takeIf { it.isNotBlank() }?.toLongOrNull()
                val equivalence = if (mode == SaleModes.UNIT) null else eq.toIntOrNull()?.takeIf { it > 0 }
                val stock = units.toLongOrNull()
                val loose = subunits.toLongOrNull()
                val minStock = minimum.toLongOrNull()
                validation = when {
                    name.isBlank() -> "Escribe el nombre del producto."
                    category.isBlank() -> "Escribe una categoría."
                    unitName.isBlank() -> "Especifica cómo se llama la unidad."
                    mode != SaleModes.SUBUNIT && (pUnit == null || pUnit <= 0) -> "El precio por unidad en efectivo debe ser mayor que cero."
                    mode != SaleModes.SUBUNIT && unitTransfer.isNotBlank() && (pTransfer == null || pTransfer <= 0) -> "El precio por transferencia debe ser mayor que cero o dejarse vacío."
                    mode != SaleModes.UNIT && eq.isNotBlank() && equivalence == null -> "La equivalencia debe ser un número entero mayor que cero, o dejarse vacía si no se conoce."
                    mode != SaleModes.UNIT && subunitName.isBlank() -> "Indica el nombre de la subunidad."
                    mode != SaleModes.UNIT && (pSub == null || pSub <= 0) -> "Configura el precio en efectivo por subunidad."
                    mode != SaleModes.UNIT && subTransfer.isNotBlank() && (pSubTransfer == null || pSubTransfer <= 0) -> "El precio por transferencia de la subunidad no es válido."
                    product == null && (stock == null || stock < 0 || loose == null && subunits.isNotBlank() || (loose ?: 0) < 0) -> "Revisa las existencias iniciales."
                    minStock == null || minStock < 0 -> "El stock mínimo no puede ser negativo."
                    else -> null
                }
                if (validation == null) {
                    onSave(ProductDraft(
                        id = product?.id,
                        name = name,
                        description = description,
                        categoryName = category,
                        unitName = unitName,
                        subunitName = subunitName,
                        saleMode = mode,
                        subunitsPerUnit = equivalence,
                        unitPriceCup = if (mode == SaleModes.SUBUNIT) null else pUnit,
                        unitPriceTransfer = if (mode == SaleModes.SUBUNIT) null else pTransfer,
                        subunitPriceCup = if (mode == SaleModes.UNIT) null else pSub,
                        subunitPriceTransfer = if (mode == SaleModes.UNIT) null else pSubTransfer,
                        stockUnits = stock ?: product?.stockUnits ?: 0,
                        stockSubunits = if (equivalence != null) (loose ?: product?.stockSubunits ?: 0L) else null,
                        minimumStock = minStock!!,
                        publicVisible = visible,
                        imageUri = imageUri
                    ))
                }
            }) { Text(if (busy) "Guardando…" else "Guardar producto") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } }
    )
}

@Composable
private fun InventoryAdjustDialog(product: ProductEntity, busy: Boolean, onDismiss: () -> Unit, onSave: (Long, Long?, String) -> Unit) {
    var units by remember { mutableStateOf("0") }
    var subunits by remember { mutableStateOf("0") }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ajustar inventario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(product.name, fontWeight = FontWeight.SemiBold)
                Text("Existencias actuales: ${stockLabel(product)}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                OutlinedTextField(units, { units = it }, label = { Text("Cambio de unidades (+ entra, − sale)") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                if (product.subunitsPerUnit != null) OutlinedTextField(subunits, { subunits = it }, label = { Text("Cambio de subunidades") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                OutlinedTextField(reason, { reason = it }, label = { Text("Motivo obligatorio") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val u = units.toLongOrNull()
                val s = if (product.subunitsPerUnit != null) subunits.toLongOrNull() else null
                error = when {
                    u == null -> "El cambio de unidades no es válido."
                    product.subunitsPerUnit != null && s == null -> "El cambio de subunidades no es válido."
                    u == 0L && (s == null || s == 0L) -> "Indica un cambio de inventario."
                    reason.isBlank() -> "Escribe el motivo del ajuste."
                    else -> null
                }
                if (error == null) onSave(u!!, s, reason)
            }) { Text(if (busy) "Guardando…" else "Aplicar ajuste") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun CategoryRenameDialog(
    categories: List<String>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var selected by remember(categories) { mutableStateOf(categories.firstOrNull().orEmpty()) }
    var newName by remember(selected) { mutableStateOf(selected) }
    var expanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Editar categoría") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box {
                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (selected.isBlank()) "Selecciona una categoría" else selected)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        categories.forEach { name ->
                            DropdownMenuItem(text = { Text(name) }, onClick = { selected = name; newName = name; expanded = false; error = null })
                        }
                    }
                }
                OutlinedTextField(
                    value = newName, onValueChange = { newName = it; error = null },
                    label = { Text("Nuevo nombre") }, modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                Text("El cambio se aplicará a todos los productos activos de la categoría y quedará guardado offline.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                error = when {
                    selected.isBlank() -> "Selecciona una categoría."
                    newName.trim().isBlank() -> "Escribe el nuevo nombre."
                    newName.trim().equals(selected, true) -> "El nombre no ha cambiado."
                    else -> null
                }
                if (error == null) onSave(selected, newName.trim())
            }) { Text(if (busy) "Guardando…" else "Guardar categoría") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } }
    )
}

@Composable
private fun PhysicalCountDialog(product: ProductEntity, busy: Boolean, onDismiss: () -> Unit, onSave: (Long, Long?, String) -> Unit) {
    var units by remember { mutableStateOf(product.stockUnits.toString()) }
    var subunits by remember { mutableStateOf(product.stockSubunits?.toString().orEmpty()) }
    var reason by remember { mutableStateOf("Inventario físico") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Conteo físico") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(product.name, fontWeight = FontWeight.SemiBold)
                Text("Saldo guardado: ${stockLabel(product)}. Introduce lo que encontraste físicamente; el sistema registrará la diferencia como un movimiento auditado.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(units, { units = it }, label = { Text("${product.unitName} contadas") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                if (product.subunitsPerUnit != null) OutlinedTextField(subunits, { subunits = it }, label = { Text("${product.subunitName ?: "Subunidades"} contadas") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                OutlinedTextField(reason, { reason = it }, label = { Text("Motivo / referencia") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val countUnits = units.toLongOrNull()
                val countSubunits = if (product.subunitsPerUnit != null) subunits.toLongOrNull() else null
                error = when {
                    countUnits == null || countUnits < 0L -> "Las unidades contadas deben ser un entero no negativo."
                    product.subunitsPerUnit != null && (countSubunits == null || countSubunits < 0L || countSubunits >= product.subunitsPerUnit.toLong()) -> "Revisa las subunidades contadas y la equivalencia configurada."
                    reason.trim().length < 3 -> "Escribe un motivo o referencia."
                    else -> null
                }
                if (error == null) onSave(countUnits!!, countSubunits, reason.trim())
            }) { Text(if (busy) "Guardando…" else "Guardar conteo") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } }
    )
}

@Composable
private fun SellDialog(product: ProductEntity, busy: Boolean, onDismiss: () -> Unit, onConfirm: (Long, Long, String) -> Unit) {
    var unitCount by remember { mutableStateOf(if (product.saleMode == SaleModes.SUBUNIT) "0" else "1") }
    var subunitCount by remember { mutableStateOf("0") }
    var method by remember { mutableStateOf(PaymentMethods.CASH_CUP) }
    var error by remember { mutableStateOf<String?>(null) }
    val units = unitCount.toLongOrNull() ?: 0L
    val subunits = subunitCount.toLongOrNull() ?: 0L
    val unitPrice = if (method == PaymentMethods.CASH_CUP) product.unitPriceCup ?: 0L else product.unitPriceTransfer ?: 0L
    val subPrice = if (method == PaymentMethods.CASH_CUP) product.subunitPriceCup ?: 0L else product.subunitPriceTransfer ?: 0L
    val total = runCatching {
        Math.addExact(
            Math.multiplyExact(max(0L, units), unitPrice),
            Math.multiplyExact(max(0L, subunits), subPrice)
        )
    }.getOrDefault(-1L)
    val transferAvailable = (units == 0L || (product.unitPriceTransfer ?: 0) > 0) && (subunits == 0L || (product.subunitPriceTransfer ?: 0) > 0)
    LaunchedEffect(transferAvailable, method, units, subunits) {
        if (method == PaymentMethods.TRANSFER_CUP && !transferAvailable) method = PaymentMethods.CASH_CUP
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Registrar venta", fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 550.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductImage(product.imageUrl, Modifier.size(60.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(product.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (product.saleMode == SaleModes.SUBUNIT && product.subunitsPerUnit == null && product.stockUnits == 0L) "Venta por ${product.subunitName ?: "porciones"} · stock no controlado"
                            else stockLabel(product),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (product.saleMode != SaleModes.SUBUNIT) OutlinedTextField(unitCount, { unitCount = it }, label = { Text("${product.unitName} vendidas") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (product.saleMode != SaleModes.UNIT) OutlinedTextField(subunitCount, { subunitCount = it }, label = { Text("${product.subunitName ?: "Subunidades"} vendidas") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (product.subunitsPerUnit == null && product.saleMode == SaleModes.BOTH) InfoPanel("Inventario por porciones", "No se conoce la equivalencia entre el recipiente y las porciones. La venta y su importe se guardarán, pero las porciones no descontarán automáticamente recipientes.", PanelType.INFO)
                if (product.saleMode == SaleModes.SUBUNIT && product.subunitsPerUnit == null) InfoPanel("Venta por porciones", "Registra los vasos o tazas servidos. El sistema calcula el importe, pero no inventa una cantidad de recipientes restantes.", PanelType.INFO)
                product.subunitsPerUnit?.takeIf { it > 0 }?.let { equivalence ->
                    if (subunits > 0) {
                        val normalizedUnits = units + subunits / equivalence
                        val normalizedSubunits = subunits % equivalence
                        Text("Para el inventario, esta cantidad equivale a $normalizedUnits ${product.unitName} y $normalizedSubunits ${product.subunitName ?: "subunidades"}.", color = accentColor(), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Text("Método de pago", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = method == PaymentMethods.CASH_CUP, onClick = { method = PaymentMethods.CASH_CUP })
                    Column(Modifier.weight(1f)) { Text("Efectivo CUP"); Text("Precio según catálogo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = method == PaymentMethods.TRANSFER_CUP, onClick = { method = PaymentMethods.TRANSFER_CUP }, enabled = transferAvailable)
                    Column(Modifier.weight(1f)) { Text("Transferencia"); Text(if (transferAvailable) "Precio según catálogo" else "No hay precio de transferencia para esta cantidad", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatCup(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = accentColor())
                }
                error?.let { InfoPanel("No se puede confirmar", it, PanelType.ERROR) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                error = when {
                    (unitCount.toLongOrNull() == null || subunitCount.toLongOrNull() == null) -> "Introduce cantidades numéricas válidas."
                    units < 0 || subunits < 0 || units + subunits <= 0 -> "La cantidad debe ser mayor que cero."
                    method == PaymentMethods.TRANSFER_CUP && !transferAvailable -> "Elige efectivo o configura precio por transferencia para todos los formatos vendidos."
                    total <= 0 -> "El total debe ser mayor que cero."
                    else -> null
                }
                if (error == null) onConfirm(units, subunits, method)
            }) { Text(if (busy) "Guardando…" else "Confirmar venta") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancelar") } }
    )
}

@Composable
private fun SellScreen(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    ProductsScreen(state, vm, Roles.WORKER)
}

@Composable
private fun SalesScreen(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (state.session?.role == Roles.WORKER) "Mis ventas" else "Ventas", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Registro por hora, trabajadora y método de pago", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = vm::refreshSales) { Icon(Icons.Outlined.Refresh, "Actualizar") }
        }
        if (state.sales.isEmpty()) EmptyPanel("Todavía no hay ventas", "Las operaciones aparecerán aquí una vez registradas.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(state.sales, key = { it.id }) { sale -> SaleRow(sale, state.session?.businessTimeZone, if (sale.workerId == state.session?.userId) ({ vm.retrySale(sale.id) }) else null) }
        }
    }
}

@Composable
private fun SaleRow(sale: SaleSummary, timeZoneId: String? = null, onRetry: (() -> Unit)? = null) {
    val statusColor = when (sale.status) { "SYNCED" -> MaterialTheme.colorScheme.tertiary; "NEEDS_REVIEW" -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.secondary }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(sale.summary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(sale.workerName, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
                Text(formatCup(sale.totalCUP), fontWeight = FontWeight.Bold, color = accentColor())
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(formatDateTime(sale.soldAt, timeZoneId), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(if (sale.paymentMethod == PaymentMethods.TRANSFER_CUP) "Transferencia" else "Efectivo CUP", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(when (sale.status) { "SYNCED" -> "Sincronizada"; "NEEDS_REVIEW" -> "Revisar"; else -> "Pendiente" }, color = statusColor, fontWeight = FontWeight.SemiBold, fontSize = 10.sp)
            }
            sale.error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis) }
            if (sale.status == "NEEDS_REVIEW" && onRetry != null) {
                TextButton(onClick = onRetry, modifier = Modifier.align(Alignment.End)) { Text("Reintentar envío") }
            }
        }
    }
}

@Composable
private fun ReportsScreen(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    val businessZone = remember(state.session?.businessTimeZone) {
        runCatching { ZoneId.of(state.session?.businessTimeZone ?: "America/Havana") }.getOrDefault(ZoneId.of("America/Havana"))
    }
    val today = LocalDate.now(businessZone)
    val periodStart = when (state.reportPeriod) {
        "WEEK" -> today.with(java.time.DayOfWeek.MONDAY)
        "MONTH" -> today.withDayOfMonth(1)
        else -> today
    }
    val periodEnd = when (state.reportPeriod) {
        "WEEK" -> periodStart.plusWeeks(1)
        "MONTH" -> periodStart.plusMonths(1)
        else -> periodStart.plusDays(1)
    }
    val periodLabel = when (state.reportPeriod) {
        "WEEK" -> "Semana del ${periodStart.format(DateTimeFormatter.ofPattern("dd/MM"))} al ${periodEnd.minusDays(1).format(DateTimeFormatter.ofPattern("dd/MM"))}"
        "MONTH" -> periodStart.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale("es"))).replaceFirstChar { it.uppercase(Locale("es")) }
        else -> "Hoy · ${today.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))}"
    }
    val salesInPeriod = state.sales.filter {
        runCatching {
            val date = OffsetDateTime.parse(it.soldAt).atZoneSameInstant(businessZone).toLocalDate()
            !date.isBefore(periodStart) && date.isBefore(periodEnd)
        }.getOrDefault(false)
    }
    val cash = sumCupExactly(salesInPeriod.filter { it.paymentMethod == PaymentMethods.CASH_CUP && it.status != "NEEDS_REVIEW" })
    val transfer = sumCupExactly(salesInPeriod.filter { it.paymentMethod == PaymentMethods.TRANSFER_CUP && it.status != "NEEDS_REVIEW" })
    val inventoryCash = state.dashboard?.totalInventoryValueCash
    val inventoryTransfer = state.dashboard?.totalInventoryValueTransfer
    val confirmedProducts = state.dashboard?.productSales.orEmpty()
    val workers = state.dashboard?.workerSales.orEmpty()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Estadísticas", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(periodLabel, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = { vm.refreshReports() }) { Icon(Icons.Outlined.Refresh, "Actualizar estadísticas") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = state.reportPeriod == "DAY", onClick = { vm.refreshReports("DAY") }, label = { Text("Hoy") })
            FilterChip(selected = state.reportPeriod == "WEEK", onClick = { vm.refreshReports("WEEK") }, label = { Text("Esta semana") })
            FilterChip(selected = state.reportPeriod == "MONTH", onClick = { vm.refreshReports("MONTH") }, label = { Text("Este mes") })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("Efectivo", if (state.dashboard != null) exactCupLabel(state.dashboard.cashCup, null) else exactCupLabel(null, cash), Icons.Outlined.PointOfSale, Modifier.weight(1f))
            SummaryCard("Transferencias", if (state.dashboard != null) exactCupLabel(state.dashboard.transferCup, null) else exactCupLabel(null, transfer), Icons.Outlined.CloudSync, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("Operaciones", (state.dashboard?.saleCount ?: salesInPeriod.count { it.status != "NEEDS_REVIEW" }).toString(), Icons.Outlined.Assessment, Modifier.weight(1f))
            SummaryCard("Por reponer", (state.dashboard?.lowStockCount ?: state.products.count { !it.stockIsApproximate && !(it.saleMode == SaleModes.SUBUNIT && it.subunitsPerUnit == null && it.stockUnits == 0L) && it.stockUnits <= it.minimumStock }).toString(), Icons.Outlined.WarningAmber, Modifier.weight(1f), caution = true)
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Productos vendidos", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                Text("Cantidades reales registradas, aunque no controles existencias.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (confirmedProducts.isEmpty()) {
            EmptyPanel("Sin productos vendidos", "Cuando se registren ventas confirmadas, aquí verás cuántas unidades, vasos o porciones se vendieron en el periodo.")
        } else {
            confirmedProducts.forEach { ProductSalesReportCard(it) }
        }

        if (state.pendingProductSales.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Pendientes o por revisar", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Se guardaron en este teléfono y aún no están confirmadas en el servidor. Algunas pueden necesitar revisión.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                        }
                    }
                    state.pendingProductSales.forEach { PendingProductSalesCard(it) }
                }
            }
        }

        if (workers.isNotEmpty()) {
            Text("Ventas por trabajadora", fontWeight = FontWeight.Bold, fontSize = 19.sp)
            workers.forEach { WorkerSalesReportCard(it) }
        }

        Text("Valor potencial de existencias", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("En efectivo", inventoryCash?.let(::formatCup) ?: "Sin dato", Icons.Outlined.Inventory2, Modifier.weight(1f))
            SummaryCard("Por transferencia", inventoryTransfer?.let(::formatCup) ?: "Sin dato", Icons.Outlined.CloudSync, Modifier.weight(1f))
        }
        InfoPanel("Importante", "Las cantidades vendidas se calculan desde cada artículo de las ventas, no desde el stock disponible. Por eso también se cuentan los vasos y porciones de productos sin control de existencias. Las ventas pendientes se muestran aparte para evitar duplicar o dar por confirmados datos que aún no llegaron al servidor.", PanelType.INFO)
        Text("Operaciones del periodo", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        if (salesInPeriod.isEmpty()) EmptyPanel("Sin operaciones locales", "El detalle de ventas registradas en este teléfono aparecerá aquí.") else salesInPeriod.forEach { SaleRow(it, state.session?.businessTimeZone) }
    }
}

@Composable
private fun ProductSalesReportCard(item: ProductSalesSummary) {
    val quantity = buildList {
        if (item.unitQuantity > 0) add("${item.unitQuantity} ${item.unitName}")
        if (item.subunitQuantity > 0) add("${item.subunitQuantity} ${item.subunitName ?: "porciones"}")
    }.joinToString(" · ").ifBlank { "Sin cantidad" }
    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(item.productName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(quantity, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                }
                Text(formatCup(item.totalCup), fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Efectivo", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text(formatCup(item.cashCup), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Transferencia", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                Text(formatCup(item.transferCup), fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PendingProductSalesCard(item: PendingProductSalesSummary) {
    val quantity = buildList {
        if (item.unitQuantity > 0) add("${item.unitQuantity} ${item.unitName}")
        if (item.subunitQuantity > 0) add("${item.subunitQuantity} ${item.subunitName ?: "porciones"}")
    }.joinToString(" · ").ifBlank { "Sin cantidad" }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.72f), RoundedCornerShape(14.dp)).padding(11.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(item.productName, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(quantity, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Text("Efectivo ${formatCup(item.cashCup)}  ·  Transferencia ${formatCup(item.transferCup)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WorkerSalesReportCard(item: WorkerSalesSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(item.workerName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text(formatCup(item.totalCup), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            Text("${item.saleCount} operaciones registradas", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Efectivo: ${formatCup(item.cashCup)}", fontSize = 12.sp)
                Text("Transferencia: ${formatCup(item.transferCup)}", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AnnouncementsScreen(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    val owner = state.session?.role == Roles.OWNER
    var showCreate by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (owner) "Avisos y ofertas" else "Avisos de la cafetería", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text(if (owner) "Publica novedades ahora o programa su hora." else "Novedades y ofertas publicadas por la cafetería.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            }
            IconButton(onClick = vm::refreshAnnouncements) { Icon(Icons.Outlined.Refresh, "Actualizar avisos") }
        }
        if (owner) Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Crear aviso u oferta") }
        if (state.announcements.isEmpty()) EmptyPanel("No hay avisos para mostrar", "Los anuncios publicados o programados aparecerán aquí cuando el servicio esté conectado.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(state.announcements, key = { it.id }) { announcement ->
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(announcement.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(announcement.message, fontSize = 14.sp)
                        Text("Publicación: ${formatDateTime(announcement.publishAt, state.session?.businessTimeZone)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        announcement.expiresAt?.let { Text("Vence: ${formatDateTime(it, state.session?.businessTimeZone)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        if (owner && announcement.status != "CANCELLED") TextButton(onClick = { vm.cancelAnnouncement(announcement.id) }, modifier = Modifier.align(Alignment.End)) { Icon(Icons.Outlined.DeleteOutline, null); Spacer(Modifier.width(4.dp)); Text("Cancelar aviso") }
                    }
                }
            }
        }
    }
    if (showCreate) AnnouncementEditorDialog(
        busy = state.busy,
        businessTimeZone = state.session?.businessTimeZone ?: "America/Havana",
        onDismiss = { showCreate = false },
        onPublish = { title, message, publish, expiry -> vm.publishAnnouncement(title, message, publish, expiry) { showCreate = false } }
    )
}

@Composable
private fun AnnouncementEditorDialog(busy: Boolean, businessTimeZone: String, onDismiss: () -> Unit, onPublish: (String, String, String, String?) -> Unit) {
    val announcementZone = remember(businessTimeZone) {
        runCatching { ZoneId.of(businessTimeZone) }.getOrDefault(ZoneId.of("America/Havana"))
    }
    var title by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var publishAt by remember(announcementZone) { mutableStateOf(OffsetDateTime.now(announcementZone).withSecond(0).withNano(0).toString()) }
    var expiresAt by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    fun chooseDateTime(initialValue: String?, onSelected: (String) -> Unit) {
        val initial = runCatching { OffsetDateTime.parse(initialValue).atZoneSameInstant(announcementZone).toOffsetDateTime() }
            .getOrElse { OffsetDateTime.now(announcementZone) }
        DatePickerDialog(
            context,
            { _, year, month, day ->
                TimePickerDialog(
                    context,
                    { _, hour, minute ->
                        val selected = LocalDate.of(year, month + 1, day)
                            .atTime(hour, minute)
                            .atZone(announcementZone)
                            .toOffsetDateTime()
                        onSelected(selected.toString())
                    },
                    initial.hour,
                    initial.minute,
                    true
                ).show()
            },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear aviso") },
        text = {
            Column(Modifier.heightIn(max = 510.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Título") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(message, { message = it }, label = { Text("Mensaje") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                Text("Cuándo se publicará", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                OutlinedButton(onClick = { chooseDateTime(publishAt) { publishAt = it } }, modifier = Modifier.fillMaxWidth()) {
                    Text(formatDateTime(publishAt, businessTimeZone))
                }
                Text("El servidor deberá publicar el aviso a esta hora aunque el teléfono de la dueña esté apagado.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("Vencimiento", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (expiresAt == null) {
                    OutlinedButton(onClick = { chooseDateTime(null) { expiresAt = it } }, modifier = Modifier.fillMaxWidth()) { Text("Añadir fecha de vencimiento") }
                } else {
                    OutlinedButton(onClick = { chooseDateTime(expiresAt) { expiresAt = it } }, modifier = Modifier.fillMaxWidth()) { Text(formatDateTime(expiresAt!!, businessTimeZone)) }
                    TextButton(onClick = { expiresAt = null }, modifier = Modifier.align(Alignment.End)) { Text("No poner vencimiento") }
                }
                error?.let { InfoPanel("Revisa el aviso", it, PanelType.ERROR) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val expiryValue = expiresAt
                error = when {
                    title.isBlank() -> "Escribe un título."
                    message.isBlank() -> "Escribe un mensaje."
                    runCatching { OffsetDateTime.parse(publishAt) }.isFailure -> "La fecha de publicación no es válida."
                    expiryValue != null && runCatching { OffsetDateTime.parse(expiryValue) }.isFailure -> "La fecha de vencimiento no es válida."
                    expiryValue != null && runCatching { OffsetDateTime.parse(expiryValue).isAfter(OffsetDateTime.parse(publishAt)) }.getOrDefault(false).not() -> "El vencimiento debe ser posterior a la publicación."
                    else -> null
                }
                if (error == null) onPublish(title, message, publishAt, expiryValue)
            }) { Text(if (busy) "Guardando…" else "Publicar / programar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun TeamScreen(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    var showAdd by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Equipo", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Cada trabajadora tiene su propio acceso.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            IconButton(onClick = vm::refreshTeam) { Icon(Icons.Outlined.Refresh, "Actualizar equipo") }
        }
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Añadir trabajadora") }
        if (state.team.isEmpty()) EmptyPanel("No hay cuentas de trabajadoras cargadas", "Añade una cuenta cuando el servicio esté conectado.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.team, key = { it.id }) { member ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(accentContainerColor()), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Group, null, tint = accentColor()) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(member.displayName, fontWeight = FontWeight.SemiBold)
                            Text(member.identifier, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (member.active) "Cuenta activa" else "Cuenta desactivada", fontSize = 11.sp, color = if (member.active) successColor() else MaterialTheme.colorScheme.error)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(member.role, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(
                                onClick = { vm.setWorkerActive(member.id, !member.active) },
                                enabled = !state.busy
                            ) { Text(if (member.active) "Desactivar" else "Activar", fontSize = 11.sp) }
                        }
                    }
                }
            }
        }
    }
    if (showAdd) AddWorkerDialog(state.busy, onDismiss = { showAdd = false }, onSave = { n, i, p -> vm.addWorker(n, i, p) { showAdd = false } })
}

@Composable
private fun AddWorkerDialog(busy: Boolean, onDismiss: () -> Unit, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir trabajadora") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre visible") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(identifier, { identifier = it }, label = { Text("Nick de la trabajadora") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(password, { password = it }, label = { Text("Contraseña temporal (8+ caracteres)") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
            }
        },
        confirmButton = { Button(enabled = !busy, onClick = {
            error = when { name.isBlank() -> "Escribe el nombre."; identifier.isBlank() -> "Escribe el usuario."; password.length < 8 -> "La contraseña debe tener 8 caracteres como mínimo."; else -> null }
            if (error == null) onSave(name, identifier, password)
        }) { Text(if (busy) "Creando…" else "Crear cuenta") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun CustomerCatalogScreen(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    val context = LocalContext.current
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted || Build.VERSION.SDK_INT < 33) vm.enableNotifications()
    }
    val categories = remember(state.products) {
        listOf("Todas") + state.products.filter { it.active && it.publicVisible }.map { it.categoryName }.distinct().sorted()
    }
    val filtered = state.products.filter {
        it.active && it.publicVisible &&
            (state.search.isBlank() || it.name.contains(state.search, true) || it.categoryName.contains(state.search, true) || it.description.contains(state.search, true)) &&
            (state.category == "Todas" || it.categoryName == state.category)
    }
    val espresso = Color(0xFF352319)
    val caramel = Color(0xFF8B6244)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically(initialOffsetY = { it / 10 })) {
            Box(
                Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(27.dp))
                    .background(Brush.linearGradient(listOf(espresso, Color(0xFF68452F), caramel)))
            ) {
                Box(Modifier.size(170.dp).align(Alignment.CenterEnd).offset(x = 34.dp, y = 35.dp)
                    .clip(CircleShape).background(Color.White.copy(alpha = 0.07f)))
                Box(Modifier.size(105.dp).align(Alignment.TopEnd).offset(x = 15.dp, y = (-34).dp)
                    .clip(CircleShape).background(Color(0xFFD8B28D).copy(alpha = 0.10f)))
                Row(Modifier.fillMaxSize().padding(start = 21.dp, end = 18.dp, top = 19.dp, bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("Una pausa que sabe bien", color = Color(0xFFFFF3E5), fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                        Text("Descubre tus favoritos de hoy.", color = Color(0xFFEEDBC8), fontSize = 12.sp)
                        Surface(color = Color(0xFFFFEAD5).copy(alpha = 0.16f), shape = RoundedCornerShape(50.dp)) {
                            Text("Hecho con cariño", color = Color(0xFFFFEAD5), fontSize = 10.sp, modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp))
                        }
                    }
                    Box(
                        Modifier.size(82.dp).clip(CircleShape)
                            .background(Brush.radialGradient(listOf(Color(0xFFDDBB96).copy(alpha = 0.42f), Color(0xFF4E3425)))),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Storefront, null, tint = Color(0xFFFFEBD5), modifier = Modifier.size(41.dp))
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                modifier = Modifier.weight(1f),
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = caramel) },
                placeholder = { Text("Busca café, pan, confituras…", fontSize = 13.sp) },
                singleLine = true,
                shape = RoundedCornerShape(19.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f),
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                )
            )
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.size(50.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = vm::refreshProducts),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.Refresh, "Actualizar catálogo", tint = MaterialTheme.colorScheme.onPrimary)
            }
        }

        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            categories.forEach { category ->
                FilterChip(
                    selected = state.category == category,
                    onClick = { vm.setCategory(category) },
                    label = { Text(category, fontSize = 12.sp) },
                    shape = RoundedCornerShape(50.dp),
                    border = if (state.category == category) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f))
                .clickable {
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else vm.enableNotifications()
                }
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
                Icon(Icons.Outlined.NotificationsActive, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(10.dp))
                Text("Recibe ofertas y novedades", color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.ArrowUpward, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(15.dp))
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Para disfrutar", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Text("${filtered.size} ${if (filtered.size == 1) "producto" else "productos"}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
            }
            if (!state.online) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(50.dp)) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onTertiaryContainer, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(5.dp))
                        Text("Sin conexión", fontSize = 10.sp, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }
        }

        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                ) {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 23.dp, vertical = 27.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Box(
                            Modifier.size(70.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Inventory2, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
                        }
                        Text(
                            if (state.search.isNotBlank() || state.category != "Todas") "No encontramos ese producto" else "Estamos preparando el escaparate",
                            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            if (state.search.isNotBlank() || state.category != "Todas") "Prueba con otra búsqueda o categoría."
                            else "Los productos publicados aparecerán aquí con sus fotos, precios y disponibilidad.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (state.online) {
                            OutlinedButton(onClick = vm::refreshProducts, shape = RoundedCornerShape(50.dp)) {
                                Icon(Icons.Outlined.Refresh, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(7.dp)); Text("Actualizar catálogo")
                            }
                        } else {
                            Text("Vuelve a conectarte para consultar las novedades.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(13.dp),
                contentPadding = PaddingValues(bottom = 18.dp)
            ) {
                gridItems(filtered, key = { it.id }) { product ->
                    Card(
                        modifier = Modifier.fillMaxWidth().animateContentSize(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 5.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    ) {
                        Column(Modifier.fillMaxWidth().padding(9.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Box {
                                ProductImage(product.imageUrl, Modifier.fillMaxWidth().height(122.dp))
                                Surface(
                                    modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                                    color = Color(0xFF2F2119).copy(alpha = 0.82f),
                                    shape = RoundedCornerShape(50.dp)
                                ) {
                                    Text(product.categoryName, color = Color(0xFFFFF0DF), fontSize = 9.sp, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 17.sp)
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                product.unitPriceCup?.let {
                                    Text("${formatCup(it)}", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text("por ${product.unitName}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                                }
                                product.subunitPriceCup?.let {
                                    Text("${formatCup(it)} / ${product.subunitName ?: "porción"}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                                }
                            }
                            product.unitPriceTransfer?.let {
                                Text("Transferencia: ${formatCup(it)}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            product.subunitPriceTransfer?.let {
                                Text("Transferencia porción: ${formatCup(it)}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                Box(Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF4C9A70)))
                                Text(
                                    when {
                                        product.saleMode == SaleModes.SUBUNIT && product.subunitsPerUnit == null && product.stockUnits == 0L -> "Por porciones"
                                        product.stockUnits > 0L -> "Disponible"
                                        else -> "Agotado"
                                    },
                                    fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private enum class PanelType { INFO, SUCCESS, ERROR }

@Composable
private fun InfoPanel(title: String, message: String, type: PanelType) {
    val bg = when (type) {
        PanelType.INFO -> MaterialTheme.colorScheme.secondaryContainer
        PanelType.SUCCESS -> MaterialTheme.colorScheme.tertiaryContainer
        PanelType.ERROR -> MaterialTheme.colorScheme.errorContainer
    }
    val fg = when (type) {
        PanelType.INFO -> MaterialTheme.colorScheme.onSecondaryContainer
        PanelType.SUCCESS -> MaterialTheme.colorScheme.onTertiaryContainer
        PanelType.ERROR -> MaterialTheme.colorScheme.onErrorContainer
    }
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bg).padding(12.dp), verticalAlignment = Alignment.Top) {
        Icon(when (type) { PanelType.INFO -> Icons.Outlined.CloudSync; PanelType.SUCCESS -> Icons.Outlined.CheckCircle; PanelType.ERROR -> Icons.Outlined.WarningAmber }, null, tint = fg, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = fg, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            Text(message, color = fg, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun EmptyPanel(title: String, message: String) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Inventory2, null, tint = accentColor(), modifier = Modifier.size(36.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

private fun formatDateTime(value: String, zoneId: String? = null): String {
    return runCatching {
        val targetZone = runCatching { ZoneId.of(zoneId ?: ZoneId.systemDefault().id) }.getOrDefault(ZoneId.systemDefault())
        val dateTime = OffsetDateTime.parse(value).atZoneSameInstant(targetZone)
        dateTime.format(DateTimeFormatter.ofPattern("dd/MM/yyyy · HH:mm", Locale.getDefault()))
    }.getOrElse { value }
}
