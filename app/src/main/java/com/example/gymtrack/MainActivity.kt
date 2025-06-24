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
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
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
            MaterialTheme(colorScheme = darkColorScheme()) {
                val navController = rememberNavController()
                NavigationHost(navController)
            }
        }
    }
}

@Composable
fun NavigationHost(navController: NavHostController) {
    val context = LocalContext.current
    val db = remember { NoteDatabase.getDatabase(context) }
    val dao = db.noteDao()
    var notes by remember { mutableStateOf(listOf<NoteLine>()) }
    var selectedNotes by remember { mutableStateOf(setOf<NoteLine>()) }
    var currentNote by remember { mutableStateOf<NoteLine?>(null) }

    LaunchedEffect(Unit) {
        notes = dao.getAll().map { NoteLine(it.text, it.timestamp) }
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
                }
            )
        }
        composable("edit") {
            NoteEditor(
                note = currentNote,
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
    onCreate: () -> Unit
) {
    Scaffold(
        floatingActionButton = {
            if (selectedNotes.isEmpty()) {
                FloatingActionButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
                }
            }
        },
        topBar = {
            TopAppBar(
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
                    colors = CardDefaults.cardColors(containerColor = if (isSelected) Color.DarkGray else Color.Black)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(note.text.lines().firstOrNull() ?: "No text", fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatRelativeTime(note.timestamp),
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
fun NoteEditor(note: NoteLine?, onSave: (String) -> Unit, onCancel: () -> Unit) {
    var text by remember { mutableStateOf(note?.text ?: "") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null) }
            IconButton(onClick = { onSave(text) }) { Icon(Icons.Default.Check, null) }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.fillMaxSize(),
            placeholder = { Text("Start typing") },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSave(text) })
        )
    }
}

fun formatRelativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val date = Date(timestamp)
    val format = SimpleDateFormat("MMM dd HH:mm", Locale.getDefault())
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "Just now"
        diff < 86_400_000 -> timeFormat.format(date)
        diff < 172_800_000 -> "Yesterday " + timeFormat.format(date)
        else -> format.format(date)
    }
}

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
