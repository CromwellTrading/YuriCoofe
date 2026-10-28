package com.cafeteria.gestion.ui

import android.net.Uri
import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private val Accent = Color(0xFF6250D8)
private val AccentSoft = Color(0xFFECE8FF)
private val AppBg = Color(0xFFF8F7FC)
private val TextMuted = Color(0xFF777589)
private val Success = Color(0xFF21865B)
private val Warning = Color(0xFFB56B00)

@Composable
fun CafeteriaApp(viewModel: CafeteriaViewModel) {
    val state by viewModel.state.collectAsState()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Accent,
            onPrimary = Color.White,
            primaryContainer = AccentSoft,
            onPrimaryContainer = Color(0xFF302568),
            background = AppBg,
            surface = Color.White,
            onSurface = Color(0xFF232235),
            secondary = Color(0xFF3E8C73),
            error = Color(0xFFB3261E)
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = AppBg) {
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Accent) }
                state.session == null -> LoginScreen(
                    busy = state.busy,
                    apiConfigured = state.apiConfigured,
                    error = state.error,
                    message = state.message,
                    onLogin = viewModel::login,
                    onBrowseAsCustomer = viewModel::openCatalogAsGuest
                )
                else -> MainShell(state, viewModel)
            }
        }
    }
}

@Composable
private fun LoginScreen(
    busy: Boolean,
    apiConfigured: Boolean,
    error: String?,
    message: String?,
    onLogin: (String, String) -> Unit,
    onBrowseAsCustomer: () -> Unit
) {
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(86.dp).clip(RoundedCornerShape(28.dp)).background(AccentSoft),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Storefront, null, tint = Accent, modifier = Modifier.size(46.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("Cafetería", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Color(0xFF242137))
        Spacer(Modifier.height(6.dp))
        Text("Inventario, ventas y avisos en un solo lugar", color = TextMuted, fontSize = 14.sp)
        Spacer(Modifier.height(30.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Iniciar sesión", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = identifier, onValueChange = { identifier = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Usuario o correo") }, singleLine = true
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it }, modifier = Modifier.fillMaxWidth(),
                    label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation()
                )
                if (!apiConfigured) {
                    InfoPanel(
                        title = "Servicio pendiente de configuración",
                        message = "Las pantallas y el almacenamiento local están preparados. El acceso real se habilitará al conectar la dirección del backend; esta app no incluye cuentas ni datos inventados.",
                        type = PanelType.INFO
                    )
                }
                if (!error.isNullOrBlank()) InfoPanel("No se pudo iniciar sesión", error, PanelType.ERROR)
                if (!message.isNullOrBlank()) InfoPanel("Información", message, PanelType.SUCCESS)
                Button(onClick = { onLogin(identifier, password) }, enabled = !busy, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(15.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    else Text("Entrar", fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onBrowseAsCustomer, modifier = Modifier.fillMaxWidth(), enabled = !busy) {
                    Text("Ver catálogo como cliente")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text("Cada persona utiliza su propia cuenta y permisos.", color = TextMuted, fontSize = 12.sp)
    }
}

private data class NavEntry(val screen: String, val label: String, val icon: ImageVector)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainShell(state: CafeteriaUiState, vm: CafeteriaViewModel) {
    val session = state.session ?: return
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
        containerColor = AppBg,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Cafetería", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text(
                            when (session.role) { Roles.OWNER -> "Administración"; Roles.WORKER -> "Punto de venta"; else -> "Catálogo público" },
                            fontSize = 11.sp, color = TextMuted
                        )
                    }
                },
                actions = {
                    if (state.pendingCount > 0) {
                        Badge(containerColor = Warning, contentColor = Color.White) { Text(state.pendingCount.toString()) }
                        Spacer(Modifier.width(3.dp))
                    }
                    IconButton(onClick = vm::syncNow) {
                        Icon(if (state.online) Icons.Outlined.CloudSync else Icons.Outlined.CloudOff, contentDescription = "Sincronizar", tint = if (state.online) Accent else TextMuted)
                    }
                    IconButton(onClick = vm::logout) { Icon(Icons.Outlined.Logout, contentDescription = "Cerrar sesión") }
                }
            )
        },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
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
                    Modifier.fillMaxWidth().background(if (state.pendingCount > 0) Color(0xFFFFF1D9) else Color(0xFFECEAF2)).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(if (state.pendingCount > 0) Icons.Outlined.CloudSync else Icons.Outlined.CloudOff, null, tint = if (state.pendingCount > 0) Warning else TextMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (state.pendingCount > 0) "${state.pendingCount} venta(s) necesitan sincronización o revisión. Los registros se conservan en este teléfono."
                        else "Sin conexión. Puedes consultar los datos guardados; las ventas nuevas se conservarán en este teléfono.",
                        fontSize = 12.sp, color = Color(0xFF514B60), modifier = Modifier.weight(1f)
                    )
                    if (state.pendingCount > 0) TextButton(onClick = { vm.setScreen(SCREEN_SALES) }) { Text("Ver", color = Accent) }
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
                else -> DashboardScreen(state, vm)
            }
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
    val cash = salesToday.filter { it.paymentMethod == PaymentMethods.CASH_CUP }.sumOf { it.totalCUP }
    val transfer = salesToday.filter { it.paymentMethod == PaymentMethods.TRANSFER_CUP }.sumOf { it.totalCUP }
    val lowStock = state.products.count { it.stockUnits <= it.minimumStock }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Hola, ${state.session?.displayName?.substringBefore(' ') ?: ""}", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                Text("Resumen del negocio · $today", color = TextMuted, fontSize = 13.sp)
            }
            IconButton(onClick = vm::refreshAll) { Icon(Icons.Outlined.Refresh, "Actualizar") }
        }
        if (state.session?.role == Roles.OWNER) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryCard("Efectivo CUP", formatCup(state.dashboard?.cashCup ?: cash), Icons.Outlined.PointOfSale, Modifier.weight(1f))
                SummaryCard("Transferencia", formatCup(state.dashboard?.transferCup ?: transfer), Icons.Outlined.CloudSync, Modifier.weight(1f))
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
            Text("Consulta lo que está disponible y revisa los cambios recientes.", color = TextMuted)
            Button(onClick = { vm.setScreen(if (state.session?.role == Roles.WORKER) SCREEN_SELL else SCREEN_CUSTOMER_CATALOG) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.session?.role == Roles.WORKER) "Registrar una venta" else "Ver catálogo")
            }
            if (state.pendingCount > 0) InfoPanel("Ventas pendientes", "${state.pendingCount} venta(s) están guardadas localmente y se enviarán al recuperar conexión.", PanelType.INFO)
        }
    }
}

