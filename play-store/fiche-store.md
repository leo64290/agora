# Fiche Google Play — AGORA REVOLUTION

Identifiant de l'appli (définitif, ne peut plus changer) : `com.agora.manif`
Langue par défaut : français (France) — fr-FR
Appli ou jeu : **Jeu** · Gratuit · Catégorie : **Jeux de société** (ou « Simulation » si refusé)

## Nom (30 caractères max)
AGORA REVOLUTION

## Description courte (80 caractères max)
Le jeu de la manif : propose, vote, et fais monter les idées qui rassemblent.

## Description complète (4000 caractères max)
AGORA REVOLUTION transforme une manifestation en grande assemblée. Chacun propose une idée, tout le monde vote, et les idées qui RASSEMBLENT montent en haut du mur — pas celles qui divisent.

Sans internet. Sans boîtier. Seulement les téléphones.
Les messages passent de téléphone en téléphone en Bluetooth, jusqu'à 7 sauts, grâce au réseau libre BitChat. Quand la 4G sature dans la foule, AGORA continue de marcher.

LE MUR D'IDÉES
• Propose un slogan, une revendication ou une action (140 caractères)
• Vote Pour, Contre ou Passer — ou en mode cartes : glisse à droite, glisse à gauche
• Les idées sont classées automatiquement selon l'accord du cortège
• Un vote compte 30 minutes, une idée sans vote s'efface : le mur suit le rythme de la manif
• Améliore l'idée de quelqu'un : si ta version fait mieux, elle la remplace et vous devenez co-auteurs

LES IDÉES QUI RASSEMBLENT
AGORA repère tout seul les gens qui votent souvent pareil, sans jamais afficher qui pense quoi. Une idée aimée par des gens qui d'habitude ne sont pas d'accord reçoit le badge « Rassembleuse » et un gros bonus. Le jeu récompense le terrain d'entente, pas la bagarre.

L'ASSEMBLÉE ÉCLAIR
Pose une question au cortège avec un minuteur (« On fait une pause place de la République ? »). Le résultat reste caché jusqu'à la fin pour que chacun vote librement, puis s'affiche chez tout le monde. On peut même répondre depuis la notification, téléphone dans la poche.

LE JEU
• Manches de 30 minutes : l'idée gagnante est gravée dans le Livre du cortège
• Pari Flair : devine la future n°1
• Défis du cortège à réussir tous ensemble
• Points, niveaux et 8 badges à collectionner
• Radar du cortège : regarde le réseau grandir
• Journal de fin de manif à partager

TA PROTECTION D'ABORD
• On note les idées, jamais les personnes
• Pseudo automatique (« Renard-42 »), aucun compte, aucun numéro
• Le jeu n'utilise aucune position GPS
• Idées et votes effacés après 30 minutes ; rien n'est envoyé à un serveur
• Mode panique : tout effacer d'un coup

AGORA fait aussi grandir le réseau BitChat de tout le monde : tant qu'il est allumé, il fait suivre les messages des autres téléphones BitChat, même de ceux qui ne jouent pas.

Logiciel libre (licence GPL v3), construit sur bitchat-android. Code source : https://github.com/leo64290/agora

## Coordonnées
Site web : https://leo64290.github.io/agora/
Politique de confidentialité : https://leo64290.github.io/agora/confidentialite.html
E-mail de contact : celui du compte développeur (Google l'exige ; à choisir par toi dans Play Console)

## Images
- Icône 512 × 512 : `icone-512.png`
- Image de présentation 1024 × 500 : `banniere-1024x500.png`
- Captures de téléphone : `captures/` (pas obligatoires pour le test interne)

---

# Réponses aux questionnaires de Play Console

## Accès à l'appli
Toutes les fonctions sont accessibles sans identifiant ni compte.

## Annonces (publicités)
Non, l'appli ne contient pas de publicité.

## Classification du contenu (questionnaire IARC)
- Catégorie : jeu (« Tous les autres types d'applis » si besoin)
- Violence, sexe, langage grossier, drogue : Non
- Jeux d'argent simulés : Non (le « pari Flair » se joue avec des points du jeu, sans argent ni lot)
- Les utilisateurs peuvent-ils interagir ou échanger du contenu ? **Oui** (idées, votes, chat BitChat)
- Partage de la position avec d'autres utilisateurs : **Oui** (seulement si l'utilisateur rejoint lui-même un salon de quartier BitChat ; le jeu, lui, n'utilise pas la position)
- Achats numériques : Non

## Public cible
18 ans et plus (le jeu est pensé pour les manifestations, avec des échanges entre inconnus).

## Sécurité des données
- Collecte de données : **Non** — rien n'est envoyé au développeur ni à un serveur du jeu. Les idées, votes et messages passent directement de téléphone en téléphone.
- Partage avec des tiers : **Non** (les échanges sont lancés par l'utilisateur lui-même, de téléphone à téléphone).
- Chiffrement en transit : messages privés BitChat chiffrés (protocole Noise) ; messages du jeu signés.
- Suppression : tout s'efface au bout de 30 minutes ou avec le mode panique.

## Applis d'actualités / gouvernementales / financières / santé
Non à tout.

## Autorisations à justifier (si Play Console le demande)
- Service au premier plan « connectedDevice » : garder le réseau Bluetooth BitChat actif pour relayer les messages quand l'appli est en arrière-plan (fonction principale : réseau maillé sans internet).
- Localisation (au premier plan seulement) : exigée par Android pour la recherche Bluetooth ; l'appli ne lit ni n'enregistre la position pour le jeu.
- Ignorer l'optimisation de la batterie : sans elle, Android coupe le réseau Bluetooth et les messages ne sont plus relayés.
