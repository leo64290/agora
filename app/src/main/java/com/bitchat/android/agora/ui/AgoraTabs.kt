package com.bitchat.android.agora.ui

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.agora.AgoraHub
import com.bitchat.android.agora.AgoraNames
import com.bitchat.android.agora.AgoraRules
import com.bitchat.android.agora.AgoraSnapshot
import com.bitchat.android.agora.AssemblyView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun Section(modifier: Modifier = Modifier, bg: Color = AG.Card, content: @Composable () -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(bg).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) { content() }
}

@Composable
fun Title(text: String, color: Color = AG.Ink, size: Int = 17) {
    Text(text, fontWeight = FontWeight.ExtraBold, fontSize = size.sp, color = color)
}

@Composable
fun Choice(text: String, selected: Boolean, enabled: Boolean = true, trailing: String? = null, onClick: () -> Unit) {
    val bg = if (selected) AG.Cream else AG.Card
    val border = when { selected -> AG.Ink; enabled -> AG.Border; else -> AG.Track }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .border(BorderStroke(2.dp, border), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(18.dp).clip(CircleShape)
                .background(if (selected) AG.Ink else Color.Transparent)
                .border(2.dp, if (selected) AG.Yellow else AG.Grey, CircleShape)
        )
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (enabled || selected) AG.Ink else AG.Grey, modifier = Modifier.weight(1f))
        trailing?.let { Text(it, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AG.Muted) }
    }
}

private fun hhmm(t: Long): String = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(t))

// ------------------------------------------------------------------- manche

