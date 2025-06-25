// Refactored to open NoteEditor as separate screen using Navigation
package com.example.gymtrack

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.gymtrack.ui.theme.GymTrackTheme
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settingsState = remember { mutableStateOf(Settings()) }
            GymTrackTheme(darkTheme = settingsState.value.darkMode) {
                val navController = rememberNavController()
                NavigationHost(navController, settingsState)
            }
        }
    }
}

@Composable
fun NavigationHost(navController: NavHostController, settingsState: MutableState<Settings>) {
    val context = LocalContext.current
    val db = remember { NoteDatabase.getDatabase(context) }
    val dao = db.noteDao()
    var notes by remember { mutableStateOf(listOf<NoteLine>()) }
    var selectedNotes by remember { mutableStateOf(setOf<NoteLine>()) }
    var currentNote by remember { mutableStateOf<NoteLine?>(null) }

    LaunchedEffect(Unit) {
        val retrieved = withContext(Dispatchers.IO) { dao.getAll() }
        notes = retrieved.map { NoteLine(it.text, it.timestamp) }
    }

    NavHost(navController = navController, startDestination = "main") {
        composable("main") {
            NotesScreen(
                notes = notes,
                selectedNotes = selectedNotes,
                onSelect = { selectedNotes = it },
                onEdit = {
                    currentNote = it
                    navController.navigate("edit")
                },
                onDelete = { toDelete ->
                    CoroutineScope(Dispatchers.IO).launch {
                        toDelete.forEach { dao.delete(NoteEntity(it.timestamp, it.text)) }
                        withContext(Dispatchers.Main) {
                            notes = notes.filterNot { it in toDelete }
                            selectedNotes = emptySet()
                        }
                    }
                },
                onCreate = {
                    currentNote = null
                    navController.navigate("edit")
                },
                onOpenSettings = { navController.navigate("settings") },
                settings = settingsState.value
            )
        }
        composable("edit") {
            NoteEditor(
                note = currentNote,
                settings = settingsState.value,
                onSave = { text ->
                    val updated = currentNote?.copy(text = text) ?: NoteLine(text, System.currentTimeMillis())
                    CoroutineScope(Dispatchers.IO).launch {
                        dao.insert(NoteEntity(updated.timestamp, updated.text))
                        withContext(Dispatchers.Main) {
                            notes = notes.filter { it.timestamp != updated.timestamp } + updated
                            navController.popBackStack()
                        }
                    }
                },
                onCancel = { navController.popBackStack() }
            )
        }
        composable("settings") {
            SettingsScreen(
                settings = settingsState.value,
                onChange = { settingsState.value = it },
                onBack = { navController.popBackStack() }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    notes: List<NoteLine>,
    selectedNotes: Set<NoteLine>,
    onSelect: (Set<NoteLine>) -> Unit,
    onEdit: (NoteLine) -> Unit,
    onDelete: (Set<NoteLine>) -> Unit,
    onCreate: () -> Unit,
    onOpenSettings: () -> Unit,
    settings: Settings
) {
    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (selectedNotes.isEmpty()) {
                FloatingActionButton(
                    onClick = onCreate,
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
                }
            }
        },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.smallTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                title = {
                    Text(
                        if (selectedNotes.isNotEmpty()) "${selectedNotes.size} selected" else "Notes",
                        fontSize = 20.sp
                    )
                },
                actions = {
                    if (selectedNotes.isNotEmpty()) {
                        IconButton(onClick = { onDelete(selectedNotes) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().padding(8.dp)
        ) {
            itemsIndexed(notes) { _, note ->
                val isSelected = selectedNotes.contains(note)
                Card(
                    modifier = Modifier
                        .padding(6.dp)
                        .fillMaxWidth()
                        .combinedClickable(
                            onClick = {
                                if (selectedNotes.isNotEmpty()) {
                                    onSelect(selectedNotes.toMutableSet().also {
                                        if (it.contains(note)) it.remove(note) else it.add(note)
                                    })
                                } else {
                                    onEdit(note)
                                }
                            },
                            onLongClick = {
                                onSelect(selectedNotes.toMutableSet().also { it.add(note) })
                            }
                        ),
                    shape = MaterialTheme.shapes.medium,
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(note.text.lines().firstOrNull() ?: "No text", fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatRelativeTime(note.timestamp, settings),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NoteEditor(note: NoteLine?, settings: Settings, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var fieldValue by remember { mutableStateOf(TextFieldValue(note?.text ?: "")) }
    var lastEnter by remember { mutableStateOf(System.currentTimeMillis()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null) }
            IconButton(onClick = { onSave(fieldValue.text) }) { Icon(Icons.Default.Check, null) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                // Detect Enter pressed at the end of the text
                if (newValue.text.length > fieldValue.text.length &&
                    newValue.text.endsWith("\n")) {
                    val now = System.currentTimeMillis()
                    val diffSec = (now - lastEnter) / 1000
                    lastEnter = now

                    val lines = fieldValue.text.split('\n').toMutableList()
                    if (lines.isNotEmpty()) {
                        val lastIndex = lines.lastIndex
                        val time = formatRoundedTime(now, settings)
                        lines[lastIndex] = lines[lastIndex] + " (" + diffSec + "s) " + time
                    }
                    val updated = lines.joinToString("\n") + "\n"
                    fieldValue = TextFieldValue(updated, TextRange(updated.length))
                } else {
                    fieldValue = newValue
                }
            },
            visualTransformation = RestTimeVisualTransformation(),
            modifier = Modifier.fillMaxSize(),
            placeholder = { Text("Start typing") },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(fieldValue.text) }),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
            )
        )
    }
}

class RestTimeVisualTransformation : VisualTransformation {
    private val regex = "\\(\\d+s\\)".toRegex()
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)
        regex.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = Color.LightGray), match.range.first, match.range.last + 1)
        }
        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit, onBack: () -> Unit) {
    var is24 by remember { mutableStateOf(settings.is24Hour) }
    var rounding by remember { mutableStateOf(settings.roundingSeconds.toString()) }
    var dark by remember { mutableStateOf(settings.darkMode) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("24-hour format", modifier = Modifier.weight(1f))
                Switch(checked = is24, onCheckedChange = {
                    is24 = it
                    onChange(settings.copy(is24Hour = it, roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds, darkMode = dark))
                })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark mode", modifier = Modifier.weight(1f))
                Switch(checked = dark, onCheckedChange = {
                    dark = it
                    onChange(settings.copy(is24Hour = is24, roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds, darkMode = it))
                })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = rounding,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() }
                    rounding = filtered
                    onChange(settings.copy(is24Hour = is24, roundingSeconds = filtered.toIntOrNull() ?: settings.roundingSeconds, darkMode = dark))
                },
                label = { Text("Rounding seconds") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
    }
}

