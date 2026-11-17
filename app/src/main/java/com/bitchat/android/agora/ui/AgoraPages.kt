package com.bitchat.android.agora.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.bitchat.android.agora.AgoraHub
import com.bitchat.android.agora.AgoraNames
import com.bitchat.android.agora.AgoraNotifier
import com.bitchat.android.agora.AgoraProgress
import com.bitchat.android.agora.AgoraRules
import com.bitchat.android.agora.AgoraSnapshot
import com.bitchat.android.agora.IdeaKind
import com.bitchat.android.agora.MemoryStore
import com.bitchat.android.service.MeshForegroundService
import com.bitchat.android.service.MeshServicePreferences
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

// ------------------------------------------------------------------ proposer

@Composable
fun ProposerPage(toast: (String?) -> Unit) {
    var kind by remember { mutableStateOf(IdeaKind.ACTION) }
    var text by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Proposer une idée")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Gap12) {
            Title("1. C'est quoi, ton idée ?", size = 15)
            Row(horizontalArrangement = Gap8) {
                listOf(IdeaKind.SLOGAN to "à crier", IdeaKind.DEMAND to "ce qu'on veut", IdeaKind.ACTION to "parcours, pause…").forEach { (k, hint) ->
                    val on = kind == k
                    Column(
                        Modifier.weight(1f).heightIn(min = 92.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (on) AG.Ink else AG.Card)
                            .border(2.dp, if (on) AG.Ink else AG.Line, RoundedCornerShape(14.dp))
                            .clickable { kind = k }.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(kindIcon(k), contentDescription = null, tint = if (on) AG.Yellow else AG.Ink)
                        Text(k.label, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (on) AG.Yellow else AG.Ink)
                        Text(hint, fontSize = 11.sp, color = if (on) AG.Light else AG.Muted, textAlign = TextAlign.Center)
                    }
                }
            }
            Title("2. Écris-la en une phrase", size = 15)
            AgoraField(text, { text = it.take(AgoraRules.MAX_TEXT) }, "Ex : Pause eau et ombre à la prochaine place", minLines = 3)
            Text("${text.length} / ${AgoraRules.MAX_TEXT}", fontSize = 12.sp, color = AG.Muted, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            Section {
                Text("Comment elle apparaîtra", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AG.Muted)
                Text(
                    if (text.isBlank()) "Ton idée s'affichera ici" else if (kind == IdeaKind.SLOGAN) "« ${text.trim()} »" else text.trim(),
                    fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AG.Ink
                )
                Text("par ${AgoraNames.pseudo(AgoraHub.state.value.myId)} (ton pseudo) · s'efface après 30 min sans vote", fontSize = 12.sp, color = AG.Muted)
            }
            Text(
                "• 3 idées actives au maximum, 1 nouvelle par minute.\n" +
                    "• Ton idée est signée par ton téléphone : personne ne peut parler à ta place.\n" +
                    "• Pas de nom, pas de position GPS : seulement ton pseudo.",
                fontSize = 13.sp, color = AG.Body, lineHeight = 19.sp
            )
            BigButton("Envoyer au cortège", enabled = text.trim().length >= 3) {
                val refused = AgoraHub.propose(kind, text)
                if (refused == null) {
                    toast("Ton idée part de téléphone en téléphone !")
                    AgoraNav.page.value = null
                    AgoraNav.cardsMode.value = false
                } else toast(refused)
            }
        }
    }
}

// ------------------------------------------------------------------- duel

@Composable
fun AmeliorerPage(snap: AgoraSnapshot, parentKey: String, toast: (String?) -> Unit) {
    val parent = snap.wall.firstOrNull { it.key == parentKey }
    var text by remember(parentKey) { mutableStateOf(parent?.text ?: "") }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Améliorer une idée")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Gap12) {
            if (parent == null) {
                Text("Cette idée n'est plus sur le mur.", fontSize = 15.sp, color = AG.Body)
                return@Column
            }
            Section {
                Text("VERSION 1 · par ${AgoraNames.pseudos(parent.authorIds)}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AG.Muted)
                Text(parent.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AG.Ink)
                Text("Accord du cortège : ${parent.accord} %", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AG.Body)
                Bar(parent.accord / 100f, AG.Grey, height = 8)
            }
            Title("Ta version améliorée", size = 15)
            AgoraField(text, { text = it.take(AgoraRules.MAX_TEXT) }, "Écris une meilleure version", minLines = 3)
            Section(bg = AG.Cream) {
                Title("Les règles du duel", size = 15)
                Text(
                    "• Les deux versions s'affichent sur le mur.\n" +
                        "• Dès que ta version a ${AgoraRules.DUEL_MIN_VOTES} votes et un meilleur accord, elle remplace l'ancienne.\n" +
                        "• Vous devenez co-auteurs : les points sont partagés moitié-moitié.\n" +
                        "• Si ta version fait moins bien en 10 minutes, l'ancienne reste.",
                    fontSize = 13.sp, color = AG.Body, lineHeight = 19.sp
                )
            }
            BigButton("Lancer le duel", enabled = text.trim().length >= 3 && text.trim() != parent.text) {
                val refused = AgoraHub.propose(parent.kind, text, parentKey)
                if (refused == null) {
                    toast("Duel lancé ! Fais voter les gens autour de toi.")
                    AgoraNav.page.value = null
                } else toast(refused)
            }
        }
    }
}

