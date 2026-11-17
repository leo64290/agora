package com.bitchat.android.agora.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.agora.AgoraHub
import com.bitchat.android.agora.AgoraNames
import com.bitchat.android.agora.AgoraSnapshot
import com.bitchat.android.agora.IdeaKind
import com.bitchat.android.agora.IdeaView
import kotlin.math.roundToInt

fun kindIcon(kind: IdeaKind): ImageVector = when (kind) {
    IdeaKind.SLOGAN -> Icons.Filled.Campaign
    IdeaKind.DEMAND -> Icons.Filled.Flag
    IdeaKind.ACTION -> Icons.Filled.Explore
}

@Composable
fun MurTab(snap: AgoraSnapshot, toast: (String?) -> Unit) {
    if (AgoraNav.cardsMode.value) {
        CardsMode(snap, toast)
        return
    }
    Box(Modifier.fillMaxSize()) {
        if (snap.wall.isEmpty()) {
            EmptyWall(snap)
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
                verticalArrangement = Gap12
            ) {
                item {
                    Text(
                        "Les idées qui rassemblent montent en haut. Un vote compte 30 minutes.",
                        color = AG.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
                items(snap.wall, key = { it.key }) { v -> IdeaCard(v, toast) }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { AgoraNav.page.value = AgoraPage.Proposer },
            containerColor = AG.Yellow, contentColor = AG.Ink,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("Proposer", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        )
    }
}

@Composable
private fun EmptyWall(snap: AgoraSnapshot) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Le mur est vide", fontWeight = FontWeight.Black, fontSize = 26.sp, color = AG.Ink)
        Spacer(Modifier.height(10.dp))
        Text(
            "Propose la première idée : un slogan, une revendication ou une action. " +
                "Elle passera de téléphone en téléphone, sans internet.",
            color = AG.Body, fontSize = 15.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(14.dp))
        Text(
            if (snap.meshPeers == 0) "Aucun autre téléphone BitChat à portée pour l'instant."
            else "${snap.meshPeers} téléphone(s) BitChat à portée.",
            color = AG.Muted, fontSize = 13.sp, textAlign = TextAlign.Center
        )
    }
}

@Composable
fun KindChip(kind: IdeaKind) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(AG.Soft).padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(kindIcon(kind), contentDescription = null, tint = AG.Body, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(5.dp))
        Text(kind.label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AG.Body)
    }
}

@Composable
fun Tag(text: String, bg: Color, fg: Color = AG.Ink) {
    Text(
        text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(bg).padding(horizontal = 9.dp, vertical = 4.dp)
    )
}

@Composable
fun IdeaCard(v: IdeaView, toast: (String?) -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AG.Card).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${v.rank}", fontWeight = FontWeight.Black, fontSize = 22.sp, color = AG.Ink, modifier = Modifier.width(30.dp))
            KindChip(v.kind)
            Spacer(Modifier.weight(1f))
            when {
                v.bridging -> Tag("Rassembleuse", AG.Yellow)
                v.divisive -> Tag("Divise le cortège", AG.Line, AG.Body)
            }
        }
        Text(
            if (v.kind == IdeaKind.SLOGAN) "« ${v.text} »" else v.text,
            fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = AG.Ink, lineHeight = 23.sp
        )
        v.duelNote?.let { Tag(it, AG.Cream, AG.Body) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "par ${AgoraNames.pseudos(v.authorIds)}${if (v.isMine) " (toi)" else ""} · ${v.pour} pour · ${v.contre} contre",
                fontSize = 12.sp, color = AG.Muted, modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.Timer, contentDescription = null, tint = AG.Muted, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(3.dp))
            Text("${v.minutesLeft} min", fontSize = 12.sp, color = AG.Muted)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Accord du cortège : ${v.accord} %", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AG.Body)
            Bar(v.accord / 100f, AG.Ink)
        }
        Bar(v.life, AG.Grey, AG.Soft, height = 3)
        if (v.isMine) {
            Text("C'est ton idée : fais-la voter autour de toi !", fontSize = 13.sp, color = AG.Body)
        } else {
            Row(horizontalArrangement = Gap8) {
                VoteButton("Pour", Icons.Filled.ThumbUp, v.myVote == 1, 1, Modifier.weight(1f)) { toast(AgoraHub.vote(v.key, 1)) }
                VoteButton("Contre", Icons.Filled.ThumbDown, v.myVote == -1, -1, Modifier.weight(1f)) { toast(AgoraHub.vote(v.key, -1)) }
                VoteButton("Passer", Icons.Filled.Remove, v.myVote == 0, 0, Modifier.weight(1f)) { toast(AgoraHub.vote(v.key, 0)) }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!v.isMine) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).clickable { AgoraNav.page.value = AgoraPage.Ameliorer(v.key) }
                        .heightIn(min = 40.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = AG.Ink, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Améliorer cette idée", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = AG.Ink)
                }
            }
            Spacer(Modifier.weight(1f))
            if (v.rank == 1 && !v.isMine) {
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(AG.Yellow).clickable { AgoraHub.clap(v.key) }
                        .heightIn(min = 40.dp).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Celebration, contentDescription = null, tint = AG.Ink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Applaudir · ${v.claps}", fontSize = 13.sp, fontWeight = FontWeight.ExtraBold, color = AG.Ink)
                }
            }
        }
    }
}

