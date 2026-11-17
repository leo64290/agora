# AGORA — notice

AGORA est un jeu Android pour les manifs : on propose des idées, on vote, et les
idées qui **rassemblent** montent en haut. Tout passe par le Bluetooth des
téléphones (réseau BitChat) : **pas d'internet, pas de boîtier**.

Le concept complet est dans [AGORA-CONCEPT.md](AGORA-CONCEPT.md).

## Installer

1. Installe AGORA REVOLUTION depuis le Play Store (test interne), ou prends
   le fichier `app/build/outputs/apk/debug/app-universal-debug.apk`
   après avoir fabriqué l'appli.
2. Envoie-le sur le téléphone et ouvre-le. Android demande d'autoriser
   « l'installation d'applis inconnues » : accepte pour ce fichier.
3. Au premier lancement, accepte le Bluetooth, la localisation (Android
   l'exige pour le Bluetooth, le jeu n'utilise **aucune** position) et les
   notifications (pour le mode poche).

AGORA s'installe **à côté** de BitChat (ce n'est pas la même appli) : tu peux
garder les deux. Les deux se parlent : un téléphone BitChat normal fait suivre
les messages du jeu, et AGORA fait suivre les messages BitChat.

## Jouer

- **Mur** : propose une idée (bouton jaune), vote Pour / Contre / Passer.
  Bouton « Cartes » en haut : les idées une par une, glisse à droite ou à gauche.
- **Améliorer cette idée** : lance un duel. Si ta version fait mieux, elle
  remplace l'ancienne et vous devenez co-auteurs.
- **Manche** : compte à rebours de 30 minutes, pari Flair, défis du cortège,
  Livre du cortège.
- **Assemblée** : une question, un minuteur, résultat caché jusqu'à la fin.
- **Moi** : tes points, ton niveau, tes badges, le journal de fin, le mode
  poche, le radar.
- **Chat** (en haut à droite) : le chat BitChat normal. Le mode panique de
  BitChat (toucher 3 fois le titre du chat) efface aussi tout le jeu.

## Pour tester, il faut au moins 2 téléphones

Le jeu ne peut pas être essayé en vrai avec un seul téléphone : il faut au
moins 2 téléphones Android avec AGORA (ou 1 avec AGORA + 1 avec BitChat pour
voir le relais), à quelques mètres l'un de l'autre.

## Ce qui a été vérifié

- Le moteur du jeu (idées, votes, camps cachés, points, manches, paris,
  duels, assemblées) : **17 tests** sur PC, dont une simulation de
  30 téléphones avec 35 % de messages perdus → tout le monde voit le même mur.
- La fabrication de l'appli et les tests d'origine de BitChat.
- Pas encore vérifié : le vrai passage des messages entre deux vrais téléphones.

## Pour les développeurs

- Code du jeu : `app/src/main/java/com/bitchat/android/agora/`
  (moteur sans Android : `AgoraModels`, `AgoraCodec`, `AgoraOpinion`,
  `AgoraEngine`, `AgoraProgress` ; Android : `AgoraHub`, `AgoraNotifier` ;
  écrans : `ui/`).
- Tests : `app/src/test/java/com/bitchat/android/agora/AgoraEngineTest.kt`.
- Branchements dans BitChat (cherche « AGORA ») : `protocol/BinaryProtocol.kt`
  (type 0x41), `protocol/PacketPayloadLimits.kt`, `mesh/SecurityManager.kt`
  (signature obligatoire), `mesh/PacketProcessor.kt`,
  `mesh/BluetoothMeshService.kt` (envoi, réception, démarrage),
  `mesh/PacketRelayManager.kt` (compteur Relais), `ui/ChatViewModel.kt`
  (mode panique), `MainActivity.kt` (écran d'accueil).
- Fabriquer : JDK 17 (le projet a été passé de 21 à 17) et un dossier
  temporaire court, sinon Java échoue avec « Unable to establish loopback
  connection » :

```powershell
$env:JAVA_HOME = "<dossier de ton JDK 17>"
$env:TMP = "$PWD\.tmp"; $env:TEMP = $env:TMP
$env:JAVA_TOOL_OPTIONS = "-Djdk.net.unixdomain.tmpdir=$env:TMP -Djava.io.tmpdir=$env:TMP"
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest
```

## Licence

AGORA est construit sur bitchat-android, sous licence **GPL v3**. Si tu
distribues l'appli, tu dois aussi partager son code source.
