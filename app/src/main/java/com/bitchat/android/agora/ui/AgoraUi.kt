package com.bitchat.android.agora.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bitchat.android.agora.AgoraHub
import com.bitchat.android.agora.AgoraSnapshot
import com.bitchat.android.ui.ChatScreen
import com.bitchat.android.ui.ChatViewModel
import kotlinx.coroutines.launch

/** Les couleurs d'AGORA (pensées pour être lisibles en plein soleil). */
object AG {
    val Ink = Color(0xFF17151C)
    val Ink2 = Color(0xFF2A2731)
    val Paper = Color(0xFFF6F3EC)
    val Card = Color(0xFFFFFFFF)
    val Yellow = Color(0xFFFFD23F)
    val Muted = Color(0xFF5A5560)
    val Body = Color(0xFF3B3740)
    val Line = Color(0xFFE4DED3)
    val Soft = Color(0xFFF1ECE3)
    val Track = Color(0xFFEFEAE1)
    val Light = Color(0xFFD8D2C6)
    val Cream = Color(0xFFFFF6D1)
    val Grey = Color(0xFF8A8390)
    val Border = Color(0xFFCFC8BB)
}

enum class AgoraTab { MUR, MANCHE, ASSEMBLEE, MOI }

sealed class AgoraPage {
    object Proposer : AgoraPage()
    data class Ameliorer(val parentKey: String) : AgoraPage()
    object Radar : AgoraPage()
    object Poche : AgoraPage()
    object Journal : AgoraPage()
    object NouvelleAssemblee : AgoraPage()
}

/** Où on est dans l'appli (onglet, page ouverte, chat BitChat). */
object AgoraNav {
    val tab = mutableStateOf(AgoraTab.MUR)
    val page = mutableStateOf<AgoraPage?>(null)
    val showChat = mutableStateOf(false)
    val cardsMode = mutableStateOf(false)

    /** Bouton retour d'Android. Renvoie true si on a géré. */
    fun handleBack(chatHandles: () -> Boolean): Boolean {
        if (showChat.value) {
            if (chatHandles()) return true
            showChat.value = false
            return true
        }
        if (page.value != null) { page.value = null; return true }
        if (tab.value != AgoraTab.MUR) { tab.value = AgoraTab.MUR; return true }
        return false
    }
}

/** Point d'entrée : le jeu, ou le chat BitChat normal. */
@Composable
fun AgoraRoot(chatViewModel: ChatViewModel) {
    val showChat by AgoraNav.showChat
    if (showChat) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(AG.Ink).statusBarsPadding().clickable { AgoraNav.showChat.value = false }
                    .heightIn(min = 48.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = AG.Yellow)
                Spacer(Modifier.width(10.dp))
                Text("Retour au jeu AGORA", color = AG.Yellow, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
            Box(Modifier.weight(1f).consumeWindowInsets(WindowInsets.statusBars)) { ChatScreen(viewModel = chatViewModel) }
        }
    } else {
        AgoraApp()
    }
}

@Composable
fun AgoraApp() {
    val snap by AgoraHub.state.collectAsState()
    val tab by AgoraNav.tab
    val page by AgoraNav.page
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val toast: (String?) -> Unit = { msg -> if (msg != null) scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
    LaunchedEffect(Unit) { AgoraHub.messages.collect { toast(it) } }

    Box(Modifier.fillMaxSize().background(AG.Paper)) {
        if (page != null) {
            // Pages ouvertes par-dessus : on laisse la place à la barre du bas et au clavier
            Box(Modifier.fillMaxSize().background(AG.Ink).navigationBarsPadding().imePadding()) { PageContent(page!!, snap, toast) }
        } else {
            Column(Modifier.fillMaxSize()) {
                AgoraHeader(snap)
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (tab) {
                        AgoraTab.MUR -> MurTab(snap, toast)
                        AgoraTab.MANCHE -> MancheTab(snap, toast)
                        AgoraTab.ASSEMBLEE -> AssembleeTab(snap, toast)
                        AgoraTab.MOI -> MoiTab(snap)
                    }
                }
                AgoraBottomBar(tab)
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 84.dp))
    }
}

@Composable
private fun PageContent(p: AgoraPage, snap: AgoraSnapshot, toast: (String?) -> Unit) {
    Box(Modifier.fillMaxSize().background(AG.Paper)) {
        when (p) {
            is AgoraPage.Proposer -> ProposerPage(toast)
            is AgoraPage.Ameliorer -> AmeliorerPage(snap, p.parentKey, toast)
            is AgoraPage.Radar -> RadarPage(snap)
            is AgoraPage.Poche -> PochePage()
            is AgoraPage.Journal -> JournalPage(snap)
            is AgoraPage.NouvelleAssemblee -> NouvelleAssembleePage(toast)
        }
    }
}

