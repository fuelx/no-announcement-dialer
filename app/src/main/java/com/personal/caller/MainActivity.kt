package com.personal.caller

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.personal.caller.data.Contact
import com.personal.caller.data.ContactRepository
import com.personal.caller.ui.theme.PersonalCallerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private val roleRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Default Dialer Set", Toast.LENGTH_SHORT).show()
        }
    }

    private var hasContactPermissionState = mutableStateOf(false)
    private var currentScreenState = mutableStateOf("dialpad")
    private var dialIntentNumberState = mutableStateOf("")
    private var pendingCallNumber: String? = null

    private val contactPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasContactPermissionState.value = isGranted
        if (isGranted) {
            currentScreenState.value = "contacts"
        }
    }

    private val callPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        val number = pendingCallNumber
        pendingCallNumber = null
        if (isGranted && number != null) {
            placeCall(number)
        } else if (!isGranted) {
            Toast.makeText(this, "Phone permission is required to place calls", Toast.LENGTH_LONG).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ -> /* The incoming-call notification is retried when the permission is later granted. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        hasContactPermissionState.value = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
        dialIntentNumberState.value = numberFromDialIntent(intent).orEmpty()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val contactRepository = ContactRepository(this)
        
        setContent {
            PersonalCallerTheme {
                var currentScreen by currentScreenState
                val hasContactPermission by hasContactPermissionState

                Scaffold(
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            val items = listOf(
                                NavigationItem("Dialpad", Icons.Default.Dialpad, "dialpad"),
                                NavigationItem("Contacts", Icons.Default.Person, "contacts"),
                                NavigationItem("Settings", Icons.Default.Settings, "main")
                            )
                            items.forEach { item ->
                                NavigationBarItem(
                                    icon = { Icon(item.icon, contentDescription = item.label) },
                                    label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                                    selected = currentScreen == item.screen,
                                    onClick = {
                                        if (item.screen == "contacts" && !hasContactPermission) {
                                            contactPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                                        } else {
                                            currentScreen = item.screen
                                        }
                                    }
                                )
                            }
                        }
                    }
                ) { paddingValues ->
                    Surface(
                        modifier = Modifier.fillMaxSize().padding(paddingValues),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        when (currentScreen) {
                            "dialpad" -> DialpadScreen(
                                contactRepository = contactRepository,
                                initialNumber = dialIntentNumberState.value,
                                hasContactPermission = hasContactPermission,
                                onDial = { number -> placeCall(number) }
                            )
                            "contacts" -> ContactsScreen(
                                repository = contactRepository,
                                hasContactPermission = hasContactPermission,
                                onDial = { number -> placeCall(number) }
                            )
                            "main" -> MainScreen(
                                onSetDefaultClick = { requestDefaultDialer() }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun placeCall(phoneNumber: String) {
        val normalizedNumber = phoneNumber.trim()
        if (normalizedNumber.isEmpty()) return
        val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        val uri = Uri.fromParts("tel", normalizedNumber, null)
        val extras = Bundle().apply {
            putBoolean(TelecomManager.EXTRA_START_CALL_WITH_SPEAKERPHONE, false)
        }
        try {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                telecomManager.placeCall(uri, extras)
            } else {
                pendingCallNumber = normalizedNumber
                callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        numberFromDialIntent(intent)?.let {
            dialIntentNumberState.value = it
            currentScreenState.value = "dialpad"
        }
    }

    private fun numberFromDialIntent(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_DIAL && intent?.action != Intent.ACTION_VIEW) return null
        return intent.data?.schemeSpecificPart?.takeIf { it.isNotBlank() }
    }

    private fun requestDefaultDialer() {
        val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_DIALER)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
            roleRequestLauncher.launch(intent)
        } else {
            Toast.makeText(this, "Already Default Dialer", Toast.LENGTH_SHORT).show()
        }
    }

}

data class NavigationItem(val label: String, val icon: ImageVector, val screen: String)

@Composable
fun MainScreen(onSetDefaultClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Minimal Settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(48.dp))
        
        OutlinedButton(onClick = onSetDefaultClick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text("Set as Default Dialer")
        }
    }
}

@Composable
fun DialpadScreen(
    contactRepository: ContactRepository,
    initialNumber: String,
    hasContactPermission: Boolean,
    onDial: (String) -> Unit
) {
    var dialedNumber by rememberSaveable(initialNumber) { mutableStateOf(initialNumber) }
    val allContacts = rememberContacts(contactRepository, hasContactPermission)
    
    val matchedContacts = remember(dialedNumber, allContacts) {
        if (dialedNumber.isEmpty()) emptyList()
        else {
            allContacts.filter { contact ->
                contact.phoneNumber?.replace(" ", "")?.contains(dialedNumber) == true ||
                matchT9(contact.displayName, dialedNumber)
            }.take(3)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Suggestions Area
        Box(modifier = Modifier.height(100.dp).fillMaxWidth().padding(horizontal = 16.dp)) {
            if (matchedContacts.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    matchedContacts.forEach { contact ->
                        Column(
                            modifier = Modifier.weight(1f).clickable {
                                contact.phoneNumber?.takeIf { it.isNotBlank() }?.let(onDial)
                            },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(modifier = Modifier.size(44.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(contact.displayName.firstOrNull()?.toString() ?: "?", style = MaterialTheme.typography.titleMedium)
                                }
                            }
                            Text(contact.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 1, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }

        // Dialed Number
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = dialedNumber,
                style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 2.sp),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            if (dialedNumber.isNotEmpty()) {
                IconButton(onClick = { dialedNumber = dialedNumber.dropLast(1) }) {
                    Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "backspace")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Grid
        val keys = listOf(
            listOf("1" to "", "2" to "ABC", "3" to "DEF"),
            listOf("4" to "GHI", "5" to "JKL", "6" to "MNO"),
            listOf("7" to "PQRS", "8" to "TUV", "9" to "WXYZ"),
            listOf("*" to "", "0" to "+", "#" to "")
        )

        Column(modifier = Modifier.padding(horizontal = 40.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            keys.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { (digit, letters) ->
                        DialKey(digit, letters, Modifier.weight(1f)) { dialedNumber += digit }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        IconButton(
            onClick = { if (dialedNumber.isNotEmpty()) onDial(dialedNumber) },
            modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 24.dp).size(72.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(Icons.Default.Call, contentDescription = "Call", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun DialKey(digit: String, letters: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.aspectRatio(1f).clickable(onClick = onClick),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(digit, style = MaterialTheme.typography.headlineMedium)
            if (letters.isNotEmpty()) {
                Text(letters, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun ContactsScreen(repository: ContactRepository, hasContactPermission: Boolean, onDial: (String) -> Unit) {
    var searchQuery by remember { mutableStateOf("") }
    val allContacts = rememberContacts(repository, hasContactPermission)
    val filteredContacts = remember(searchQuery, allContacts) {
        if (searchQuery.isBlank()) allContacts
        else allContacts.filter { it.displayName.contains(searchQuery, ignoreCase = true) || it.phoneNumber?.contains(searchQuery) == true }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            placeholder = { Text("Search...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            colors = TextFieldDefaults.colors(focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent, unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent),
            shape = CircleShape
        )

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
            items(filteredContacts) { contact ->
                ContactItem(contact, onDial)
                Divider(modifier = Modifier.padding(vertical = 4.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
fun ContactItem(contact: Contact, onDial: (String) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable {
            contact.phoneNumber?.takeIf { it.isNotBlank() }?.let(onDial)
        }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
            if (contact.photoUri != null) {
                AsyncImage(model = contact.photoUri, contentDescription = null, contentScale = ContentScale.Crop)
            } else {
                Box(contentAlignment = Alignment.Center) {
                    Text(contact.displayName.firstOrNull()?.toString() ?: "?", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(contact.displayName, style = MaterialTheme.typography.bodyLarge)
            contact.phoneNumber?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
}

@Composable
private fun rememberContacts(repository: ContactRepository, hasContactPermission: Boolean): List<Contact> {
    val contacts by produceState(emptyList<Contact>(), repository, hasContactPermission) {
        value = if (hasContactPermission) {
            withContext(Dispatchers.IO) { repository.getContacts() }
        } else {
            emptyList()
        }
    }
    return contacts
}

fun matchT9(name: String, digits: String): Boolean {
    val t9Map = mapOf('2' to "abc", '3' to "def", '4' to "ghi", '5' to "jkl", '6' to "mno", '7' to "pqrs", '8' to "tuv", '9' to "wxyz")
    val lowerName = name.lowercase().split(" ")
    return digits.isNotEmpty() && lowerName.any { word ->
        word.length >= digits.length && digits.indices.all { i -> t9Map[digits[i]]?.contains(word[i]) == true }
    }
}
