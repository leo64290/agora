package com.bitchat.android.agora

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

/** Identité de joueur : les 8 premiers octets de l'empreinte de la clé de signature BitChat. */
object AgoraIds {
    fun fromSigningKey(key: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(key).take(8).joinToString("") { "%02x".format(it) }
}

/** Tout ce que l'écran affiche, recalculé chaque seconde (et après chaque message). */
data class AgoraSnapshot(
    val ready: Boolean = false,
    val myId: String = "",
    val wall: List<IdeaView> = emptyList(),
    val cards: List<IdeaView> = emptyList(),
    val assemblies: List<AssemblyView> = emptyList(),
    val round: RoundView? = null,
    val scores: List<ScoreView> = emptyList(),
    val myScore: ScoreView? = null,
    val relayPoints: Int = 0,
    val relayedTotal: Long = 0,
    val meshPeers: Int = 0,
    val agoraPlayers: Int = 1,
    val badges: List<BadgeView> = emptyList(),
    val dayStats: DayStats? = null,
    val book: List<BookEntry> = emptyList()
) {
    /** Mon total affiché = points vérifiables + points Relais (qui ne comptent pas dans le classement). */
    val myDisplayTotal: Int get() = (myScore?.total ?: 0) + relayPoints
}

/**
 * Le chef d'orchestre d'AGORA sur le téléphone : il relie le moteur au réseau
 * BitChat, fait tourner l'horloge du jeu et prévient l'écran.
 */
object AgoraHub {
    private const val TAG = "AgoraHub"

    @Volatile private var engine: AgoraEngine? = null
    @Volatile private var progress: AgoraProgress? = null
    @Volatile private var sender: ((ByteArray) -> Unit)? = null
    @Volatile private var peerCounter: (() -> Int)? = null
    @Volatile private var snapshotSoonAt = 0L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private val refreshRequests = Channel<Unit>(Channel.CONFLATED)
    private val relayedPending = AtomicLong(0)

    private val _state = MutableStateFlow(AgoraSnapshot())
    val state: StateFlow<AgoraSnapshot> = _state

    /** Petits messages à afficher en bas de l'écran (« Défi réussi ! »…). */
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val messages: SharedFlow<String> = _messages

    @Synchronized
    fun attach(context: Context, myId: String, send: (ByteArray) -> Unit, peers: () -> Int) {
        val app = context.applicationContext
        if (engine?.myId != myId) engine = AgoraEngine(myId)
        if (progress == null) progress = AgoraProgress(PrefsStore(app))
        sender = send
        peerCounter = peers
        AgoraNotifier.init(app)
        if (loop == null) {
            loop = scope.launch { runLoop() }
            scope.launch { runRefresher() }
        }
        snapshotSoonAt = System.currentTimeMillis() + 5_000
        refresh()
    }

    // --------------------------------------------------------------- réseau

    fun onIncoming(playerId: String, payload: ByteArray) {
        val e = engine ?: return
        if (e.receive(playerId, payload)) refresh()
    }

    /** Un nouveau voisin Bluetooth : on lui renvoie nos données dans 5 s (le temps qu'il reçoive notre clé). */
    fun onPeerConnected() {
        val soon = System.currentTimeMillis() + 5_000
        if (snapshotSoonAt == 0L || snapshotSoonAt > soon) snapshotSoonAt = soon
    }

    fun noteRelayed(@Suppress("UNUSED_PARAMETER") type: UByte) {
        relayedPending.incrementAndGet()
    }

    private fun send(payload: ByteArray) {
        try { sender?.invoke(payload) } catch (t: Throwable) { Log.w(TAG, "envoi impossible : ${t.message}") }
    }

    // -------------------------------------------------------------- boucles

    private suspend fun runLoop() {
        var lastSnapshot = 0L
        var lastClaps = 0L
        var lastRelayFlush = 0L
        while (currentCoroutineContext().isActive) {
            try {
                val e = engine
                val p = progress
                if (e != null && p != null) {
                    val now = System.currentTimeMillis()
                    e.meshPeers = try { peerCounter?.invoke() ?: 0 } catch (_: Exception) { 0 }
                    p.onPhones(e.meshPeers + 1)
                    val events = e.tick()
                    if (events.isNotEmpty()) handleEvents(events, p)
                    if (now - lastClaps >= 30_000) {
                        lastClaps = now
                        e.flushClaps()?.let { send(it) }
                    }
                    val soon = snapshotSoonAt
                    if (now - lastSnapshot >= 90_000 || (soon != 0L && now >= soon)) {
                        lastSnapshot = now
                        snapshotSoonAt = 0L
                        e.snapshot().forEachIndexed { i, chunk ->
                            if (i > 0) delay(300)
                            send(chunk)
                        }
                    }
                    if (now - lastRelayFlush >= 10_000) {
                        lastRelayFlush = now
                        p.addRelayed(relayedPending.getAndSet(0))
                    }
                    publish(e, p)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "boucle AGORA : ${t.message}")
            }
            delay(1_000)
        }
    }

    private suspend fun runRefresher() {
        for (ignored in refreshRequests) {
            val e = engine
            val p = progress
            if (e != null && p != null) {
                try { publish(e, p) } catch (t: Throwable) { Log.w(TAG, "affichage : ${t.message}") }
            }
            delay(250)
        }
    }

