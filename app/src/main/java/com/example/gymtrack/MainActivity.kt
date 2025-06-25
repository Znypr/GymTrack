// Refactored to open NoteEditor as separate screen using Navigation
package com.example.gymtrack

import androidx.compose.ui.graphics.luminance
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import com.example.gymtrack.ui.theme.GymTrackTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.style.TextIndent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.room.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
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
        notes = retrieved.map { NoteLine(it.title, it.text, it.timestamp, it.categoryName, it.categoryColor) }
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
                        toDelete.forEach { dao.delete(NoteEntity(it.timestamp, it.title, it.text, it.categoryName, it.categoryColor)) }
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
                onSave = { title, text, category ->
                    val updated = currentNote?.copy(title = title, text = text, categoryName = category?.name, categoryColor = category?.color) ?: NoteLine(title, text, System.currentTimeMillis(), category?.name, category?.color)
                    CoroutineScope(Dispatchers.IO).launch {
                        dao.insert(NoteEntity(updated.timestamp, updated.title, updated.text, updated.categoryName, updated.categoryColor))
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
        containerColor = MaterialTheme.colorScheme.background,
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
            if (selectedNotes.isEmpty()) {
                CenterAlignedTopAppBar(
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    title = {
                        Text(
                            "GymTrack",
                            fontSize = 24.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            } else {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                    title = {
                        Text(
                            "${'$'}{selectedNotes.size} selected",
                            fontSize = 20.sp
                        )
                    },
                    actions = {
                        IconButton(onClick = { onDelete(selectedNotes) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
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
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                        } else {
                            val base = note.categoryColor?.let { Color(it.toInt()) }
                                ?: if (settings.darkMode) MaterialTheme.colorScheme.surface
                                else MaterialTheme.colorScheme.surfaceVariant
                            if (note.categoryColor == null) {
                                base
                            } else if (settings.darkMode) {
                                base.darken(0.7f)
                            } else {
                                base.lighten(0.1f)
                            }
                        },
                        contentColor = MaterialTheme.colorScheme.onSurface
                    )
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(note.title, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(note.text.lines().firstOrNull() ?: "", fontSize = 14.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            formatFullDateTime(note.timestamp, settings),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteEditor(note: NoteLine?, settings: Settings, onSave: (String, String, Category?) -> Unit, onCancel: () -> Unit) {
    var titleValue by remember { mutableStateOf(TextFieldValue(note?.title ?: "")) }
    var fieldValue by remember { mutableStateOf(TextFieldValue(note?.text ?: "")) }
    var selectedCategory by remember { mutableStateOf<Category?>(settings.categories.find { it.name == note?.categoryName }) }
    var lastEnter by remember { mutableStateOf(System.currentTimeMillis()) }
    val noteTimestamp = note?.timestamp ?: System.currentTimeMillis()

    val lifecycleOwner = LocalLifecycleOwner.current
    var saved by remember { mutableStateOf(false) }
    val saveIfNeeded = {
        if (!saved) {
            saved = true
            onSave(titleValue.text, fieldValue.text, selectedCategory)
        }
    }

    var expanded by remember { mutableStateOf(false) }

    BackHandler { saveIfNeeded() }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                saveIfNeeded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(Modifier.padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = { saveIfNeeded() }) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = { saveIfNeeded() }) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = titleValue,
            onValueChange = { titleValue = it },
            placeholder = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = LocalTextStyle.current.copy(lineHeight = 18.sp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
        Spacer(Modifier.height(8.dp))
        Text(
            formatFullDateTime(noteTimestamp, settings),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(8.dp))
        if (settings.categories.isNotEmpty()) {
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
                OutlinedTextField(
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    readOnly = true,
                    value = selectedCategory?.name ?: "None",
                    onValueChange = {},
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("None") }, onClick = { selectedCategory = null; expanded = false })
                    settings.categories.forEach { cat ->
                        DropdownMenuItem(
                            text = { Text(cat.name) },
                            leadingIcon = { Box(Modifier.size(16.dp).background(Color(cat.color.toInt()))) },
                            onClick = { selectedCategory = cat; expanded = false }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        OutlinedTextField(
            value = fieldValue,
            onValueChange = { newValue ->
                // Detect Enter pressed at the end of the text
                if (newValue.text.length > fieldValue.text.length && newValue.text.endsWith("\n")) {
                    val now = System.currentTimeMillis()
                    val diffSec = (now - lastEnter) / 1000
                    lastEnter = now

                    val lines = fieldValue.text.split('\n').toMutableList()
                    if (lines.isNotEmpty()) {
                        val lastIndex = lines.lastIndex
                        if (lines[lastIndex].isNotBlank()) {
                            val time = formatRoundedTime(now, settings)
                            lines[lastIndex] = lines[lastIndex] + " (" + diffSec + "s) " + time
                        }
                    }
                    val aligned = alignTimestamps(lines)
                    val updated = aligned.joinToString("\n") + "\n"
                    fieldValue = TextFieldValue(updated, TextRange(updated.length))
                }
                else {
                    fieldValue = newValue
                }
            },
            visualTransformation = WorkoutVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Start typing") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            )
        )
    }
}
}

class WorkoutVisualTransformation : VisualTransformation {
    private val timeRegex = "\\(\\d+s\\)".toRegex()
    override fun filter(text: AnnotatedString): TransformedText {
        val builder = AnnotatedString.Builder(text)

        // Highlight rest time parentheses
        timeRegex.findAll(text.text).forEach { match ->
            builder.addStyle(SpanStyle(color = Color.LightGray), match.range.first, match.range.last + 1)

        }

        // Style exercise headings and indent set lines
        val lines = text.text.split('\n')
        var index = 0
        var previousBlank = true


        lines.forEach { line ->
            val end = index + line.length
            if (line.isNotBlank()) {
                if (previousBlank) {
                    // Heading: bigger, bold
                    builder.addStyle(
                        SpanStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold),
                        index,
                        end
                    )
                } else {
                    builder.addStyle(
                        ParagraphStyle(
                            textIndent = TextIndent(firstLine = 14.sp, restLine = 14.sp)
                            ),
                        index,
                        end
                    )
                }
            }
            previousBlank = line.isBlank()
            index = end + 1
        }


        return TransformedText(builder.toAnnotatedString(), OffsetMapping.Identity)
    }
}

fun Color.darken(factor: Float): Color {
    return Color(
        red = (red * (1 - factor)).coerceIn(0f, 1f),
        green = (green * (1 - factor)).coerceIn(0f, 1f),
        blue = (blue * (1 - factor)).coerceIn(0f, 1f),
        alpha = alpha
    )
}

fun Color.lighten(factor: Float): Color {
    return Color(
        red = (red + (1 - red) * factor).coerceIn(0f, 1f),
        green = (green + (1 - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (1 - blue) * factor).coerceIn(0f, 1f),
        alpha = alpha
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(settings: Settings, onChange: (Settings) -> Unit, onBack: () -> Unit) {
    var is24 by remember { mutableStateOf(settings.is24Hour) }
    var rounding by remember { mutableStateOf(settings.roundingSeconds.toString()) }
    var dark by remember { mutableStateOf(settings.darkMode) }
    var categories by remember { mutableStateOf(settings.categories.toMutableList()) }
    var newName by remember { mutableStateOf("") }
    var newColor by remember { mutableStateOf("#FF0000") }

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
                    onChange(settings.copy(is24Hour = it, roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds, darkMode = dark, categories = categories))
                })
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Dark mode", modifier = Modifier.weight(1f))
                Switch(checked = dark, onCheckedChange = {
                    dark = it
                    onChange(settings.copy(is24Hour = is24, roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds, darkMode = it, categories = categories))
                })
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = rounding,
                onValueChange = {
                    val filtered = it.filter { ch -> ch.isDigit() }
                    rounding = filtered
                    onChange(settings.copy(is24Hour = is24, roundingSeconds = filtered.toIntOrNull() ?: settings.roundingSeconds, darkMode = dark, categories = categories))
                },
                label = { Text("Rounding seconds") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(Modifier.height(16.dp))
            Text("Categories", fontWeight = FontWeight.Bold)
            categories.forEachIndexed { index, cat ->
                var name by remember { mutableStateOf(cat.name) }
                var colorText by remember { mutableStateOf(String.format("#%06X", 0xFFFFFF and cat.color.toInt())) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = {
                            name = it
                            categories = categories.toMutableList().also { list ->
                                list[index] = list[index].copy(name = it)
                            }
                            onChange(settings.copy(is24Hour = is24, roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds, darkMode = dark, categories = categories))
                        },
                        modifier = Modifier.weight(1f),
                        label = { Text("Name") }
                    )
                    Spacer(Modifier.width(4.dp))
                    OutlinedTextField(
                        value = colorText,
                        onValueChange = {
                            colorText = it
                            runCatching { it.trimStart('#').toLong(16) or 0xFF000000 }.getOrNull()?.let { clr ->
                                categories = categories.toMutableList().also { list ->
                                    list[index] = list[index].copy(color = clr)
                                }
                                onChange(settings.copy(is24Hour = is24, roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds, darkMode = dark, categories = categories))
                            }
                        },
                        modifier = Modifier.width(100.dp),
                        label = { Text("Color") }
                    )
                    IconButton(onClick = {
                        categories = categories.toMutableList().also { it.removeAt(index) }
                        onChange(settings.copy(is24Hour = is24, roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds, darkMode = dark, categories = categories))
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier.weight(1f),
                    label = { Text("New category") }
                )
                Spacer(Modifier.width(4.dp))
                OutlinedTextField(
                    value = newColor,
                    onValueChange = { newColor = it },
                    modifier = Modifier.width(100.dp),
                    label = { Text("Color") }
                )
                IconButton(onClick = {
                    val color = runCatching { newColor.trimStart('#').toLong(16) or 0xFF000000 }.getOrDefault(0xFFFF0000)
                    categories = (categories + Category(newName.ifBlank { "Category" }, color)).toMutableList()
                    onChange(settings.copy(is24Hour = is24, roundingSeconds = rounding.toIntOrNull() ?: settings.roundingSeconds, darkMode = dark, categories = categories))
                    newName = ""
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    }
}

fun formatRelativeTime(timestamp: Long, settings: Settings): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val date = Date(timestamp)
    val timeFormat = SimpleDateFormat(if (settings.is24Hour) "HH:mm" else "hh:mm a", Locale.getDefault())
    val fullFormat = SimpleDateFormat(if (settings.is24Hour) "MMM dd HH:mm" else "MMM dd hh:mm a", Locale.getDefault())

    return when {
        diff < 60_000 -> "Just now"
        diff < 86_400_000 -> "Today ${timeFormat.format(date)}"
        diff < 172_800_000 -> "Yesterday ${timeFormat.format(date)}"
        else -> fullFormat.format(date)
    }
}

fun formatRoundedTime(timestamp: Long, settings: Settings): String {
    val rounding = settings.roundingSeconds.coerceAtLeast(1) * 1000L
    val rounded = ((timestamp + rounding / 2) / rounding) * rounding
    val pattern = if (settings.is24Hour) "HH:mm:ss" else "hh:mm:ss a"
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(Date(rounded))
}

fun formatFullDateTime(timestamp: Long, settings: Settings): String {
    val pattern = if (settings.is24Hour) "yyyy-MM-dd HH:mm" else "yyyy-MM-dd hh:mm a"
    val format = SimpleDateFormat(pattern, Locale.getDefault())
    return format.format(Date(timestamp))
}

fun alignTimestamps(lines: List<String>): List<String> {
    val timeRegex = "\\d{2}:\\d{2}:\\d{2}(?:\\s[AP]M)?$".toRegex()
    val parsed = lines.map { line ->
        val match = timeRegex.find(line)
        val base = if (match != null) line.substring(0, match.range.first).trimEnd() else line
        Pair(base, match?.value)
    }
    val maxBase = parsed.maxOfOrNull { it.first.length } ?: 0
    return parsed.map { (base, time) ->
        if (time != null) {
            base.padEnd(maxBase) + " " + time
        } else {
            base
        }
    }
}

data class Category(
    val name: String,
    val color: Long
)

data class Settings(
    val is24Hour: Boolean = true,
    val roundingSeconds: Int = 15,
    val darkMode: Boolean = true,
    val categories: List<Category> = emptyList()
)

data class NoteLine(
    val title: String,
    val text: String,
    val timestamp: Long,
    val categoryName: String? = null,
    val categoryColor: Long? = null
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey val timestamp: Long,
    val title: String,
    val text: String,
    val categoryName: String?,
    val categoryColor: Long?
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

@Database(entities = [NoteEntity::class], version = 3)
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
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