@Composable
private fun SummaryCard(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier, caution: Boolean = false) {
    Card(modifier, shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(12.dp)).background(if (caution) Color(0xFFFFE7C1) else AccentSoft), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = if (caution) Warning else Accent, modifier = Modifier.size(19.dp))
            }
            Text(title, color = TextMuted, fontSize = 12.sp)
            Text(value, fontWeight = FontWeight.Bold, fontSize = 18.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun QuickAction(label: String, icon: ImageVector, modifier: Modifier = Modifier, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 54.dp), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE3DFF2))) {
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
    var selectedSale by remember { mutableStateOf<ProductEntity?>(null) }
    val owner = role == Roles.OWNER
    val categories = listOf("Todas") + state.products.map { it.categoryName }.filter { it.isNotBlank() }.distinct().sorted()
    val filtered = state.products.filter { product ->
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
                Text("${state.products.size} productos guardados", fontSize = 12.sp, color = TextMuted)
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
            Text("Categoría", color = TextMuted, fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                categories.forEach { category -> FilterChip(selected = state.category == category, onClick = { vm.setCategory(category) }, label = { Text(category, maxLines = 1) }) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ordenar por", color = TextMuted, fontSize = 12.sp)
            SortSelector(state.sortBy, vm::setSort)
            IconButton(onClick = vm::toggleSortDirection) { Icon(if (state.ascending) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, if (state.ascending) "Ascendente" else "Descendente") }
            Text(if (state.ascending) "Ascendente" else "Descendente", color = TextMuted, fontSize = 11.sp)
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
                        onSell = { selectedSale = product }
                    )
                }
            }
        }
    }
    if (showCreate) ProductEditorDialog(null, state.busy, onDismiss = { showCreate = false }, onSave = { draft -> vm.saveProduct(draft) { showCreate = false } })
    editing?.let { product -> ProductEditorDialog(product, state.busy, onDismiss = { editing = null }, onSave = { draft -> vm.saveProduct(draft) { editing = null } }) }
    adjusting?.let { product -> InventoryAdjustDialog(product, state.busy, onDismiss = { adjusting = null }, onSave = { u, s, r -> vm.adjustInventory(product.id, u, s, r) { adjusting = null } }) }
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
    onSell: () -> Unit
) {
    val low = product.stockUnits <= product.minimumStock
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(1.dp)) {
        Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(12.dp), verticalAlignment = Alignment.Top) {
            ProductImage(product.imageUrl, Modifier.size(82.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(product.name, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(product.categoryName, color = TextMuted, fontSize = 12.sp)
                if (product.unitPriceCup != null) Text("${formatCup(product.unitPriceCup)} / ${product.unitName}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                else if (product.subunitPriceCup != null) Text("${formatCup(product.subunitPriceCup)} / ${product.subunitName ?: "subunidad"}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                product.subunitPriceCup?.let { Text("${formatCup(it)} / ${product.subunitName ?: "subunidad"}", color = TextMuted, fontSize = 11.sp) }
                product.subunitPriceTransfer?.let { Text("${formatCup(it)} por transferencia · ${product.subunitName ?: "subunidad"}", color = TextMuted, fontSize = 11.sp) }
                product.unitPriceTransfer?.let { Text("${formatCup(it)} por transferencia", color = TextMuted, fontSize = 11.sp) }
                val stockDescription = stockLabel(product)
                Text(stockDescription, color = if (low) Warning else TextMuted, fontSize = 12.sp)
                if (low && owner) Text("Stock bajo", color = Warning, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                if (product.subunitsPerUnit == null && product.saleMode == SaleModes.BOTH) Text("Las subunidades no descuentan cajas sin equivalencia definida.", fontSize = 10.sp, color = TextMuted)
            }
            if (owner) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, "Editar producto", tint = Accent) }
                    IconButton(onClick = onAdjust) { Icon(Icons.Outlined.Add, "Ajustar inventario", tint = Success) }
                }
            } else {
                IconButton(onClick = onSell) { Icon(Icons.Outlined.PointOfSale, "Registrar venta", tint = Accent) }
            }
        }
    }
}

