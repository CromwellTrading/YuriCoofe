package com.cafeteria.gestion.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cafeteria.gestion.CafeteriaApplication
import com.cafeteria.gestion.data.*
import com.cafeteria.gestion.data.local.ProductEntity
import com.cafeteria.gestion.data.remote.CreateAnnouncementRequest
import com.cafeteria.gestion.data.remote.CreateWorkerRequest
import com.cafeteria.gestion.data.remote.InventoryAdjustmentRequest
import com.cafeteria.gestion.sync.SalesSyncScheduler
import kotlinx.coroutines.launch

const val SCREEN_HOME = "HOME"
const val SCREEN_PRODUCTS = "PRODUCTS"
const val SCREEN_SELL = "SELL"
const val SCREEN_SALES = "SALES"
const val SCREEN_REPORTS = "REPORTS"
const val SCREEN_ANNOUNCEMENTS = "ANNOUNCEMENTS"
const val SCREEN_TEAM = "TEAM"
const val SCREEN_CUSTOMER_CATALOG = "CUSTOMER_CATALOG"

data class CafeteriaUiState(
    val loading: Boolean = true,
    val busy: Boolean = false,
    val session: UserSession? = null,
    val products: List<ProductEntity> = emptyList(),
    val sales: List<SaleSummary> = emptyList(),
    val pendingCount: Int = 0,
    val dashboard: DashboardSummary? = null,
    val announcements: List<Announcement> = emptyList(),
    val team: List<TeamMember> = emptyList(),
    val screen: String = SCREEN_HOME,
    val message: String? = null,
    val error: String? = null,
    val apiConfigured: Boolean = false,
    val online: Boolean = false,
    val search: String = "",
    val category: String = "Todas",
    val sortBy: String = "Nombre",
    val ascending: Boolean = true
)

class CafeteriaViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as CafeteriaApplication
    private val repository = app.repository
    var state = kotlinx.coroutines.flow.MutableStateFlow(
        CafeteriaUiState(apiConfigured = repository.isBackendConfigured())
    )
        private set

    init {
        viewModelScope.launch {
            val session = runCatching { repository.restoreSession() }.getOrNull()
            val cachedProducts = runCatching { repository.getCachedProducts() }.getOrDefault(emptyList())
            val localSales = runCatching { repository.getRecentSales() }.getOrDefault(emptyList())
            val pending = runCatching { repository.pendingCount() }.getOrDefault(0)
            state.value = state.value.copy(
                loading = false,
                session = session,
                products = cachedProducts,
                sales = localSales,
                pendingCount = pending,
                screen = when (session?.role) {
                    Roles.OWNER -> SCREEN_HOME
                    Roles.WORKER -> SCREEN_SELL
                    Roles.CUSTOMER -> SCREEN_CUSTOMER_CATALOG
                    else -> SCREEN_HOME
                },
                online = repository.isNetworkAvailable()
            )
            if (session != null) {
                if (session.role != Roles.CUSTOMER) app.registerPushTokenIfConfigured()
                refreshAll()
            }
        }
    }

    fun clearMessage() { state.value = state.value.copy(message = null, error = null) }
    fun setScreen(screen: String) { state.value = state.value.copy(screen = screen, message = null, error = null) }
    fun setSearch(value: String) { state.value = state.value.copy(search = value) }
    fun setCategory(value: String) { state.value = state.value.copy(category = value) }
    fun setSort(value: String) { state.value = state.value.copy(sortBy = value) }
    fun toggleSortDirection() { state.value = state.value.copy(ascending = !state.value.ascending) }

    fun login(identifier: String, password: String) = runBusy("No se pudo iniciar sesión.") {
        require(identifier.isNotBlank()) { "Escribe tu usuario o correo." }
        require(password.isNotBlank()) { "Escribe tu contraseña." }
        val session = repository.login(identifier, password)
        if (session.role != Roles.CUSTOMER) app.registerPushTokenIfConfigured()
        state.value = state.value.copy(
            session = session,
            screen = when (session.role) {
                Roles.OWNER -> SCREEN_HOME
                Roles.WORKER -> SCREEN_SELL
                else -> SCREEN_CUSTOMER_CATALOG
            },
            message = "Sesión iniciada.",
            error = null
        )
        refreshAll()
    }

    fun openCatalogAsGuest() {
        viewModelScope.launch {
            val guest = UserSession(
                userId = "guest-${java.util.UUID.randomUUID()}",
                displayName = "Cliente",
                role = Roles.CUSTOMER,
                accessToken = ""
            )
            repository.sessionStore.save(guest)
            state.value = state.value.copy(session = guest, screen = SCREEN_CUSTOMER_CATALOG, message = null, error = null)
            refreshAll()
        }
    }

    fun enableNotifications() {
        val configured = app.registerPushTokenIfConfigured()
        state.value = state.value.copy(message = if (configured) "Se solicitó el registro de este teléfono para recibir avisos." else "Las notificaciones todavía necesitan configurar el servicio de mensajería y su conexión con el servidor.")
    }

    fun logout() = runBusy("No se pudo cerrar la sesión.") {
        repository.logout()
        state.value = state.value.copy(session = null, message = "Sesión cerrada.", screen = SCREEN_HOME)
    }

    fun refreshAll() {
        viewModelScope.launch {
            state.value = state.value.copy(online = repository.isNetworkAvailable())
            refreshProductsQuietly()
            refreshSalesQuietly()
            refreshAnnouncementsQuietly()
            if (state.value.session?.role == Roles.OWNER) {
                val (from, to) = todayRange()
                runCatching { repository.loadDashboard(from, to) }.onSuccess { state.value = state.value.copy(dashboard = it) }
                runCatching { repository.loadTeam() }.onSuccess { state.value = state.value.copy(team = it) }
            }
        }
    }

    fun refreshProducts() = runBusy("No se pudo actualizar el catálogo.") {
        val list = repository.refreshProducts()
        state.value = state.value.copy(products = list, message = "Catálogo actualizado.")
    }

    private suspend fun refreshProductsQuietly() {
        if (!repository.isBackendConfigured() || !repository.isNetworkAvailable()) return
        runCatching { repository.refreshProducts() }.onSuccess { state.value = state.value.copy(products = it) }
    }

    fun recordSale(product: ProductEntity, units: Long, subunits: Long, method: String, onSaved: () -> Unit = {}) = runBusy("No se pudo guardar la venta.") {
        repository.recordSale(product, units, subunits, method)
        state.value = state.value.copy(
            products = repository.getCachedProducts(),
            sales = repository.getRecentSales(),
            pendingCount = repository.pendingCount(),
            message = "Venta guardada con su hora original. Se sincronizará al tener conexión.",
            error = null
        )
        SalesSyncScheduler.enqueueNow(getApplication())
        onSaved()
    }

    fun retrySale(saleId: String) = runBusy("No se pudo reintentar la venta.") {
        repository.retrySale(saleId)
        state.value = state.value.copy(sales = repository.getRecentSales(), pendingCount = repository.pendingCount(), message = "Se volverá a enviar la venta con la misma fecha e identificador.")
    }

    fun saveProduct(draft: ProductDraft, onSaved: () -> Unit = {}) = runBusy("No se pudo guardar el producto.") {
        val product = repository.saveProduct(draft)
        state.value = state.value.copy(products = repository.getCachedProducts(), message = "Producto guardado: ${product.name}.")
        onSaved()
    }

    fun adjustInventory(productId: String, unitsDelta: Long, subunitsDelta: Long?, reason: String, onSaved: () -> Unit = {}) = runBusy("No se pudo ajustar el inventario.") {
        require(reason.isNotBlank()) { "Indica el motivo del ajuste." }
        repository.submitInventoryAdjustment(InventoryAdjustmentRequest(productId, unitsDelta, subunitsDelta, reason.trim()))
        state.value = state.value.copy(products = repository.getCachedProducts(), message = "Inventario actualizado.")
        onSaved()
    }

    fun refreshSales() = runBusy("No se pudo actualizar el historial.") {
        refreshSalesMerged()
        state.value = state.value.copy(message = "Historial actualizado.")
    }

    private suspend fun refreshSalesQuietly() {
        runCatching { refreshSalesMerged() }
    }

    private suspend fun refreshSalesMerged() {
        val local = repository.getRecentSales()
        val remote = if (repository.isBackendConfigured() && repository.isNetworkAvailable()) {
            runCatching { repository.getRemoteSales() }.getOrDefault(emptyList())
        } else emptyList()
        val merged = (remote + local).distinctBy { it.id }.sortedByDescending { it.soldAt }
        state.value = state.value.copy(sales = merged, pendingCount = repository.pendingCount())
    }

    fun refreshReports() = runBusy("No se pudieron cargar las estadísticas.") {
        val (from, to) = todayRange()
        val summary = repository.loadDashboard(from, to)
        state.value = state.value.copy(dashboard = summary, message = "Estadísticas actualizadas.")
    }

    private suspend fun refreshAnnouncementsQuietly() {
        if (!repository.isBackendConfigured() || !repository.isNetworkAvailable()) return
        runCatching { repository.refreshAnnouncements() }.onSuccess { state.value = state.value.copy(announcements = it) }
    }

    fun refreshAnnouncements() = runBusy("No se pudieron cargar los avisos.") {
        state.value = state.value.copy(announcements = repository.refreshAnnouncements(), message = "Avisos actualizados.")
    }

    fun publishAnnouncement(title: String, message: String, publishAt: String, expiresAt: String?, onSaved: () -> Unit = {}) = runBusy("No se pudo publicar el aviso.") {
        require(title.isNotBlank()) { "Escribe un título." }
        require(message.isNotBlank()) { "Escribe el contenido del aviso." }
        val parsed = runCatching { java.time.OffsetDateTime.parse(publishAt) }.getOrNull()
            ?: throw IllegalArgumentException("La fecha debe incluir zona horaria, por ejemplo 2026-10-27T08:00:00-04:00.")
        val expiry = expiresAt?.takeIf { it.isNotBlank() }?.let {
            runCatching { java.time.OffsetDateTime.parse(it) }.getOrNull()
                ?: throw IllegalArgumentException("La fecha de vencimiento no tiene un formato válido.")
        }
        if (expiry != null) require(expiry.isAfter(parsed)) { "El vencimiento debe ser posterior a la publicación." }
        val created = repository.publishAnnouncement(CreateAnnouncementRequest(title.trim(), message.trim(), parsed.toString(), expiry?.toString(), true))
        state.value = state.value.copy(announcements = listOf(created) + state.value.announcements, message = "Aviso guardado.")
        onSaved()
    }

    fun cancelAnnouncement(id: String) = runBusy("No se pudo cancelar el aviso.") {
        repository.cancelAnnouncement(id)
        state.value = state.value.copy(announcements = state.value.announcements.filterNot { it.id == id }, message = "Aviso cancelado.")
    }

    fun refreshTeam() = runBusy("No se pudo cargar el equipo.") {
        state.value = state.value.copy(team = repository.loadTeam(), message = "Equipo actualizado.")
    }

    fun addWorker(name: String, identifier: String, password: String, onSaved: () -> Unit = {}) = runBusy("No se pudo crear la cuenta.") {
        require(name.isNotBlank()) { "Escribe el nombre de la trabajadora." }
        require(identifier.isNotBlank()) { "Escribe un usuario o correo." }
        require(password.length >= 8) { "La contraseña temporal debe tener al menos 8 caracteres." }
        val created = repository.createWorker(CreateWorkerRequest(name.trim(), identifier.trim(), password))
        state.value = state.value.copy(team = listOf(created) + state.value.team, message = "Cuenta creada para ${created.displayName}.")
        onSaved()
    }

    fun setWorkerActive(workerId: String, active: Boolean) = runBusy("No se pudo actualizar la cuenta.") {
        val updated = repository.setWorkerActive(workerId, active)
        state.value = state.value.copy(
            team = state.value.team.map { if (it.id == updated.id) updated else it },
            message = if (updated.active) "Cuenta activada." else "Cuenta desactivada."
        )
    }

    fun syncNow() {
        SalesSyncScheduler.enqueueNow(getApplication())
        viewModelScope.launch {
            state.value = state.value.copy(online = repository.isNetworkAvailable())
            refreshSalesMerged()
            state.value = state.value.copy(message = "Sincronización solicitada. Las ventas se conservan hasta recibir confirmación del servicio.")
        }
    }

    private fun todayRange(): Pair<String, String> {
        val zone = java.time.ZoneId.of(state.value.session?.businessTimeZone ?: "America/Havana")
        val today = java.time.LocalDate.now(zone)
        val from = today.atStartOfDay(zone).toOffsetDateTime().toString()
        val to = today.plusDays(1).atStartOfDay(zone).toOffsetDateTime().toString()
        return from to to
    }

    private fun runBusy(defaultError: String, action: suspend () -> Unit) {
        viewModelScope.launch {
            state.value = state.value.copy(busy = true, error = null, message = null)
            try {
                action()
            } catch (e: Exception) {
                state.value = state.value.copy(error = e.message?.takeIf { it.isNotBlank() } ?: defaultError)
            } finally {
                state.value = state.value.copy(busy = false, online = repository.isNetworkAvailable())
                val cached = runCatching { repository.getCachedProducts() }.getOrNull()
                if (cached != null) state.value = state.value.copy(products = cached)
                state.value = state.value.copy(pendingCount = runCatching { repository.pendingCount() }.getOrDefault(state.value.pendingCount))
            }
        }
    }
}
