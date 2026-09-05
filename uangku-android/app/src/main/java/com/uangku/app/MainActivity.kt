package com.uangku.app

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

private val RupiahLocale = Locale("id", "ID")
private fun money(value: Long): String = NumberFormat.getCurrencyInstance(RupiahLocale).apply { maximumFractionDigits = 0 }.format(value)
private fun today(): String = SimpleDateFormat("dd MMM yyyy", RupiahLocale).format(Date())

private enum class TxType { INCOME, EXPENSE }
private data class Transaction(
    val id: Long,
    val type: TxType,
    val amount: Long,
    val category: String,
    val note: String,
    val date: String
)

private class TransactionStore(context: Context) {
    private val prefs = context.getSharedPreferences("uangku_store", Context.MODE_PRIVATE)
    fun load(): List<Transaction> {
        val raw = prefs.getString("transactions", "[]") ?: "[]"
        val a = JSONArray(raw)
        return buildList {
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                add(Transaction(o.getLong("id"), TxType.valueOf(o.getString("type")), o.getLong("amount"), o.getString("category"), o.optString("note"), o.getString("date")))
            }
        }.sortedByDescending { it.id }
    }
    fun save(items: List<Transaction>) {
        val a = JSONArray()
        items.forEach { t ->
            a.put(JSONObject().apply {
                put("id", t.id); put("type", t.type.name); put("amount", t.amount)
                put("category", t.category); put("note", t.note); put("date", t.date)
            })
        }
        prefs.edit().putString("transactions", a.toString()).apply()
    }
}

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = TransactionStore(app)
    var transactions by mutableStateOf(store.load())
        private set
    var selectedTab by mutableIntStateOf(0)
    var showAdd by mutableStateOf(false)
    var editing by mutableStateOf<Transaction?>(null)
        private set

    fun save(type: TxType, amount: Long, category: String, note: String) {
        val current = editing
        val item = Transaction(current?.id ?: System.currentTimeMillis(), type, amount, category, note, current?.date ?: today())
        transactions = (if (current == null) listOf(item) + transactions else transactions.map { if (it.id == current.id) item else it }).sortedByDescending { it.id }
        store.save(transactions)
        editing = null
        showAdd = false
    }
    fun edit(t: Transaction) { editing = t; showAdd = true }
    fun newTransaction() { editing = null; showAdd = true }
    fun delete(t: Transaction) { transactions = transactions.filterNot { it.id == t.id }; store.save(transactions) }
    fun closeEditor() { showAdd = false; editing = null }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { UangKuApp() }
    }
}

@Composable
fun UangKuApp(vm: MainViewModel = viewModel()) {
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF186F5B), secondary = Color(0xFFE39B35), background = Color(0xFFF6F8F7))) {
        Scaffold(
            topBar = { TopAppBar(title = { Text("UangKu", fontWeight = FontWeight.Bold) }) },
            bottomBar = {
                NavigationBar {
                    listOf("Ringkasan" to Icons.Default.Home, "Transaksi" to Icons.Default.List, "Statistik" to Icons.Default.BarChart).forEachIndexed { i, pair ->
                        NavigationBarItem(selected = vm.selectedTab == i, onClick = { vm.selectedTab = i }, icon = { Icon(pair.second, null) }, label = { Text(pair.first) })
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(onClick = vm::newTransaction, containerColor = MaterialTheme.colorScheme.primary) {
                    Icon(Icons.Default.Add, contentDescription = "Tambah transaksi", tint = Color.White)
                }
            }
        ) { pad ->
            Box(Modifier.fillMaxSize().padding(pad)) {
                AnimatedContent(vm.selectedTab, label = "tab") { tab ->
                    when (tab) {
                        0 -> Dashboard(vm.transactions)
                        1 -> TransactionsScreen(vm.transactions, vm::edit, vm::delete)
                        else -> StatsScreen(vm.transactions)
                    }
                }
            }
        }
        if (vm.showAdd) AddTransactionSheet(vm.editing, onDismiss = vm::closeEditor, onSave = vm::save)
    }
}

@Composable
private fun Dashboard(items: List<Transaction>) {
    val income = items.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val expense = items.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Keuangan Anda", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("Pantau pemasukan dan pengeluaran harian dengan mudah.", color = Color.Gray)
        Spacer(Modifier.height(18.dp))
        Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(22.dp)) {
                Text("Saldo saat ini", color = Color.White.copy(alpha=.8f))
                Text(money(income - expense), color = Color.White, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Metric("Pemasukan", money(income), Icons.Default.TrendingUp)
                    Metric("Pengeluaran", money(expense), Icons.Default.TrendingDown)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Transaksi terbaru", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        if (items.isEmpty()) EmptyState() else items.take(5).forEach { TransactionRow(it, {}, {}) }
    }
}

@Composable private fun Metric(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.White); Spacer(Modifier.width(8.dp))
        Column { Text(title, color = Color.White.copy(alpha=.75f), style = MaterialTheme.typography.labelMedium); Text(value, color = Color.White, fontWeight = FontWeight.SemiBold) }
    }
}