@Composable
private fun VoteButton(label: String, icon: ImageVector, on: Boolean, kind: Int, modifier: Modifier, onClick: () -> Unit) {
    val (bg, fg) = when {
        !on -> AG.Card to AG.Ink
        kind == 1 -> AG.Ink to AG.Yellow
        kind == -1 -> AG.Muted to AG.Card
        else -> AG.Line to AG.Ink
    }
    Row(
        modifier.height(44.dp).clip(RoundedCornerShape(12.dp)).background(bg)
            .border(BorderStroke(1.5.dp, if (on) bg else AG.Border), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = fg, fontWeight = FontWeight.Bold, fontSize = 14.sp)
    }
}

// ------------------------------------------------------------ vote en cartes

@Composable
private fun CardsMode(snap: AgoraSnapshot, toast: (String?) -> Unit) {
    var streak by remember { mutableIntStateOf(0) }
    var reveal by remember { mutableStateOf<Pair<String, Int>?>(null) }
    val card = snap.cards.firstOrNull()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (card == null) "Toutes les idées sont votées" else "${snap.cards.size} idée(s) à voter",
                color = AG.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.Bolt, contentDescription = null, tint = AG.Ink, modifier = Modifier.size(16.dp))
            Text("Série : $streak", color = AG.Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        if (card == null) {
            Column(
                Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("+${streak * 2}", fontWeight = FontWeight.Black, fontSize = 56.sp, color = AG.Ink)
                Text("Tu as voté sur toutes les idées !", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = AG.Ink, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Text(
                    "De nouvelles idées arrivent sans arrêt. Active « Chaque nouvelle idée » dans le mode poche pour être prévenu.",
                    color = AG.Body, fontSize = 15.sp, textAlign = TextAlign.Center
                )
            }
        } else {
            var offsetX by remember(card.key) { mutableFloatStateOf(0f) }
            val shown by animateFloatAsState(offsetX, label = "carte")
            fun doVote(value: Int) {
                val label = when (value) { 1 -> "Pour"; -1 -> "Contre"; else -> "Passer" }
                val refused = AgoraHub.vote(card.key, value)
                if (refused == null) {
                    streak += 1
                    reveal = label to card.accord
                } else {
                    toast(refused); offsetX = 0f
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f)) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 10.dp).padding(top = 14.dp).height(380.dp).clip(RoundedCornerShape(24.dp)).background(AG.Line))
                Column(
                    Modifier.fillMaxWidth().height(390.dp)
                        .offset { IntOffset(shown.roundToInt(), 0) }
                        .rotate(shown / 22f)
                        .clip(RoundedCornerShape(24.dp)).background(AG.Card)
                        .pointerInput(card.key) {
                            detectHorizontalDragGestures(
                                onDragEnd = {
                                    when {
                                        offsetX > 260f -> { offsetX = 1400f; doVote(1) }
                                        offsetX < -260f -> { offsetX = -1400f; doVote(-1) }
                                        else -> offsetX = 0f
                                    }
                                },
                                onDragCancel = { offsetX = 0f }
                            ) { _, dx -> offsetX += dx }
                        }
                        .padding(24.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KindChip(card.kind)
                        Spacer(Modifier.weight(1f))
                        Text("par ${AgoraNames.pseudos(card.authorIds)}", fontSize = 12.sp, color = AG.Muted)
                    }
                    Text(
                        if (card.kind == IdeaKind.SLOGAN) "« ${card.text} »" else card.text,
                        fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = AG.Ink, lineHeight = 32.sp
                    )
                    Row {
                        Text(
                            when {
                                offsetX > 80f -> "POUR"
                                offsetX < -80f -> "CONTRE"
                                else -> "Accord caché jusqu'à ton vote"
                            },
                            fontSize = if (offsetX > 80f || offsetX < -80f) 20.sp else 13.sp,
                            fontWeight = if (offsetX > 80f || offsetX < -80f) FontWeight.Black else FontWeight.Normal,
                            color = AG.Muted, modifier = Modifier.weight(1f)
                        )
                        Text("${card.minutesLeft} min", fontSize = 13.sp, color = AG.Muted)
                    }
                }
            }
        }
        reveal?.let { (label, accord) ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(AG.Ink).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Tu as dit $label · Accord du cortège : $accord %", color = AG.Paper, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("+2 pts", color = AG.Yellow, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (card != null) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                RoundButton(Icons.Filled.ThumbDown, "Contre", 68, AG.Card, AG.Ink) { toast(AgoraHub.vote(card.key, -1).also { if (it == null) { streak++; reveal = "Contre" to card.accord } }) }
                Spacer(Modifier.width(22.dp))
                RoundButton(Icons.Filled.ArrowUpward, "Passer", 52, AG.Card, AG.Muted) { toast(AgoraHub.vote(card.key, 0).also { if (it == null) { streak++; reveal = "Passer" to card.accord } }) }
                Spacer(Modifier.width(22.dp))
                RoundButton(Icons.Filled.ThumbUp, "Pour", 68, AG.Ink, AG.Yellow) { toast(AgoraHub.vote(card.key, 1).also { if (it == null) { streak++; reveal = "Pour" to card.accord } }) }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "Glisse la carte : à droite Pour, à gauche Contre",
                color = AG.Muted, fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun RoundButton(icon: ImageVector, label: String, size: Int, bg: Color, fg: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(bg)
            .border(2.dp, if (bg == AG.Card) AG.Ink else bg, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = label, tint = fg, modifier = Modifier.size((size * 0.4f).dp))
    }
}