@Composable
fun MancheTab(snap: AgoraSnapshot, toast: (String?) -> Unit) {
    val r = snap.round ?: return
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(AG.Ink).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(clock(r.secondsLeft), color = AG.Paper, fontWeight = FontWeight.Black, fontSize = 52.sp, modifier = Modifier.weight(1f))
                Text("avant de graver\nl'idée gagnante", color = AG.Light, fontSize = 13.sp, textAlign = TextAlign.End)
            }
            Bar(r.progress, AG.Yellow, AG.Ink2, height = 8)
            Text(
                "Manche de ${hhmm(r.round * AgoraRules.ROUND_MS)} à ${hhmm((r.round + 1) * AgoraRules.ROUND_MS)}",
                color = AG.Light, fontSize = 12.sp
            )
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Gap12) {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Title("Pari Flair")
                    Spacer(Modifier.weight(1f))
                    Tag("+${AgoraRules.BET_POINTS} pts si tu as raison", AG.Yellow)
                }
                when {
                    r.myBet != null -> Text("Ton pari : « ${r.myBet} ». Résultat à la fin du compte à rebours.", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AG.Ink)
                    !r.betOpen -> Text("Les paris sont fermés pour cette manche (ils ferment 5 minutes avant la fin).", fontSize = 13.sp, color = AG.Body)
                    r.betOptions.isEmpty() -> Text("Pas encore d'idée sur le mur : reviens dans un moment.", fontSize = 13.sp, color = AG.Body)
                    else -> {
                        Text("Quelle idée sera n°1 à la fin de la manche ? Un seul pari par manche.", fontSize = 13.sp, color = AG.Body)
                        r.betOptions.forEach { v ->
                            Choice(v.text, selected = false, trailing = "n°${v.rank}") { toast(AgoraHub.bet(v.key) ?: "Pari placé !") }
                        }
                    }
                }
            }
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Title("Défis du cortège")
                    Spacer(Modifier.weight(1f))
                    Text("tous ensemble", fontSize = 12.sp, color = AG.Muted)
                }
                r.challenges.forEach { c ->
                    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row {
                            Text(c.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AG.Ink, modifier = Modifier.weight(1f))
                            Text(if (c.done) "Réussi !" else "${c.current} / ${c.target}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = AG.Ink)
                        }
                        Bar(c.current.toFloat() / c.target, if (c.done) AG.Yellow else AG.Ink, height = 10)
                    }
                }
                Text("Chaque défi réussi compte pour le badge de tout le monde.", fontSize = 12.sp, color = AG.Muted)
            }
            Title("Le Livre du cortège")
            if (snap.book.isEmpty()) {
                Text(
                    "Rien de gravé pour l'instant. À ${hhmm((r.round + 1) * AgoraRules.ROUND_MS)}, l'idée en tête (avec au moins 3 votes) sera gravée ici.",
                    fontSize = 13.sp, color = AG.Body
                )
            }
            snap.book.sortedByDescending { it.round }.forEach { e ->
                Section(bg = AG.Ink) {
                    Text("MANCHE DE ${hhmm(e.round * AgoraRules.ROUND_MS)} · GRAVÉE", color = AG.Yellow, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    Text(e.text, color = AG.Paper, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text("Couronnée : ${AgoraNames.pseudos(e.authorIds)} · accord ${e.accord} %", color = AG.Light, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

// --------------------------------------------------------------- assemblée

@Composable
fun AssembleeTab(snap: AgoraSnapshot, toast: (String?) -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp), verticalArrangement = Gap12) {
        Text(
            "Une question, un minuteur, tout le cortège vote. Le résultat reste caché jusqu'à la fin, " +
                "pour que chacun vote selon son idée et pas selon les autres.",
            fontSize = 13.sp, color = AG.Body
        )
        Row(
            Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
                .border(2.dp, AG.Ink, RoundedCornerShape(27.dp))
                .clickable { AgoraNav.page.value = AgoraPage.NouvelleAssemblee },
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = AG.Ink)
            Spacer(Modifier.width(8.dp))
            Text("Lancer une assemblée", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = AG.Ink)
        }
        if (snap.assemblies.isEmpty()) {
            Text("Aucune assemblée en cours. Lance la première !", fontSize = 14.sp, color = AG.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 20.dp))
        }
        snap.assemblies.forEach { a -> AssemblyCard(a, toast) }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun AssemblyCard(a: AssemblyView, toast: (String?) -> Unit) {
    Section {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("posée par ${AgoraNames.pseudo(a.authorId)}${if (a.isMine) " (toi)" else ""}", fontSize = 12.sp, color = AG.Muted, modifier = Modifier.weight(1f))
            Tag(if (a.ended) "Terminé" else clock(a.secondsLeft), if (a.ended) AG.Line else AG.Yellow)
        }
        Text(a.question, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold, color = AG.Ink, lineHeight = 26.sp)
        if (!a.ended) {
            a.choices.forEachIndexed { i, label ->
                Choice(label, selected = a.myChoice == i) { toast(AgoraHub.ballot(a.key, i)) }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AG.Soft).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = null, tint = AG.Body, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text("${a.voters} personne(s) ont voté. Les chiffres s'affichent à la fin.", fontSize = 13.sp, color = AG.Body)
            }
            if (a.myChoice != null) Text("Ton vote est enregistré. Tu peux changer d'avis jusqu'à la fin.", fontSize = 13.sp, color = AG.Body)
        } else {
            val results = a.results ?: emptyList()
            a.choices.forEachIndexed { i, label ->
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Row {
                        Text(label + if (a.myChoice == i) " (ton choix)" else "", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AG.Ink, modifier = Modifier.weight(1f))
                        Text("${results.getOrElse(i) { 0 }} %", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AG.Ink)
                    }
                    Bar(results.getOrElse(i) { 0 } / 100f, if (a.winner == i) AG.Ink else AG.Grey, height = 12)
                }
            }
            Text(
                if (a.voters == 0) "Personne n'a voté." else "Décision : ${a.winner?.let { a.choices[it] } ?: "égalité"}",
                color = AG.Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AG.Ink).padding(14.dp)
            )
            Text("${a.voters} votant(s)", fontSize = 13.sp, color = AG.Muted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun NouvelleAssembleePage(toast: (String?) -> Unit) {
    var question by remember { mutableStateOf("") }
    val choices = remember { mutableStateListOf("Oui", "Non") }
    var minutes by remember { mutableIntStateOf(5) }
    Column(Modifier.fillMaxSize()) {
        PageHeader("Lancer une assemblée")
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Gap12) {
            Title("1. La question")
            AgoraField(question, { question = it.take(AgoraRules.MAX_TEXT) }, "Ex : On fait une pause place de la République ?", minLines = 2)
            Title("2. Les réponses (2 à 4)")
            choices.forEachIndexed { i, c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) { AgoraField(c, { choices[i] = it.take(AgoraRules.MAX_CHOICE_TEXT) }, "Réponse ${i + 1}") }
                    if (choices.size > 2) {
                        Box(Modifier.size(44.dp).clip(CircleShape).clickable { choices.removeAt(i) }, contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = "Retirer", tint = AG.Muted)
                        }
                    }
                }
            }
            if (choices.size < AgoraRules.MAX_CHOICES) {
                Text(
                    "+ Ajouter une réponse", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = AG.Ink,
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { choices.add("") }.padding(8.dp)
                )
            }
            Title("3. Le minuteur")
            Row(horizontalArrangement = Gap8) {
                listOf(2, 5, 10).forEach { m ->
                    val on = minutes == m
                    Text(
                        "$m min", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = if (on) AG.Yellow else AG.Ink, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(if (on) AG.Ink else AG.Card)
                            .border(2.dp, if (on) AG.Ink else AG.Border, RoundedCornerShape(12.dp))
                            .clickable { minutes = m }.padding(vertical = 14.dp)
                    )
                }
            }
            Text("Une seule assemblée à la fois par personne. Les autres reçoivent une longue vibration et peuvent voter depuis la notification.", fontSize = 12.sp, color = AG.Muted)
            BigButton("Lancer le vote") {
                val refused = AgoraHub.createAssembly(question, choices.toList(), minutes * 60_000L)
                if (refused == null) {
                    toast("Assemblée lancée : elle part de téléphone en téléphone")
                    AgoraNav.page.value = null
                    AgoraNav.tab.value = AgoraTab.ASSEMBLEE
                } else toast(refused)
            }
        }
    }
}