@Composable
private fun AgoraHeader(snap: AgoraSnapshot) {
    Column(Modifier.fillMaxWidth().background(AG.Ink).statusBarsPadding().padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("AGORA", color = AG.Yellow, fontWeight = FontWeight.Black, fontSize = 26.sp, letterSpacing = 1.sp)
            Spacer(Modifier.weight(1f))
            Pill(
                icon = Icons.Filled.Hub,
                text = "${snap.meshPeers + 1} tél.",
                onClick = { AgoraNav.page.value = AgoraPage.Radar }
            )
            Spacer(Modifier.width(8.dp))
            Pill(icon = Icons.AutoMirrored.Filled.Chat, text = "Chat", onClick = { AgoraNav.showChat.value = true })
        }
        if (AgoraNav.tab.value == AgoraTab.MUR) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Segmented(
                    left = "Mur", right = "Cartes",
                    rightSelected = AgoraNav.cardsMode.value,
                    onSelect = { AgoraNav.cardsMode.value = it }
                )
                Spacer(Modifier.weight(1f))
                snap.round?.let { r ->
                    Row(
                        Modifier.clip(RoundedCornerShape(18.dp)).clickable { AgoraNav.tab.value = AgoraTab.MANCHE }
                            .heightIn(min = 40.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = AG.Yellow, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Manche · ${clock(r.secondsLeft)}", color = AG.Yellow, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun Pill(icon: ImageVector, text: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(AG.Ink2).clickable(onClick = onClick)
            .heightIn(min = 40.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = AG.Yellow, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = AG.Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun Segmented(left: String, right: String, rightSelected: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(AG.Ink2).padding(3.dp)) {
        listOf(false to left, true to right).forEach { (isRight, label) ->
            val on = isRight == rightSelected
            Box(
                Modifier.width(78.dp).height(36.dp).clip(RoundedCornerShape(18.dp))
                    .background(if (on) AG.Yellow else Color.Transparent)
                    .clickable { onSelect(isRight) },
                contentAlignment = Alignment.Center
            ) {
                Text(label, color = if (on) AG.Ink else AG.Light, fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun AgoraBottomBar(tab: AgoraTab) {
    NavigationBar(containerColor = AG.Card) {
        val items = listOf(
            Triple(AgoraTab.MUR, "Mur", Icons.AutoMirrored.Filled.List),
            Triple(AgoraTab.MANCHE, "Manche", Icons.Filled.EmojiEvents),
            Triple(AgoraTab.ASSEMBLEE, "Assemblée", Icons.Filled.HowToVote),
            Triple(AgoraTab.MOI, "Moi", Icons.Filled.Person)
        )
        items.forEach { (t, label, icon) ->
            NavigationBarItem(
                selected = tab == t,
                onClick = { AgoraNav.tab.value = t },
                icon = { Icon(icon, contentDescription = null) },
                label = { Text(label, fontSize = 12.sp, fontWeight = if (tab == t) FontWeight.Bold else FontWeight.Medium) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = AG.Ink, selectedTextColor = AG.Ink,
                    unselectedIconColor = AG.Muted, unselectedTextColor = AG.Muted,
                    indicatorColor = AG.Yellow
                )
            )
        }
    }
}

/** En-tête simple avec bouton retour, pour les pages ouvertes par-dessus. */
@Composable
fun PageHeader(title: String, dark: Boolean = false) {
    Row(
        Modifier.fillMaxWidth().background(AG.Ink).statusBarsPadding().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(AG.Ink2).clickable { AgoraNav.page.value = null },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = AG.Paper)
        }
        Spacer(Modifier.width(12.dp))
        Text(title, color = AG.Yellow, fontWeight = FontWeight.Black, fontSize = 21.sp)
        if (dark) Spacer(Modifier.fillMaxHeight())
    }
}

/** Une barre de progression toute simple. */
@Composable
fun Bar(fraction: Float, color: Color, track: Color = AG.Track, height: Int = 6) {
    Box(Modifier.fillMaxWidth().height(height.dp).clip(RoundedCornerShape((height / 2).dp)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).fillMaxHeight().background(color))
    }
}

fun clock(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

val Gap8 = Arrangement.spacedBy(8.dp)
val Gap12 = Arrangement.spacedBy(12.dp)