// ------------------------------------------------------------------- radar

@Composable
fun RadarPage(snap: AgoraSnapshot) {
    val context = LocalContext.current
    var background by remember { mutableStateOf(MeshServicePreferences.isBackgroundEnabled(true)) }
    val transition = rememberInfiniteTransition(label = "radar")
    val sweep by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(4000, easing = LinearEasing), RepeatMode.Restart), label = "balayage")
    val total = snap.meshPeers + 1
    val agora = snap.agoraPlayers.coerceAtMost(total)
    Column(Modifier.fillMaxSize().background(AG.Ink)) {
        PageHeader("Radar du cortège")
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Gap12
        ) {
            Text("$total", color = AG.Paper, fontWeight = FontWeight.Black, fontSize = 64.sp)
            Text("téléphones reliés (toi compris)", color = AG.Light, fontSize = 14.sp)
            Canvas(Modifier.size(280.dp)) {
                val c = Offset(size.width / 2, size.height / 2)
                val r = size.minDimension / 2
                for (k in 1..3) drawCircle(Color(0xFF3A3642), radius = r * k / 3f, center = c, style = Stroke(width = 3f))
                rotate(sweep, c) {
                    drawCircle(
                        Brush.sweepGradient(listOf(AG.Yellow.copy(alpha = 0.35f), Color.Transparent, Color.Transparent), center = c),
                        radius = r, center = c
                    )
                }
                val dots = min(total - 1, 44)
                for (i in 0 until dots) {
                    val a = i * 2.39996
                    val rr = r * (0.25f + ((i * 53) % 112) / 150f)
                    val p = Offset(c.x + (rr * cos(a)).toFloat(), c.y + (rr * sin(a)).toFloat())
                    drawCircle(if (i < agora - 1) AG.Yellow else AG.Grey, radius = 12f, center = p)
                }
                drawCircle(AG.Yellow, radius = 34f, center = c)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("● $agora avec AGORA", color = AG.Yellow, fontSize = 13.sp)
                Text("● ${total - agora} avec BitChat seul", color = AG.Light, fontSize = 13.sp)
            }
            Text("Les points sont décoratifs : AGORA n'utilise aucune position.", color = Color(0xFFA9A2B0), fontSize = 11.sp)
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AG.Ink2).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Relais en arrière-plan", color = AG.Paper, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Switch(
                        checked = background,
                        onCheckedChange = {
                            background = it
                            MeshServicePreferences.setBackgroundEnabled(it)
                            if (it) try { MeshForegroundService.start(context) } catch (_: Exception) { }
                        },
                        colors = SwitchDefaults.colors(checkedTrackColor = AG.Yellow, checkedThumbColor = AG.Ink)
                    )
                }
                Text("${snap.relayedTotal} messages relayés", color = AG.Yellow, fontWeight = FontWeight.Black, fontSize = 26.sp)
                Text(
                    "AGORA est un vrai téléphone BitChat : il fait suivre les messages de tout le monde, même de ceux qui ne jouent pas. " +
                        "Tant que le relais reste allumé, même téléphone dans la poche, tu fais grandir le réseau BitChat. " +
                        "+1 point « Relais » tous les ${AgoraRules.RELAY_MESSAGES_PER_POINT} messages (50 par jour au maximum).",
                    color = AG.Light, fontSize = 13.sp, lineHeight = 19.sp
                )
            }
        }
    }
}

// ------------------------------------------------------------- mode poche

