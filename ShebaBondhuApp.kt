package com.sheba.bondhu

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

private enum class Page { DIRECTORY, MY_LISTINGS, ADD_PROVIDER, PROFILE, ADMIN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShebaBondhuApp() {
    val repo = remember { FirebaseRepository() }
    var signedIn by remember { mutableStateOf(repo.isSignedIn()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var isAdmin by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(Page.DIRECTORY) }
    var providers by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var mine by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var pending by remember { mutableStateOf<List<Provider>>(emptyList()) }
    var editProvider by remember { mutableStateOf<Provider?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    DisposableEffect(signedIn) {
        if (!signedIn) {
            providers = emptyList()
            mine = emptyList()
            pending = emptyList()
            onDispose { }
        } else {
            val a = repo.listenApproved({ providers = it }, { error = it })
            val b = repo.listenMyProviders({ mine = it }, { error = it })
            val c = repo.listenPending({ pending = it }, { /* non-admin rules may deny */ })
            onDispose { a.remove(); b.remove(); c.remove() }
        }
    }

    LaunchedEffect(signedIn) {
        isAdmin = if (signedIn) runCatching { repo.getIsAdmin() }.getOrDefault(false) else false
    }

    if (!signedIn) {
        AuthScreen(
            email = email,
            onEmail = { email = it },
            password = password,
            onPassword = { password = it },
            error = error,
            onLogin = {
                scope.launch {
                    runCatching { repo.login(email, password) }
                        .onSuccess { signedIn = true; error = "" }
                        .onFailure { error = it.message ?: "লগইন ব্যর্থ হয়েছে" }
                }
            },
            onRegister = {
                scope.launch {
                    runCatching { repo.register(email, password) }
                        .onSuccess { signedIn = true; error = "অ্যাকাউন্ট তৈরি হয়েছে" }
                        .onFailure { error = it.message ?: "নিবন্ধন ব্যর্থ হয়েছে" }
                }
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("সেবা বন্ধু") },
                actions = {
                    TextButton(onClick = {
                        repo.signOut()
                        signedIn = false
                    }) { Text("লগআউট") }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = page == Page.DIRECTORY,
                    onClick = { page = Page.DIRECTORY },
                    icon = { Text("⌕") },
                    label = { Text("সেবা খুঁজুন") }
                )
                NavigationBarItem(
                    selected = page == Page.MY_LISTINGS,
                    onClick = { page = Page.MY_LISTINGS },
                    icon = { Text("▤") },
                    label = { Text("আমার সেবা") }
                )
                NavigationBarItem(
                    selected = page == Page.ADD_PROVIDER,
                    onClick = { editProvider = null; page = Page.ADD_PROVIDER },
                    icon = { Text("+") },
                    label = { Text("যোগ করুন") }
                )
                NavigationBarItem(
                    selected = page == Page.PROFILE,
                    onClick = { page = Page.PROFILE },
                    icon = { Text("●") },
                    label = { Text("প্রোফাইল") }
                )
                if (isAdmin) NavigationBarItem(
                    selected = page == Page.ADMIN,
                    onClick = { page = Page.ADMIN },
                    icon = { Text("✓") },
                    label = { Text("অ্যাডমিন") }
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (error.isNotBlank()) {
                Text(error, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
            }
            when (page) {
                Page.DIRECTORY -> DirectoryScreen(providers) { provider ->
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(provider.phone)}"))
                    context.startActivity(intent)
                }
                Page.MY_LISTINGS -> MyListingsScreen(
                    items = mine,
                    onEdit = { editProvider = it; page = Page.ADD_PROVIDER },
                    onDelete = { p ->
                        scope.launch {
                            runCatching { repo.deleteMyProvider(p) }
                                .onFailure { error = it.message ?: "মুছতে ব্যর্থ" }
                        }
                    }
                )
                Page.ADD_PROVIDER -> ProviderFormScreen(
                    initial = editProvider,
                    onUpload = { uri -> repo.uploadPhoto(uri) },
                    onSave = { p ->
                        scope.launch {
                            runCatching { repo.saveProvider(p, editProvider?.id) }
                                .onSuccess {
                                    error = "তথ্য জমা হয়েছে; অনুমোদনের পর ডিরেক্টরিতে দেখা যাবে।"
                                    editProvider = null
                                    page = Page.MY_LISTINGS
                                }
                                .onFailure { error = it.message ?: "তথ্য সংরক্ষণ ব্যর্থ" }
                        }
                    }
                )
                Page.PROFILE -> ProfileScreen(
                    email = repo.currentEmail(),
                    onSave = { name, phone, address ->
                        scope.launch {
                            runCatching { repo.saveProfile(name, phone, address) }
                                .onSuccess { error = "প্রোফাইল সংরক্ষণ হয়েছে" }
                                .onFailure { error = it.message ?: "প্রোফাইল সংরক্ষণ ব্যর্থ" }
                        }
                    }
                )
                Page.ADMIN -> AdminScreen(
                    items = pending,
                    onApprove = { p ->
                        scope.launch {
                            runCatching { repo.approveProvider(p.id) }
                                .onSuccess { error = "সেবাদাতা অনুমোদিত হয়েছে" }
                                .onFailure { error = it.message ?: "অনুমোদন ব্যর্থ" }
                        }
                    },
                    onReject = { p ->
                        scope.launch {
                            runCatching { repo.rejectProvider(p.id) }
                                .onSuccess { error = "সেবাদাতা বাতিল করা হয়েছে" }
                                .onFailure { error = it.message ?: "বাতিল করা যায়নি" }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun AuthScreen(
    email: String, onEmail: (String) -> Unit,
    password: String, onPassword: (String) -> Unit,
    error: String, onLogin: () -> Unit, onRegister: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("সেবা বন্ধু", style = MaterialTheme.typography.headlineLarge)
        Text("আপনার এলাকার সেবা খুঁজুন", modifier = Modifier.padding(bottom = 24.dp))
        OutlinedTextField(email, onEmail, label = { Text("ইমেইল") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(password, onPassword, label = { Text("পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)") }, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(), singleLine = true)
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(16.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) { Text("লগইন") }
        OutlinedButton(onClick = onRegister, modifier = Modifier.fillMaxWidth()) { Text("নতুন অ্যাকাউন্ট") }
    }
}

@Composable
private fun DirectoryScreen(items: List<Provider>, onCall: (Provider) -> Unit) {
    var search by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        OutlinedTextField(
            value = search, onValueChange = { search = it },
            label = { Text("সেবা, নাম বা ঠিকানা খুঁজুন") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        val filtered = items.filter {
            listOf(it.name, it.category, it.address).any { field ->
                field.contains(search, ignoreCase = true)
            }
        }
        if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("কোনো অনুমোদিত সেবাদাতা পাওয়া যায়নি।")
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(filtered, key = { it.id }) { provider ->
                    ProviderCard(provider, onCall)
                }
            }
        }
    }
}

@Composable
private fun ProviderCard(provider: Provider, onCall: (Provider) -> Unit) {
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (provider.photoUrl.isNotBlank()) {
                AsyncImage(
                    model = provider.photoUrl,
                    contentDescription = "সেবাদাতার ছবি",
                    modifier = Modifier.fillMaxWidth().height(170.dp)
                )
            }
            Text(provider.name, style = MaterialTheme.typography.titleLarge)
            Text("সেবা: ${provider.category}")
            Text("ফোন: ${provider.phone}")
            Text("ঠিকানা: ${provider.address}")
            if (provider.description.isNotBlank()) Text(provider.description)
            Button(onClick = { onCall(provider) }, modifier = Modifier.fillMaxWidth()) {
                Text("ফোন করুন")
            }
        }
    }
}

@Composable
private fun MyListingsScreen(
    items: List<Provider>, onEdit: (Provider) -> Unit, onDelete: (Provider) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("আপনি এখনো কোনো সেবাদাতা তথ্য যোগ করেননি।")
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { p ->
                ElevatedCard {
                    Column(Modifier.padding(12.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("অবস্থা: ${if (p.status == "approved") "অনুমোদিত" else "অনুমোদনের অপেক্ষায়"}")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEdit(p) }) { Text("সম্পাদনা") }
                            OutlinedButton(onClick = { onDelete(p) }) { Text("মুছুন") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderFormScreen(
    initial: Provider?, onUpload: suspend (Uri) -> String, onSave: (Provider) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember(initial?.id) { mutableStateOf(initial?.category.orEmpty()) }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phone.orEmpty()) }
    var address by remember(initial?.id) { mutableStateOf(initial?.address.orEmpty()) }
    var photoUrl by remember(initial?.id) { mutableStateOf(initial?.photoUrl.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    var message by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                busy = true
                runCatching { onUpload(uri) }
                    .onSuccess { photoUrl = it; message = "ছবি আপলোড হয়েছে" }
                    .onFailure { message = it.message ?: "ছবি আপলোড ব্যর্থ" }
                busy = false
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item { Text(if (initial == null) "নতুন সেবাদাতা যোগ করুন" else "সেবাদাতার তথ্য সম্পাদনা", style = MaterialTheme.typography.titleLarge) }
        item { OutlinedTextField(name, { name = it }, label = { Text("নাম") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(category, { category = it }, label = { Text("সেবার ধরন") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(phone, { phone = it }, label = { Text("ফোন নম্বর") }, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone)) }
        item { OutlinedTextField(address, { address = it }, label = { Text("ঠিকানা") }, modifier = Modifier.fillMaxWidth()) }
        item { OutlinedTextField(description, { description = it }, label = { Text("সেবার বিবরণ") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
        item {
            OutlinedButton(onClick = { picker.launch("image/*") }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "অপেক্ষা করুন..." else "গ্যালারি থেকে ছবি বাছাই করুন")
            }
            if (photoUrl.isNotBlank()) AsyncImage(model = photoUrl, contentDescription = "নির্বাচিত ছবি", modifier = Modifier.fillMaxWidth().height(160.dp))
            if (message.isNotBlank()) Text(message)
        }
        item {
            Button(
                enabled = !busy && name.isNotBlank() && category.isNotBlank() && phone.isNotBlank() && address.isNotBlank(),
                onClick = {
                    onSave(
                        Provider(
                            id = initial?.id.orEmpty(),
                            ownerUid = initial?.ownerUid.orEmpty(),
                            name = name, category = category, phone = phone,
                            address = address, photoUrl = photoUrl, description = description,
                            status = initial?.status ?: "pending"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("তথ্য জমা দিন") }
        }
    }
}

@Composable
private fun ProfileScreen(email: String, onSave: (String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("আমার প্রোফাইল", style = MaterialTheme.typography.titleLarge)
        Text("ইমেইল: $email")
        OutlinedTextField(name, { name = it }, label = { Text("আপনার নাম") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("ফোন নম্বর") }, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(address, { address = it }, label = { Text("ঠিকানা") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { onSave(name, phone, address) }, modifier = Modifier.fillMaxWidth()) { Text("প্রোফাইল সংরক্ষণ") }
    }
}

@Composable
private fun AdminScreen(
    items: List<Provider>, onApprove: (Provider) -> Unit, onReject: (Provider) -> Unit
) {
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("অনুমোদনের অপেক্ষায় কোনো তথ্য নেই।")
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(items, key = { it.id }) { p ->
                ElevatedCard {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.name, style = MaterialTheme.typography.titleMedium)
                        Text("${p.category} • ${p.phone}")
                        Text(p.address)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onApprove(p) }) { Text("অনুমোদন") }
                            OutlinedButton(onClick = { onReject(p) }) { Text("বাতিল") }
                        }
                    }
                }
            }
        }
    }
}