@Composable
private fun ProductImage(url: String?, modifier: Modifier = Modifier) {
    if (!url.isNullOrBlank()) {
        AsyncImage(model = url, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
    } else {
        Box(modifier.clip(RoundedCornerShape(14.dp)).background(AccentSoft), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Inventory2, null, tint = Accent, modifier = Modifier.size(32.dp))
        }
    }
}

private fun stockLabel(product: ProductEntity): String {
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
                    FilterChip(selected = mode == SaleModes.SUBUNIT, onClick = { mode = SaleModes.SUBUNIT }, label = { Text("Subunidad") })
                    FilterChip(selected = mode == SaleModes.BOTH, onClick = { mode = SaleModes.BOTH }, label = { Text("Ambos") })
                }
                if (mode == SaleModes.UNIT) {
                    OutlinedTextField(unitName, { unitName = it }, label = { Text("Unidad de venta") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
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
                Text(if (product == null) "Existencias iniciales" else "Existencias iniciales: ${stockLabel(product)}", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                if (product == null) {
                    if (eq.toIntOrNull()?.let { it > 0 } == true) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(units, { units = it }, label = { Text(unitName.ifBlank { "Unidades" }) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            OutlinedTextField(subunits, { subunits = it }, label = { Text(subunitName.ifBlank { "Subunidades sueltas" }) }, modifier = Modifier.weight(1f), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    } else {
                        OutlinedTextField(units, { units = it }, label = { Text(unitName.ifBlank { "Unidades / recipientes" }) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        if (mode != SaleModes.UNIT) Text("Como no se conoce la equivalencia, las ventas por porciones se registrarán sin descontar recipientes automáticamente.", color = TextMuted, fontSize = 11.sp)
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
                if (product != null) Text("El inventario existente se cambia mediante ‘Ajustar inventario’, para conservar un motivo e historial del movimiento.", fontSize = 11.sp, color = TextMuted)
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
                Text("Existencias actuales: ${stockLabel(product)}", color = TextMuted, fontSize = 12.sp)
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
                        Text(stockLabel(product), fontSize = 11.sp, color = TextMuted)
                    }
                }
                if (product.saleMode != SaleModes.SUBUNIT) OutlinedTextField(unitCount, { unitCount = it }, label = { Text("${product.unitName} vendidas") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (product.saleMode != SaleModes.UNIT) OutlinedTextField(subunitCount, { subunitCount = it }, label = { Text("${product.subunitName ?: "Subunidades"} vendidas") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                if (product.subunitsPerUnit == null && product.saleMode == SaleModes.BOTH) InfoPanel("Inventario por porciones", "No se conoce la equivalencia entre el recipiente y las porciones. La venta y su importe se guardarán, pero las porciones no descontarán automáticamente recipientes.", PanelType.INFO)
                product.subunitsPerUnit?.takeIf { it > 0 }?.let { equivalence ->
                    if (subunits > 0) {
                        val normalizedUnits = units + subunits / equivalence
                        val normalizedSubunits = subunits % equivalence
                        Text("Para el inventario, esta cantidad equivale a $normalizedUnits ${product.unitName} y $normalizedSubunits ${product.subunitName ?: "subunidades"}.", color = Accent, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
                Text("Método de pago", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = method == PaymentMethods.CASH_CUP, onClick = { method = PaymentMethods.CASH_CUP })
                    Column(Modifier.weight(1f)) { Text("Efectivo CUP"); Text("Precio según catálogo", color = TextMuted, fontSize = 11.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = method == PaymentMethods.TRANSFER_CUP, onClick = { method = PaymentMethods.TRANSFER_CUP }, enabled = transferAvailable)
                    Column(Modifier.weight(1f)) { Text("Transferencia"); Text(if (transferAvailable) "Precio según catálogo" else "No hay precio de transferencia para esta cantidad", color = TextMuted, fontSize = 11.sp) }
                }
                HorizontalDivider(color = Color(0xFFE9E6F0))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text("Total", color = TextMuted)
                    Text(formatCup(total), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Accent)
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
                Text("Registro por hora, trabajadora y método de pago", color = TextMuted, fontSize = 12.sp)
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
    val statusColor = when (sale.status) { "SYNCED" -> Success; "NEEDS_REVIEW" -> Color(0xFFB3261E); else -> Warning }
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(sale.summary, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text(sale.workerName, color = TextMuted, fontSize = 11.sp)
                }
                Text(formatCup(sale.totalCUP), fontWeight = FontWeight.Bold, color = Accent)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(formatDateTime(sale.soldAt, timeZoneId), fontSize = 11.sp, color = TextMuted)
                Text(if (sale.paymentMethod == PaymentMethods.TRANSFER_CUP) "Transferencia" else "Efectivo CUP", fontSize = 11.sp, color = TextMuted)
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
    val today = LocalDate.now(businessZone).toString()
    val salesToday = state.sales.filter { runCatching { OffsetDateTime.parse(it.soldAt).atZoneSameInstant(businessZone).toLocalDate().toString() == today }.getOrDefault(false) }
    val cash = salesToday.filter { it.paymentMethod == PaymentMethods.CASH_CUP }.sumOf { it.totalCUP }
    val transfer = salesToday.filter { it.paymentMethod == PaymentMethods.TRANSFER_CUP }.sumOf { it.totalCUP }
    val inventoryCash = state.dashboard?.totalInventoryValueCash
    val inventoryTransfer = state.dashboard?.totalInventoryValueTransfer
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Estadísticas", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Resumen diario · $today", color = TextMuted, fontSize = 12.sp) }
            IconButton(onClick = vm::refreshReports) { Icon(Icons.Outlined.Refresh, "Actualizar estadísticas") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("Efectivo hoy", formatCup(state.dashboard?.cashCup ?: cash), Icons.Outlined.PointOfSale, Modifier.weight(1f))
            SummaryCard("Transferencias hoy", formatCup(state.dashboard?.transferCup ?: transfer), Icons.Outlined.CloudSync, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("Operaciones hoy", (state.dashboard?.saleCount ?: salesToday.size).toString(), Icons.Outlined.Assessment, Modifier.weight(1f))
            SummaryCard("Productos por reponer", (state.dashboard?.lowStockCount ?: state.products.count { it.stockUnits <= it.minimumStock }).toString(), Icons.Outlined.WarningAmber, Modifier.weight(1f), caution = true)
        }
        Text("Valor potencial de existencias", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SummaryCard("Vendiendo en CUP", inventoryCash?.let(::formatCup) ?: "Sin dato", Icons.Outlined.Inventory2, Modifier.weight(1f))
            SummaryCard("Por transferencia", inventoryTransfer?.let(::formatCup) ?: "Sin dato", Icons.Outlined.CloudSync, Modifier.weight(1f))
        }
        InfoPanel("Importante", "Estas cifras representan ventas registradas y valor estimado de venta, no ganancia neta. Para calcular ganancia se debe registrar el costo de compra de cada producto. Las ventas pendientes de sincronización se muestran localmente y no se sumarán como confirmadas por el servidor hasta su aceptación.", PanelType.INFO)
        Text("Ventas locales del día", fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        if (salesToday.isEmpty()) EmptyPanel("Sin operaciones para hoy", "El historial del día aparecerá aquí.") else salesToday.forEach { SaleRow(it, state.session?.businessTimeZone) }
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
                Text(if (owner) "Publica novedades ahora o programa su hora." else "Novedades y ofertas publicadas por la cafetería.", color = TextMuted, fontSize = 12.sp)
            }
            IconButton(onClick = vm::refreshAnnouncements) { Icon(Icons.Outlined.Refresh, "Actualizar avisos") }
        }
        if (owner) Button(onClick = { showCreate = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Crear aviso u oferta") }
        if (state.announcements.isEmpty()) EmptyPanel("No hay avisos para mostrar", "Los anuncios publicados o programados aparecerán aquí cuando el servicio esté conectado.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 12.dp)) {
            items(state.announcements, key = { it.id }) { announcement ->
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(announcement.title, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Text(announcement.message, fontSize = 14.sp)
                        Text("Publicación: ${formatDateTime(announcement.publishAt, state.session?.businessTimeZone)}", fontSize = 11.sp, color = TextMuted)
                        announcement.expiresAt?.let { Text("Vence: ${formatDateTime(it, state.session?.businessTimeZone)}", fontSize = 11.sp, color = TextMuted) }
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
                Text("El servidor deberá publicar el aviso a esta hora aunque el teléfono de la dueña esté apagado.", fontSize = 11.sp, color = TextMuted)
                HorizontalDivider(color = Color(0xFFE9E6F0))
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
            Column(Modifier.weight(1f)) { Text("Equipo", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Cada trabajadora tiene su propio acceso.", color = TextMuted, fontSize = 12.sp) }
            IconButton(onClick = vm::refreshTeam) { Icon(Icons.Outlined.Refresh, "Actualizar equipo") }
        }
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(8.dp)); Text("Añadir trabajadora") }
        if (state.team.isEmpty()) EmptyPanel("No hay cuentas de trabajadoras cargadas", "Añade una cuenta cuando el servicio esté conectado.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(state.team, key = { it.id }) { member ->
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(42.dp).clip(CircleShape).background(AccentSoft), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Group, null, tint = Accent) }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(member.displayName, fontWeight = FontWeight.SemiBold)
                            Text(member.identifier, fontSize = 12.sp, color = TextMuted)
                            Text(if (member.active) "Cuenta activa" else "Cuenta desactivada", fontSize = 11.sp, color = if (member.active) Success else MaterialTheme.colorScheme.error)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(member.role, fontSize = 10.sp, color = TextMuted)
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
                OutlinedTextField(identifier, { identifier = it }, label = { Text("Usuario o correo") }, modifier = Modifier.fillMaxWidth())
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
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Catálogo", fontSize = 24.sp, fontWeight = FontWeight.Bold); Text("Consulta qué hay disponible antes de salir.", color = TextMuted, fontSize = 12.sp) }
            IconButton(onClick = vm::refreshProducts) { Icon(Icons.Outlined.Refresh, "Actualizar catálogo") }
        }
        OutlinedTextField(state.search, vm::setSearch, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Outlined.Search, null) }, label = { Text("Buscar productos") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        val context = LocalContext.current
        val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted || Build.VERSION.SDK_INT < 33) vm.enableNotifications()
        }
        OutlinedButton(onClick = {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else vm.enableNotifications()
        }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.NotificationsActive, null); Spacer(Modifier.width(8.dp)); Text("Activar notificaciones de ofertas")
        }
        val publicProducts = state.products.filter { it.publicVisible && (state.search.isBlank() || it.name.contains(state.search, true) || it.categoryName.contains(state.search, true)) }
        if (publicProducts.isEmpty()) EmptyPanel("No hay productos para mostrar", "El catálogo se actualizará cuando el servicio esté disponible. No mostramos productos ficticios.")
        else LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(publicProducts, key = { it.id }) { product ->
                Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.Top) {
                        ProductImage(product.imageUrl, Modifier.size(92.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text(product.categoryName, fontSize = 12.sp, color = TextMuted)
                            if (product.unitPriceCup != null) Text("${formatCup(product.unitPriceCup)} / ${product.unitName}", fontWeight = FontWeight.SemiBold, color = Accent)
                            else if (product.subunitPriceCup != null) Text("${formatCup(product.subunitPriceCup)} / ${product.subunitName ?: "subunidad"}", fontWeight = FontWeight.SemiBold, color = Accent)
                            product.subunitPriceCup?.let { Text("${formatCup(it)} / ${product.subunitName ?: "subunidad"}", fontSize = 12.sp, color = TextMuted) }
                            product.subunitPriceTransfer?.let { Text("${formatCup(it)} por transferencia · ${product.subunitName ?: "subunidad"}", fontSize = 12.sp, color = TextMuted) }
                            product.unitPriceTransfer?.let { Text("${formatCup(it)} por transferencia", fontSize = 12.sp, color = TextMuted) }
                            Text(if (product.stockUnits > 0 || (product.stockSubunits ?: 0L) > 0) "Disponible" else "Consultar disponibilidad", fontSize = 12.sp, color = if (product.stockUnits > 0 || (product.stockSubunits ?: 0L) > 0) Success else Warning)
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
    val bg = when (type) { PanelType.INFO -> Color(0xFFF0EEFA); PanelType.SUCCESS -> Color(0xFFE7F6EE); PanelType.ERROR -> Color(0xFFFFEAE8) }
    val fg = when (type) { PanelType.INFO -> Color(0xFF51458C); PanelType.SUCCESS -> Color(0xFF176845); PanelType.ERROR -> Color(0xFF9C2821) }
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
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Outlined.Inventory2, null, tint = Accent, modifier = Modifier.size(36.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(message, color = TextMuted, fontSize = 12.sp, lineHeight = 17.sp)
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