@Composable
fun AgoraField(value: String, onChange: (String) -> Unit, placeholder: String, minLines: Int = 1) {
    OutlinedTextField(
        value = value, onValueChange = onChange,
        placeholder = { Text(placeholder, color = AG.Grey) },
        minLines = minLines,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = AG.Ink, unfocusedBorderColor = AG.Border,
            focusedContainerColor = AG.Card, unfocusedContainerColor = AG.Card,
            focusedTextColor = AG.Ink, unfocusedTextColor = AG.Ink, cursorColor = AG.Ink
        ),
        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp)
    )
}

@Composable
fun BigButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp, textAlign = TextAlign.Center,
        color = if (enabled) AG.Ink else AG.Muted,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(if (enabled) AG.Yellow else AG.Line)
            .clickable(enabled = enabled, onClick = onClick).padding(vertical = 17.dp)
    )
}

// --------------------------------------------------------------------- moi

@Composable
fun MoiTab(snap: AgoraSnapshot) {
    var selectedBadge by remember { mutableIntStateOf(-1) }
    val total = snap.myDisplayTotal
    val level = remember(total) { com.bitchat.android.agora.AgoraProgress(com.bitchat.android.agora.MemoryStore()).level(total) }
    val my = snap.myScore
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Column(Modifier.fillMaxWidth().background(AG.Ink).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(AgoraNames.pseudo(snap.myId), color = AG.Light, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("$total", color = AG.Paper, fontWeight = FontWeight.Black, fontSize = 60.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(level.title, color = AG.Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text("sur les 30 dernières minutes", color = AG.Light, fontSize = 12.sp)
                }
            }
            Bar(level.progress, AG.Yellow, AG.Ink2, height = 8)
            Text(
                if (level.nextTitle != null) "Encore ${(level.nextAt ?: 0) - total} points pour devenir « ${level.nextTitle} »" else "Niveau maximum : tu es une Agora vivante !",
                color = AG.Light, fontSize = 12.sp
            )
        }
        Column(Modifier.padding(14.dp), verticalArrangement = Gap12) {
            Section {
                ScoreRow("Rassembleur", "Tes idées aimées bien au-delà de ton groupe", my?.rassembleur ?: 0)
                ScoreRow("Participation", "2 pts par vote (20 votes max), 3 par assemblée", my?.participation ?: 0)
                ScoreRow("Ouverture", "« Pour » à des gens qui votent autrement que toi", my?.ouverture ?: 0)
                ScoreRow("Flair", "Paris gagnés sur la future n°1", my?.flair ?: 0)
                ScoreRow("Relais", "${snap.relayedTotal} messages AGORA et BitChat relayés au total", snap.relayPoints)
            }
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Title("Mes badges")
                    Spacer(Modifier.weight(1f))
                    Text("${snap.badges.count { it.got }} sur ${snap.badges.size}", fontSize = 12.sp, color = AG.Muted)
                }
                snap.badges.chunked(4).forEachIndexed { row, list ->
                    Row(horizontalArrangement = Gap8) {
                        list.forEachIndexed { col, b ->
                            val idx = row * 4 + col
                            Column(
                                Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                    .background(if (selectedBadge == idx) AG.Soft else Color.Transparent)
                                    .clickable { selectedBadge = idx }.padding(vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier.size(48.dp).clip(CircleShape).background(if (b.got) AG.Yellow else AG.Track),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(if (b.got) Icons.Filled.WorkspacePremium else Icons.Filled.Lock, contentDescription = null, tint = if (b.got) AG.Ink else AG.Grey)
                                }
                                Spacer(Modifier.height(5.dp))
                                Text(b.name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (b.got) AG.Ink else AG.Muted, textAlign = TextAlign.Center, lineHeight = 13.sp)
                            }
                        }
                        repeat(4 - list.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                snap.badges.getOrNull(selectedBadge)?.let { b ->
                    Text("${b.name} : ${b.desc} (${b.progress})", fontSize = 13.sp, color = AG.Ink, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AG.Soft).padding(10.dp))
                } ?: Text("Les badges restent sur ton téléphone d'une manif à l'autre. Touche un badge pour voir comment l'obtenir.", fontSize = 12.sp, color = AG.Muted)
            }
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AG.Card)) {
                LinkRow("Journal de la manif") { AgoraNav.page.value = AgoraPage.Journal }
                LinkRow("Mode poche (vibrations)") { AgoraNav.page.value = AgoraPage.Poche }
                LinkRow("Radar et relais BitChat") { AgoraNav.page.value = AgoraPage.Radar }
                LinkRow("Chat BitChat", last = true) { AgoraNav.showChat.value = true }
            }
            Title("Classement")
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AG.Card)) {
                val top = snap.scores.take(5)
                val list = if (top.none { it.playerId == snap.myId } && my != null) top + my else top
                list.forEach { s ->
                    val rank = snap.scores.indexOfFirst { it.playerId == s.playerId } + 1
                    val me = s.playerId == snap.myId
                    Row(
                        Modifier.fillMaxWidth().background(if (me) AG.Cream else Color.Transparent).padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$rank", fontWeight = FontWeight.ExtraBold, modifier = Modifier.width(28.dp), color = AG.Ink)
                        Text(AgoraNames.pseudo(s.playerId) + if (me) " (toi)" else "", fontWeight = if (me) FontWeight.Bold else FontWeight.SemiBold, color = AG.Ink, modifier = Modifier.weight(1f))
                        Text("${s.total}", fontWeight = FontWeight.ExtraBold, color = AG.Ink)
                    }
                }
            }
            Text(
                "On note les idées, jamais les personnes. Les points Relais comptent pour ton niveau mais pas pour ce classement (personne ne peut les vérifier).",
                fontSize = 12.sp, color = AG.Muted
            )
            Section(bg = AG.Soft) {
                Title("Ta protection", size = 15)
                Text(
                    "Pseudo automatique, aucune position GPS, idées et votes effacés après 30 minutes. " +
                        "Mode panique : dans le chat BitChat, touche 3 fois le titre pour tout effacer d'un coup (badges compris).",
                    fontSize = 13.sp, color = AG.Body
                )
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ScoreRow(name: String, how: String, pts: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = AG.Ink)
            Text(how, fontSize = 12.sp, color = AG.Muted)
        }
        Text("$pts", fontWeight = FontWeight.Black, fontSize = 20.sp, color = AG.Ink)
    }
}

@Composable
private fun LinkRow(text: String, last: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (text.startsWith("Chat")) {
            Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null, tint = AG.Ink, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = AG.Ink, modifier = Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = AG.Ink)
    }
    if (!last) Box(Modifier.fillMaxWidth().height(1.dp).background(AG.Track))
}
