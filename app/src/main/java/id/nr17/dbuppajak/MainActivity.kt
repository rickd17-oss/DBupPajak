package id.nr17.dbuppajak

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

private val Navy = Color(0xFF091725)
private val Gold = Color(0xFFD6B36A)
private val Cream = Color(0xFFF7F4ED)

data class AppConfig(val apiUrl: String, val folderId: String, val accessCode: String)
data class SplitOwner(val name: String = "", val hasBuilding: Boolean = false)
data class ScanItem(val uri: Uri, val type: String, val fileName: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DBupTheme { AppRoot() } }
    }
}

@Composable
fun DBupTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Navy, secondary = Gold, background = Cream, surface = Color.White),
        typography = Typography(), content = content
    )
}

@Composable
fun AppRoot() {
    val context = LocalContext.current
    var config by remember { mutableStateOf(loadConfig(context)) }
    var showSettings by remember { mutableStateOf(config == null) }
    if (showSettings) {
        ConfigScreen(config, onSaved = { saveConfig(context, it); config = it; showSettings = false }, onBack = if (config != null) {{ showSettings = false }} else null)
    } else {
        MainForm(config!!, onSettings = { showSettings = true })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(current: AppConfig?, onSaved: (AppConfig) -> Unit, onBack: (() -> Unit)?) {
    var url by remember { mutableStateOf(current?.apiUrl.orEmpty()) }
    var folder by remember { mutableStateOf(current?.folderId.orEmpty()) }
    var code by remember { mutableStateOf(current?.accessCode.orEmpty()) }
    var status by remember { mutableStateOf("") }
    var testing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Scaffold(topBar = { TopAppBar(title = { Text("Konfigurasi Drive", fontWeight = FontWeight.Bold) }, navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, titleContentColor = Color.White, navigationIconContentColor = Color.White)) }) { pad ->
        Column(Modifier.padding(pad).padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            BrandHeader("Hubungkan penyimpanan", "Masukkan API Drive milik Anda. Pengaturan ini hanya tersimpan di perangkat.")
            OutlinedTextField(url, { url = it.trim() }, label = { Text("URL Web App Apps Script") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(folder, { folder = it.trim() }, label = { Text("ID folder utama Google Drive") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(code, { code = it }, label = { Text("Kode akses API") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            if (status.isNotBlank()) Text(status, color = if (status.startsWith("Berhasil")) Color(0xFF087F23) else Color(0xFFB3261E))
            Button(onClick = { scope.launch { testing = true; status = testConnection(AppConfig(url, folder, code)); testing = false } }, enabled = !testing && url.isNotBlank() && folder.isNotBlank() && code.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CloudSync, null); Spacer(Modifier.width(8.dp)); Text(if (testing) "Menguji..." else "Tes koneksi")
            }
            Button(onClick = { onSaved(AppConfig(url, folder, code)) }, enabled = status.startsWith("Berhasil"), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy), modifier = Modifier.fillMaxWidth()) { Text("Simpan & Mulai", fontWeight = FontWeight.Bold) }
            Text("DBupPajak • Dibuat oleh NR17", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainForm(config: AppConfig, onSettings: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var block by remember { mutableStateOf("") }; var nop by remember { mutableStateOf("") }
    var need by remember { mutableStateOf("Mutasi Penuh") }; var hasBuilding by remember { mutableStateOf(false) }
    var splitCount by remember { mutableIntStateOf(2) }; var owners by remember { mutableStateOf(List(2) { SplitOwner() }) }
    var note by remember { mutableStateOf("") }; var scans by remember { mutableStateOf(emptyList<ScanItem>()) }
    var busy by remember { mutableStateOf(false) }; var progress by remember { mutableStateOf("") }; var menu by remember { mutableStateOf(false) }
    Scaffold(topBar = { TopAppBar(title = { Column { Text("DBupPajak", fontWeight = FontWeight.Bold); Text("Pengarsipan pajak digital", fontSize = 11.sp) } }, actions = { IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, null) }; DropdownMenu(menu, { menu = false }) { DropdownMenuItem({ Text("Pengaturan Drive") }, onClick = { menu = false; onSettings() }, leadingIcon = { Icon(Icons.Default.Settings, null) }) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, titleContentColor = Color.White, actionIconContentColor = Color.White)) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().background(Cream), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { BrandHeader("Berkas baru", "Folder Drive dibuat otomatis dari Blok dan NOP.") }
            item { SectionCard("Lokasi objek pajak") { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedTextField(block, { block = it.filter(Char::isDigit) }, label = { Text("Blok") }, modifier = Modifier.weight(1f)); OutlinedTextField(nop, { nop = it.filter(Char::isDigit) }, label = { Text("NOP") }, modifier = Modifier.weight(2f)) }; if (block.isNotBlank() && nop.isNotBlank()) Text("Folder: ${block.padStart(2,'0')}-${nop.padStart(4,'0')}", color = Gold, fontWeight = FontWeight.Bold) } }
            item { SectionCard("Kebutuhan") { ChoiceChips(listOf("Mutasi Penuh", "Mutasi Pecah", "Perbaikan"), need) { need = it }; if (need != "Mutasi Pecah") SwitchLine("Ada bangunan", hasBuilding) { hasBuilding = it } } }
            if (need == "Mutasi Pecah") item { SectionCard("Rincian pecahan") { Row(verticalAlignment = Alignment.CenterVertically) { Text("Dipecah menjadi", Modifier.weight(1f)); IconButton({ if (splitCount > 2) { splitCount--; owners = owners.take(splitCount) } }) { Icon(Icons.Default.RemoveCircle, null) }; Text("$splitCount", fontWeight = FontWeight.Bold); IconButton({ if (splitCount < 20) { splitCount++; owners = owners + SplitOwner() } }) { Icon(Icons.Default.AddCircle, null) } }; owners.forEachIndexed { i, owner -> OutlinedTextField(owner.name, { v -> owners = owners.toMutableList().also { it[i] = owner.copy(name=v) } }, label = { Text("Atas nama pecahan ${i+1}") }, modifier = Modifier.fillMaxWidth()); SwitchLine("Pecahan ${i+1} ada bangunan", owner.hasBuilding) { v -> owners = owners.toMutableList().also { it[i] = owner.copy(hasBuilding=v) } } } } }
            item { SectionCard("Dokumen scan") { DocumentPicker(scans) { scans = scans + it }; scans.forEachIndexed { i, scan -> ScanRow(scan, onUpdate = { updated -> scans = scans.toMutableList().also { it[i]=updated } }, onDelete = { scans = scans.toMutableList().also { it.removeAt(i) } }) } } }
            item { SectionCard("Catatan") { OutlinedTextField(note, { note = it }, label = { Text("Catatan tambahan") }, minLines = 4, modifier = Modifier.fillMaxWidth()) } }
            item { if (progress.isNotBlank()) Text(progress, color = Navy, fontWeight = FontWeight.Medium); Button(onClick = { scope.launch { busy=true; progress="Menyiapkan berkas..."; val result = uploadSubmission(context, config, block, nop, need, hasBuilding, owners, note, scans) { progress=it }; progress=result; busy=false } }, enabled = !busy && block.isNotBlank() && nop.isNotBlank() && scans.isNotEmpty() && (need != "Mutasi Pecah" || owners.all { it.name.isNotBlank() }), modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy)) { Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text(if (busy) "Mengunggah..." else "Simpan ke Google Drive", fontWeight = FontWeight.Bold) } }
            item { Text("DBupPajak v1.0 • NR17", Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = Color.Gray, fontSize = 12.sp) }
        }
    }
}

@Composable fun BrandHeader(title: String, subtitle: String) { Column { Text(title, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Navy); Text(subtitle, color = Color.Gray) } }
@Composable fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) { Card(shape=RoundedCornerShape(18.dp), colors=CardDefaults.cardColors(containerColor=Color.White), elevation=CardDefaults.cardElevation(2.dp)) { Column(Modifier.padding(16.dp), verticalArrangement=Arrangement.spacedBy(10.dp)) { Text(title, fontWeight=FontWeight.Bold, color=Navy, fontSize=17.sp); content() } } }
@Composable fun ChoiceChips(values: List<String>, selected: String, onSelect:(String)->Unit) { Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { values.forEach { FilterChip(selected==it, {onSelect(it)}, {Text(it, fontSize=11.sp)}) } } }
@Composable fun SwitchLine(label:String, checked:Boolean, onChange:(Boolean)->Unit) { Row(verticalAlignment=Alignment.CenterVertically) { Text(label, Modifier.weight(1f)); Switch(checked,onChange) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentPicker(scans: List<ScanItem>, onAdded:(ScanItem)->Unit) {
    val context=LocalContext.current; var pendingType by remember { mutableStateOf("KTP") }; var expanded by remember { mutableStateOf(false) }; var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let { context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION); onAdded(ScanItem(it,pendingType,"${pendingType}_${scans.size+1}")) } }
    val camera=rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if(ok) cameraUri?.let { onAdded(ScanItem(it,pendingType,"${pendingType}_${scans.size+1}")) } }
    ExposedDropdownMenuBox(expanded, {expanded=!expanded}) { OutlinedTextField(pendingType, {}, readOnly=true, label={Text("Jenis dokumen")}, trailingIcon={ExposedDropdownMenuDefaults.TrailingIcon(expanded)}, modifier=Modifier.menuAnchor().fillMaxWidth()); ExposedDropdownMenu(expanded,{expanded=false}) { listOf("KTP","KK","Sertifikat","SPPT","Lainnya").forEach { DropdownMenuItem({Text(it)}, {pendingType=it;expanded=false}) } } }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { OutlinedButton({ gallery.launch(arrayOf("image/*","application/pdf")) }, Modifier.weight(1f)) { Icon(Icons.Default.FolderOpen,null); Text(" Pilih file") }; OutlinedButton({ val dir=File(context.cacheDir,"scans").apply{mkdirs()}; val f=File(dir,"scan_${System.currentTimeMillis()}.jpg"); cameraUri=FileProvider.getUriForFile(context,"${context.packageName}.fileprovider",f); camera.launch(cameraUri) }, Modifier.weight(1f)) { Icon(Icons.Default.PhotoCamera,null); Text(" Kamera") } }
}

@Composable fun ScanRow(item:ScanItem,onUpdate:(ScanItem)->Unit,onDelete:()->Unit) { Row(verticalAlignment=Alignment.CenterVertically) { Icon(Icons.Default.Description,null,tint=Gold); Spacer(Modifier.width(8.dp)); OutlinedTextField(item.fileName,{onUpdate(item.copy(fileName=it))},label={Text(item.type)},modifier=Modifier.weight(1f),singleLine=true); IconButton(onDelete){Icon(Icons.Default.Delete,null,tint=Color(0xFFB3261E))} } }

private fun loadConfig(c:Context):AppConfig? { val p=c.getSharedPreferences("dbup_config",Context.MODE_PRIVATE); val u=p.getString("url",null)?:return null; return AppConfig(u,p.getString("folder","")!!,p.getString("code","")!!) }
private fun saveConfig(c:Context,x:AppConfig){c.getSharedPreferences("dbup_config",Context.MODE_PRIVATE).edit().putString("url",x.apiUrl).putString("folder",x.folderId).putString("code",x.accessCode).apply()}

private suspend fun testConnection(c:AppConfig)=withContext(Dispatchers.IO){ try { val r=postJson(c.apiUrl,JSONObject().put("action","ping").put("folderId",c.folderId).put("accessCode",c.accessCode)); if(r.optBoolean("ok")) "Berhasil terhubung ke Google Drive" else "Gagal: ${r.optString("message","Konfigurasi tidak valid")}" }catch(e:Exception){"Gagal: ${e.message}"} }

private suspend fun uploadSubmission(context:Context,c:AppConfig,block:String,nop:String,need:String,building:Boolean,owners:List<SplitOwner>,note:String,scans:List<ScanItem>,progress:(String)->Unit)=withContext(Dispatchers.IO){
    try { val folder="${block.padStart(2,'0')}-${nop.padStart(4,'0')}"; val meta=JSONObject().put("action","createSubmission").put("accessCode",c.accessCode).put("folderId",c.folderId).put("folderName",folder).put("need",need).put("hasBuilding",building).put("note",note).put("owners",JSONArray().apply{if(need=="Mutasi Pecah") owners.forEach{put(JSONObject().put("name",it.name).put("hasBuilding",it.hasBuilding))}}); val created=postJson(c.apiUrl,meta); if(!created.optBoolean("ok")) throw Exception(created.optString("message")); val submissionId=created.getString("submissionId"); scans.forEachIndexed{i,s-> progress("Mengunggah ${i+1}/${scans.size}: ${s.fileName}"); val bytes=context.contentResolver.openInputStream(s.uri)?.use{it.readBytes()}?:throw Exception("File tidak dapat dibaca"); val mime=context.contentResolver.getType(s.uri)?:"image/jpeg"; val ext=when(mime){"application/pdf"->"pdf";"image/png"->"png";else->"jpg"}; val req=JSONObject().put("action","uploadFile").put("accessCode",c.accessCode).put("submissionId",submissionId).put("fileName","${s.fileName}.$ext").put("mimeType",mime).put("data",android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)); val out=postJson(c.apiUrl,req); if(!out.optBoolean("ok"))throw Exception(out.optString("message")) }; "Berhasil. Folder $folder telah tersimpan di Drive." } catch(e:Exception){"Gagal mengunggah: ${e.message}"}
}

private fun postJson(endpoint:String,body:JSONObject):JSONObject { val conn=(URL(endpoint).openConnection() as HttpURLConnection).apply{requestMethod="POST";doOutput=true;connectTimeout=30000;readTimeout=60000;setRequestProperty("Content-Type","application/json; charset=utf-8")}; conn.outputStream.use{it.write(body.toString().toByteArray())}; val text=(if(conn.responseCode in 200..299)conn.inputStream else conn.errorStream).bufferedReader().use{it.readText()}; return JSONObject(text) }
