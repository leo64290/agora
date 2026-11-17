package com.bitchat.android.agora

/**
 * AGORA — modèles de données du jeu de manif.
 *
 * Tout ce fichier est du Kotlin pur (aucune dépendance Android) pour pouvoir
 * tester le moteur sur un PC avec des dizaines de faux joueurs.
 */
object AgoraRules {
    /** Durée de vie d'un vote, et d'une idée qui ne reçoit plus de vote. */
    const val WINDOW_MS = 30 * 60_000L

    /** Durée d'une manche (elles commencent à l'heure pile et à la demie). */
    const val ROUND_MS = 30 * 60_000L

    const val MAX_TEXT = 140
    const val MAX_CHOICE_TEXT = 60
    const val MAX_ACTIVE_IDEAS = 3
    const val MIN_IDEA_INTERVAL_MS = 60_000L

    const val MIN_ASSEMBLY_MS = 60_000L
    const val MAX_ASSEMBLY_MS = 15 * 60_000L
    const val MIN_CHOICES = 2
    const val MAX_CHOICES = 4

    /** Une date venue d'un autre téléphone peut avancer de 2 min au maximum. */
    const val FUTURE_TOLERANCE_MS = 2 * 60_000L

    /** Duel : la version 2 doit avoir au moins 5 votes et un meilleur accord. */
    const val DUEL_MIN_VOTES = 5
    const val DUEL_TIMEOUT_MS = 10 * 60_000L

    /** Pari Flair : un par manche, avant les 5 dernières minutes. */
    const val BET_CUTOFF_MS = 5 * 60_000L
    const val BET_POINTS = 30

    /** Camps : il faut au moins 5 votants ayant voté 2 fois ou plus. */
    const val MIN_CLUSTER_VOTERS = 5
    const val BRIDGE_SHARE = 0.6
    const val DIVIDE_LOW_SHARE = 0.4
    const val BRIDGE_MIN_VOTES_PER_GROUP = 2

    /** Une idée gagnante de manche doit avoir au moins 3 votes. */
    const val MIN_VOTES_TO_WIN_ROUND = 3

    /** Applaudissements : plafond par joueur et par idée. */
    const val MAX_CLAPS_PER_PLAYER = 600

    /** Points. */
    const val PARTICIPATION_PER_VOTE = 2
    const val PARTICIPATION_MAX_VOTES = 20
    const val PARTICIPATION_PER_BALLOT = 3
    const val OPENNESS_PER_VOTE = 5
    const val OPENNESS_MAX_VOTES = 10
    const val RELAY_MESSAGES_PER_POINT = 100

    /** Défis du cortège. */
    const val CHALLENGE_VOTES = 50
    const val CHALLENGE_VOTES_WINDOW_MS = 10 * 60_000L
    const val CHALLENGE_BRIDGES = 3
    const val CHALLENGE_PHONES = 100
}

enum class IdeaKind(val code: Char, val label: String) {
    SLOGAN('S', "Slogan"),
    DEMAND('R', "Revendication"),
    ACTION('A', "Action");

    companion object {
        fun fromCode(c: Char): IdeaKind? = values().firstOrNull { it.code == c }
    }
}

/** Une idée. [key] = "auteur:id", unique dans tout le réseau. */
data class Idea(
    val key: String,
    val authorId: String,
    val kind: IdeaKind,
    val text: String,
    val createdAt: Long,
    /** Si c'est une version améliorée : la clé de l'idée de départ. */
    val parentKey: String? = null
)

/** Un vote : +1 Pour, -1 Contre, 0 Passer. Le dernier vote d'une personne compte. */
data class Vote(val voterId: String, val ideaKey: String, val value: Int, val at: Long)

data class Assembly(
    val key: String,
    val authorId: String,
    val question: String,
    val choices: List<String>,
    val createdAt: Long,
    val endsAt: Long
)

data class Ballot(val voterId: String, val assemblyKey: String, val choice: Int, val at: Long)

/** Pari Flair sur l'idée qui sera n°1 à la fin de la manche [round]. */
data class Bet(val playerId: String, val round: Long, val ideaKey: String, val at: Long)

/** Total (cumulé) des applaudissements d'un joueur pour une idée. */
data class ClapReport(val playerId: String, val ideaKey: String, val total: Int, val at: Long)

/** Une ligne du Livre du cortège : l'idée gravée à la fin d'une manche. */
data class BookEntry(
    val round: Long,
    val ideaKey: String,
    val text: String,
    val authorIds: List<String>,
    val accord: Int,
    val sealedAt: Long
)

/** Ce que le moteur signale à l'appli (vibrations, badges…). */
sealed class AgoraEvent {
    data class NewAssembly(val assemblyKey: String, val question: String, val choices: List<String>) : AgoraEvent()
    data class AssemblyEnded(val assemblyKey: String, val question: String, val winner: String?) : AgoraEvent()
    data class MyIdeaBridging(val ideaKey: String, val text: String) : AgoraEvent()
    data class MyIdeaClimbed(val ideaKey: String, val rank: Int) : AgoraEvent()
    data class RoundSealed(val entry: BookEntry?, val myBetWon: Boolean, val iAmCrowned: Boolean) : AgoraEvent()
    data class RoundEndingSoon(val minutesLeft: Int) : AgoraEvent()
    data class DuelWon(val childKey: String, val iAmImprover: Boolean) : AgoraEvent()
    data class ChallengeDone(val challengeId: String, val label: String) : AgoraEvent()
    data class NewIdeasWaiting(val count: Int) : AgoraEvent()
}

/** Résultat d'une action locale : soit le message à envoyer, soit une raison de refus en français. */
sealed class AgoraResult {
    data class Ok(val payload: ByteArray, val key: String? = null) : AgoraResult()
    data class Refused(val reason: String) : AgoraResult()
}

// ---------- Vues pour l'écran ----------

data class IdeaView(
    val key: String,
    val kind: IdeaKind,
    val text: String,
    val authorIds: List<String>,
    val pour: Int,
    val contre: Int,
    val passe: Int,
    /** 0..100 */
    val accord: Int,
    val bridging: Boolean,
    val divisive: Boolean,
    val minutesLeft: Int,
    /** 0..1 : part de vie restante. */
    val life: Float,
    val myVote: Int?,
    val isMine: Boolean,
    val claps: Int,
    val rank: Int,
    /** Texte court sur le duel, ou null. */
    val duelNote: String?,
    val consensus: Double,
    val createdAt: Long
)

data class AssemblyView(
    val key: String,
    val authorId: String,
    val question: String,
    val choices: List<String>,
    val secondsLeft: Long,
    val ended: Boolean,
    val voters: Int,
    val myChoice: Int?,
    /** Résultats (seulement quand c'est fini), en pourcentage. */
    val results: List<Int>?,
    val winner: Int?,
    val isMine: Boolean
)

data class ChallengeView(val id: String, val label: String, val current: Int, val target: Int) {
    val done: Boolean get() = current >= target
}

data class RoundView(
    val round: Long,
    val secondsLeft: Long,
    /** 0..1 */
    val progress: Float,
    val betOpen: Boolean,
    val myBet: String?,
    val betOptions: List<IdeaView>,
    val book: List<BookEntry>,
    val challenges: List<ChallengeView>
)

data class ScoreView(
    val playerId: String,
    val rassembleur: Int,
    val participation: Int,
    val ouverture: Int,
    val flair: Int
) {
    val total: Int get() = rassembleur + participation + ouverture + flair
}