@Composable
fun PochePage() {
    val context = LocalContext.current
    val settings by AgoraNotifier.settings.collectAsState()
    var granted by remember {
        mutableStateOf(Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    Column(Modifier.fillMaxSize().background(Color(0xFF0F0E13))) {
        PageHeader("Mode poche")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Gap12) {
            Text("L'écran reste éteint, le téléphone vibre seulement pour ce qui compte. Tu réponds aux assemblées directement depuis la notification.", color = AG.Light, fontSize = 14.sp, lineHeight = 20.sp)
            if (!granted) {
                BigButton("Autoriser les notifications") { launcher.launch(Manifest.permission.POST_NOTIFICATIONS) }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AG.Ink2).padding(horizontal = 14.dp)) {
                PocketSwitch("Assemblées éclair", settings.assemblies) { AgoraNotifier.update(settings.copy(assemblies = it)) }
                PocketSwitch("Mes idées qui montent", settings.myIdeas) { AgoraNotifier.update(settings.copy(myIdeas = it)) }
                PocketSwitch("Fin de manche et paris", settings.round) { AgoraNotifier.update(settings.copy(round = it)) }
                PocketSwitch("Chaque lot de nouvelles idées", settings.newIdeas) { AgoraNotifier.update(settings.copy(newIdeas = it)) }
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AG.Ink2).padding(14.dp), verticalArrangement = Gap8) {
                Text("Les vibrations (touche pour essayer)", color = AG.Paper, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                VibrationRow("·", "une courte : nouvelles idées à voter") { AgoraNotifier.testVibration(0) }
                VibrationRow("· ·", "deux courtes : ton idée monte") { AgoraNotifier.testVibration(1) }
                VibrationRow("———", "une longue : assemblée éclair") { AgoraNotifier.testVibration(2) }
            }
            Text("Garde les yeux sur la foule : l'appli te prévient seulement quand ça compte.", color = AG.Light, fontSize = 13.sp)
        }
    }
}

@Composable
private fun PocketSwitch(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = AG.Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Switch(checked = on, onCheckedChange = onChange, colors = SwitchDefaults.colors(checkedTrackColor = AG.Yellow, checkedThumbColor = AG.Ink))
    }
}

@Composable
private fun VibrationRow(sign: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).heightIn(min = 44.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(sign, color = AG.Yellow, fontWeight = FontWeight.Black, fontSize = 18.sp, modifier = Modifier.width(64.dp))
        Text(text, color = AG.Light, fontSize = 13.sp)
    }
}

// ----------------------------------------------------------------- journal

private data class JournalData(
    val date: String,
    val stats: List<Pair<String, String>>,
    val top: List<Pair<String, Int>>,
    val decisions: List<String>,
    val pseudo: String,
    val level: String,
    val points: Int,
    val badges: String
)

private fun journalData(snap: AgoraSnapshot): JournalData {
    val day = snap.dayStats
    val top = if (snap.book.isNotEmpty()) snap.book.take(3).map { it.text to it.accord } else snap.wall.take(3).map { it.text to it.accord }
    val total = snap.myDisplayTotal
    return JournalData(
        date = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date()),
        stats = listOf(
            "${maxOf(day?.maxPhones ?: 0, snap.meshPeers + 1)}" to "téléphones reliés (max)",
            "${day?.rounds ?: 0}" to "manches terminées",
            "${day?.votes ?: 0}" to "votes donnés par moi",
            "${day?.relayed ?: 0}" to "messages relayés"
        ),
        top = top,
        decisions = day?.decisions?.takeLast(3) ?: emptyList(),
        pseudo = AgoraNames.pseudo(snap.myId),
        level = AgoraProgress(MemoryStore()).level(total).title,
        points = total,
        badges = "${snap.badges.count { it.got }} badges"
    )
}