fun formatRelativeTime(timestamp: Long, settings: Settings): String {
    val now = System.currentTimeMillis()
    val date = Date(timestamp)
    val pattern = if (settings.is24Hour) "HH:mm" else "hh:mm a"
    val fullPattern = if (settings.is24Hour) "MMM dd HH:mm" else "MMM dd hh:mm a"
    val format = SimpleDateFormat(fullPattern, Locale.getDefault())
    val timeFormat = SimpleDateFormat(pattern, Locale.getDefault())
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 86_400_000 -> timeFormat.format(date)
        diff < 172_800_000 -> "Yesterday " + timeFormat.format(date)
        else -> format.format(date)
    }
}

fun formatRoundedTime(timestamp: Long, settings: Settings): String {
    val rounding = settings.roundingSeconds.coerceAtLeast(1) * 1000L
    val rounded = ((timestamp + rounding / 2) / rounding) * rounding
    val pattern = if (settings.is24Hour) "HH:mm:ss" else "hh:mm:ss a"
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(Date(rounded))
}

data class Settings(
    val is24Hour: Boolean = true,
    val roundingSeconds: Int = 15,
    val darkMode: Boolean = true
)

data class NoteLine(val text: String, val timestamp: Long)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val timestamp: Long,
    val text: String
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp ASC")
    suspend fun getAll(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)
}

@Database(entities = [NoteEntity::class], version = 1)
abstract class NoteDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao

    companion object {
        @Volatile private var INSTANCE: NoteDatabase? = null

        fun getDatabase(context: Context): NoteDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    NoteDatabase::class.java,
                    "note_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