@Composable
private fun TransactionsScreen(items: List<Transaction>, onEdit: (Transaction) -> Unit, onDelete: (Transaction) -> Unit) {
    var query by remember { mutableStateOf("") }
    val filtered = items.filter { it.category.contains(query, true) || it.note.contains(query, true) }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Semua Transaksi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(query, { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true, leadingIcon = { Icon(Icons.Default.Search, null) }, placeholder = { Text("Cari transaksi...") }, shape = RoundedCornerShape(16.dp))
        Spacer(Modifier.height(14.dp))
        if (filtered.isEmpty()) EmptyState() else LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(bottom = 90.dp)) { items(filtered, key = { it.id }) { TransactionRow(it, { onEdit(it) }, { onDelete(it) }) } }
    }
}

@Composable
private fun StatsScreen(items: List<Transaction>) {
    val income = items.filter { it.type == TxType.INCOME }.sumOf { it.amount }
    val expense = items.filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val byCat = items.filter { it.type == TxType.EXPENSE }.groupBy { it.category }.mapValues { e -> e.value.sumOf { it.amount } }.toList().sortedByDescending { it.second }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Text("Statistik", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        SummaryCard("Total pemasukan", money(income), Icons.Default.TrendingUp)
        Spacer(Modifier.height(10.dp)); SummaryCard("Total pengeluaran", money(expense), Icons.Default.TrendingDown)
        Spacer(Modifier.height(20.dp)); Text("Pengeluaran per kategori", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        if (byCat.isEmpty()) Text("Belum ada data.", color = Color.Gray) else byCat.forEach { (cat, amount) -> Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(cat); Text(money(amount), fontWeight = FontWeight.SemiBold) } }
    }
}

@Composable private fun SummaryCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) { Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp)); Column { Text(title, color = Color.Gray); Text(value, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleLarge) } } }
}

@Composable
private fun TransactionRow(t: Transaction, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = if (t.type == TxType.INCOME) Color(0xFFDDF5EC) else Color(0xFFFFE5E2)) { Icon(if (t.type == TxType.INCOME) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward, null, Modifier.padding(10.dp), tint = if (t.type == TxType.INCOME) Color(0xFF13795B) else Color(0xFFD44B45)) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(t.category, fontWeight = FontWeight.SemiBold); Text(t.note.ifBlank { t.date }, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Text((if (t.type == TxType.INCOME) "+" else "-") + money(t.amount), fontWeight = FontWeight.Bold)
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit") }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, "Hapus") }
        }
    }
}

@Composable private fun EmptyState() { Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Default.AccountBalanceWallet, null, modifier = Modifier.size(48.dp), tint = Color.LightGray); Spacer(Modifier.height(8.dp)); Text("Belum ada transaksi", fontWeight = FontWeight.SemiBold); Text("Tekan tombol + untuk mencatat transaksi.", color = Color.Gray) } } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTransactionSheet(existing: Transaction?, onDismiss: () -> Unit, onSave: (TxType, Long, String, String) -> Unit) {
    var type by remember(existing) { mutableStateOf(existing?.type ?: TxType.EXPENSE) }
    var amount by remember(existing) { mutableStateOf(existing?.amount?.toString() ?: "") }
    var category by remember(existing) { mutableStateOf(existing?.category ?: "Makanan") }
    var note by remember(existing) { mutableStateOf(existing?.note ?: "") }
    val cats = listOf("Makanan", "Transportasi", "Belanja", "Tagihan", "Pendidikan", "Kesehatan", "Hiburan", "Lainnya")
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp).navigationBarsPadding()) {
            Text(if (existing == null) "Tambah Transaksi" else "Edit Transaksi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf(TxType.EXPENSE to "Pengeluaran", TxType.INCOME to "Pemasukan").forEach { (t, label) ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(label) }, modifier = Modifier.padding(end = 8.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Nominal (Rp)") }, singleLine = true)
            Spacer(Modifier.height(10.dp)); Text("Kategori", fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { cats.take(4).forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) } }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) { cats.drop(4).forEach { c -> FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c) }) } }
            Spacer(Modifier.height(8.dp)); OutlinedTextField(note, { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Catatan") }, minLines = 2)
            Spacer(Modifier.height(16.dp)); Button(onClick = { amount.toLongOrNull()?.takeIf { it > 0 }?.let { onSave(type, it, category, note) } }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), enabled = amount.toLongOrNull()?.let { it > 0 } == true) { Text("Simpan Transaksi", modifier = Modifier.padding(vertical = 6.dp)) }
            Spacer(Modifier.height(10.dp))
        }
    }
}