@Composable
fun JournalPage(snap: AgoraSnapshot) {
    val context = LocalContext.current
    val data = journalData(snap)
    Column(Modifier.fillMaxSize()) {
        PageHeader("Journal de la manif")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Gap12) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(AG.Ink).padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row {
                    Text("AGORA", color = AG.Yellow, fontWeight = FontWeight.Black, fontSize = 20.sp, modifier = Modifier.weight(1f))
                    Text(data.date, color = AG.Light, fontSize = 12.sp)
                }
                Text("Journal du cortège", color = AG.Paper, fontWeight = FontWeight.Black, fontSize = 30.sp)
                data.stats.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Gap8) {
                        pair.forEach { (n, label) ->
                            Column(Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(AG.Ink2).padding(10.dp)) {
                                Text(n, color = AG.Yellow, fontWeight = FontWeight.Black, fontSize = 22.sp)
                                Text(label, color = AG.Light, fontSize = 12.sp)
                            }
                        }
                    }
                }
                Text("LES IDÉES QUI ONT RASSEMBLÉ", color = AG.Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                if (data.top.isEmpty()) Text("Pas encore d'idée.", color = AG.Light, fontSize = 14.sp)
                data.top.forEachIndexed { i, (t, acc) ->
                    Row {
                        Text("${i + 1}", color = AG.Paper, fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(22.dp))
                        Text(t, color = AG.Paper, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("$acc %", color = AG.Paper, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
                if (data.decisions.isNotEmpty()) {
                    Text("CE QUE LE CORTÈGE A DÉCIDÉ", color = AG.Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                    data.decisions.forEach { Text(it, color = AG.Paper, fontSize = 14.sp) }
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AG.Yellow).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Moi · ${data.pseudo}", color = AG.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(data.level, color = AG.Ink, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("${data.points} pts", color = AG.Ink, fontWeight = FontWeight.Black, fontSize = 20.sp)
                        Text(data.badges, color = AG.Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Text("Fait sans internet, sans GPS et sans vrai nom", color = Color(0xFFA9A2B0), fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            Row(
                Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp)).background(AG.Ink).clickable { shareJournal(context, data) },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, tint = AG.Yellow)
                Spacer(Modifier.width(8.dp))
                Text("Partager l'image", color = AG.Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
            Text("L'image est créée sur ton téléphone. Elle n'est envoyée que si tu choisis une appli pour la partager.", fontSize = 12.sp, color = AG.Muted)
        }
    }
}

/** Dessine le journal dans une image 1080×1920 et ouvre le menu « Partager ». */
private fun shareJournal(context: Context, d: JournalData) {
    try {
        val w = 1080; val h = 1920
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val ink = 0xFF17151C.toInt(); val ink2 = 0xFF2A2731.toInt(); val yellow = 0xFFFFD23F.toInt()
        val paper = 0xFFF6F3EC.toInt(); val light = 0xFFD8D2C6.toInt()
        c.drawColor(ink)
        fun paint(size: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
        }
        fun block(text: String, p: TextPaint, x: Float, y: Float, width: Int): Float {
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, p, width).setAlignment(Layout.Alignment.ALIGN_NORMAL).build()
            c.save(); c.translate(x, y); layout.draw(c); c.restore()
            return y + layout.height
        }
        var y = 110f
        c.drawText("AGORA", 80f, y, paint(64f, yellow, true))
        val dp = paint(36f, light)
        c.drawText(d.date, w - 80f - dp.measureText(d.date), y, dp)
        y = block("Journal du cortège", paint(104f, paper, true), 80f, y + 50f, w - 160) + 40f
        val boxW = (w - 160 - 30) / 2f
        d.stats.forEachIndexed { i, (n, label) ->
            val bx = 80f + (i % 2) * (boxW + 30f)
            val by = y + (i / 2) * 200f
            c.drawRoundRect(RectF(bx, by, bx + boxW, by + 170f), 30f, 30f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink2 })
            c.drawText(n, bx + 30f, by + 85f, paint(64f, yellow, true))
            c.drawText(label, bx + 30f, by + 140f, paint(34f, light))
        }
        y += 430f
        c.drawText("LES IDÉES QUI ONT RASSEMBLÉ", 80f, y, paint(38f, yellow, true))
        y += 30f
        d.top.forEachIndexed { i, (t, acc) ->
            y = block("${i + 1}.  $t  ($acc %)", paint(44f, paper), 80f, y + 20f, w - 160) + 10f
        }
        if (d.decisions.isNotEmpty()) {
            y += 50f
            c.drawText("CE QUE LE CORTÈGE A DÉCIDÉ", 80f, y, paint(38f, yellow, true))
            y += 30f
            d.decisions.forEach { y = block(it, paint(42f, paper), 80f, y + 15f, w - 160) + 5f }
        }
        val by = h - 380f
        c.drawRoundRect(RectF(80f, by, w - 80f, by + 200f), 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = yellow })
        c.drawText("Moi · ${d.pseudo}", 120f, by + 75f, paint(36f, ink, true))
        c.drawText(d.level, 120f, by + 145f, paint(52f, ink, true))
        val pts = "${d.points} pts"
        val pp = paint(64f, ink, true)
        c.drawText(pts, w - 120f - pp.measureText(pts), by + 100f, pp)
        val bp = paint(34f, ink, true)
        c.drawText(d.badges, w - 120f - bp.measureText(d.badges), by + 155f, bp)
        val foot = "Fait sans internet, sans GPS et sans vrai nom"
        val fp = paint(32f, 0xFFA9A2B0.toInt())
        c.drawText(foot, (w - fp.measureText(foot)) / 2f, h - 90f, fp)

        val dir = File(context.cacheDir, "agora").apply { mkdirs() }
        val file = File(dir, "journal-agora.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Partager le journal").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        android.widget.Toast.makeText(context, "Impossible de créer l'image : ${e.message}", android.widget.Toast.LENGTH_LONG).show()
    }
}