    private fun refresh() {
        refreshRequests.trySend(Unit)
    }

    private fun publish(e: AgoraEngine, p: AgoraProgress) {
        val wall = e.wall()
        val scores = e.scores()
        _state.value = AgoraSnapshot(
            ready = true,
            myId = e.myId,
            wall = wall,
            cards = wall.filter { !it.isMine && it.myVote == null }.sortedByDescending { it.createdAt },
            assemblies = e.assemblies(),
            round = e.round(),
            scores = scores,
            myScore = scores.firstOrNull { it.playerId == e.myId },
            relayPoints = p.relayPoints(),
            relayedTotal = p.relayedTotal() + relayedPending.get(),
            meshPeers = e.meshPeers,
            agoraPlayers = e.agoraPlayerCount(),
            badges = p.badges(),
            dayStats = p.dayStats(),
            book = e.bookOfTheDay()
        )
    }

    private fun handleEvents(events: List<AgoraEvent>, p: AgoraProgress) {
        for (ev in events) {
            when (ev) {
                is AgoraEvent.NewAssembly -> AgoraNotifier.newAssembly(ev.assemblyKey, ev.question, ev.choices)
                is AgoraEvent.AssemblyEnded -> {
                    p.onAssemblyEnded(ev.question, ev.winner)
                    AgoraNotifier.assemblyEnded(ev.assemblyKey, ev.question, ev.winner)
                }
                is AgoraEvent.MyIdeaBridging -> {
                    p.onBridging()
                    _messages.tryEmit("Ton idée est devenue Rassembleuse !")
                    AgoraNotifier.myIdea("Ton idée est devenue Rassembleuse !", ev.text)
                }
                is AgoraEvent.MyIdeaClimbed -> if (ev.rank == 1) {
                    AgoraNotifier.myIdea("Ton idée est n°1 du mur !", null)
                }
                is AgoraEvent.RoundSealed -> {
                    p.onRoundSealed()
                    if (ev.myBetWon) p.onBetWon()
                    if (ev.iAmCrowned) p.onCrowned()
                    val text = when {
                        ev.iAmCrowned -> "Ton idée est gravée dans le Livre du cortège !"
                        ev.myBetWon -> "Pari Flair gagné : +${AgoraRules.BET_POINTS} points"
                        ev.entry != null -> "Manche terminée : « ${ev.entry.text} » est gravée"
                        else -> null // rien de gravé : pas de notification inutile
                    }
                    if (text != null) {
                        _messages.tryEmit(text)
                        AgoraNotifier.round(text)
                    }
                }
                is AgoraEvent.RoundEndingSoon -> AgoraNotifier.roundSoon()
                is AgoraEvent.DuelWon -> {
                    if (ev.iAmImprover) p.onDuelWon()
                    val text = if (ev.iAmImprover) "Ta version a gagné le duel : elle remplace l'ancienne !" else "Une version améliorée de ton idée a gagné : vous êtes co-auteurs"
                    _messages.tryEmit(text)
                    AgoraNotifier.myIdea(text, null)
                }
                is AgoraEvent.ChallengeDone -> {
                    p.onChallengeDone()
                    _messages.tryEmit("Défi du cortège réussi : ${ev.label} !")
                }
                is AgoraEvent.NewIdeasWaiting -> AgoraNotifier.newIdeas(ev.count)
            }
        }
    }

    // ------------------------------------------------- actions des joueurs

    /** Renvoie null si c'est bon, sinon la raison du refus (en français). */
    private fun act(block: (AgoraEngine) -> AgoraResult, onOk: (AgoraResult.Ok) -> Unit = {}): String? {
        val e = engine ?: return "Le réseau BitChat démarre, réessaie dans un instant."
        return when (val r = block(e)) {
            is AgoraResult.Ok -> {
                send(r.payload)
                onOk(r)
                refresh()
                null
            }
            is AgoraResult.Refused -> r.reason
        }
    }

    fun propose(kind: IdeaKind, text: String, parentKey: String? = null): String? =
        act({ it.propose(kind, text, parentKey) }) { progress?.onIdeaProposed() }

    fun vote(ideaKey: String, value: Int): String? =
        act({ it.vote(ideaKey, value) }) { progress?.onVoteCast() }

    fun createAssembly(question: String, choices: List<String>, durationMs: Long): String? =
        act({ it.createAssembly(question, choices, durationMs) })

    fun ballot(assemblyKey: String, choice: Int): String? =
        act({ it.ballot(assemblyKey, choice) })

    fun bet(ideaKey: String): String? = act({ it.bet(ideaKey) })

    fun clap(ideaKey: String) {
        engine?.clap(ideaKey)
        refresh()
    }

    /** Mode panique de BitChat : on oublie tout, badges compris. */
    fun panic() {
        engine?.clear()
        progress?.clear()
        relayedPending.set(0)
        refresh()
    }
}

/** Les badges et stats du jour, gardés seulement sur ce téléphone. */
class PrefsStore(context: Context) : AgoraStore {
    private val prefs = context.getSharedPreferences("agora_progress", Context.MODE_PRIVATE)
    override fun getLong(key: String, default: Long) = prefs.getLong(key, default)
    override fun putLong(key: String, value: Long) { prefs.edit().putLong(key, value).apply() }
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun putString(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun clear() { prefs.edit().clear().apply() }
}
