<div align="center">

<img src="assets/logo.png" alt="SkyHand" width="140">

# SHTitres

*Titres cosmétiques en dégradés animés, affichés dans le chat et au-dessus du joueur.*

<img src="https://img.shields.io/badge/Paper-26.3-f2a900?style=for-the-badge" alt="Paper 26.3"> <img src="https://img.shields.io/badge/Java-25-e76f00?style=for-the-badge" alt="Java 25"> <img src="https://img.shields.io/badge/SkyHand-Opale%20%C2%B7%20Lobby-3aa655?style=for-the-badge" alt="SkyHand Opale · Lobby">

</div>

---

## Présentation

Un titre s'affiche devant le pseudo, dans le chat et au-dessus du joueur. Utilisé sur : **Opale · Lobby**.

Chaque titre est décrit une seule fois dans `titles.yml` : texte en MiniMessage (dégradés, gras), bords (crochets ou symboles du thème), animation, icône du menu, lignes « comment l'obtenir ». Au chargement, le plugin calcule la version fixe du titre et toutes les images de son animation ; les placeholders ne font ensuite que lire l'image courante, depuis n'importe quel thread (les nametags sont résolus en asynchrone).

Trois animations, appliquées au texte (les bords restent fixes) :

| Type | Effet |
|---|---|
| `wave` | le dégradé défile en boucle d'un bord à l'autre |
| `shine` | un reflet traverse le texte, puis une pause |
| `pulse` | le texte s'éclaire puis revient, comme un battement |

Tous les joueurs qui portent le même titre sont synchronisés. Le chat et les menus montrent la version fixe, le nametag l'animation ; les titres animés le sont aussi dans le menu.

Un titre peut avoir une seconde version, choisie au clic droit dans le menu : bords à la couleur du joueur (`variant: adaptive`), ou autre apparence écrite dans `titles.yml` (couleur ou texte alternatif).

Posséder un titre, c'est avoir sa permission (`tag.<titre>`), donnée par les boxes, les envoys, la boutique ou les événements. Le titre choisi est enregistré en base (table `shtitres_players`) et suit le joueur d'un serveur à l'autre quand les serveurs partagent la base. Un joueur qui perd la permission n'affiche plus le titre ; son choix revient s'il la retrouve.

### Reprise des anciens titres

Les anciens menus posaient le titre en suffixe LuckPerms. Avec `migration.luckperms-suffix: true`, chaque suffixe qui correspond à un titre connu (table `luckperms-migration.yml` du jar, variante comprise) devient le titre du joueur au démarrage, sans écraser un titre déjà choisi, puis le suffixe est retiré de LuckPerms. Les suffixes inconnus restent et sont listés dans la console. À n'activer que sur un serveur.

## Dépendances

- Obligatoires : une base MariaDB ou MySQL
- Optionnelles : `PlaceholderAPI` (sans lui, les titres ne s'affichent nulle part), `LuckPerms` (reprise des anciens suffixes)

## Commandes

| Commande | Description | Permission |
|---|---|---|
| `/titres` | Ouvre le menu des titres. | `shtitres.use` |
| `/shtitres set <joueur> <titre> [variant]` | Pose un titre à un joueur. | `shtitres.admin.set` |
| `/shtitres clear <joueur>` | Retire le titre d'un joueur. | `shtitres.admin.clear` |
| `/shtitres info <joueur>` | Titre enregistré d'un joueur. | `shtitres.admin.info` |
| `/shtitres list` | Tous les titres, rendus dans le chat. | `shtitres.admin.list` |
| `/shtitres reload` | Recharge `config.yml`, `titles.yml` et `messages.yml`. Si un fichier est invalide, rien n'est remplacé. La base et la reprise demandent un redémarrage. | `shtitres.admin.reload` |

Alias de `/titres` : `/titre`, `/tags`, `/tag`, `/titres2`, `/titre_speciaux`, `/titre_speciaux2` (les anciens noms des menus ouvrent la même page qu'avant).

## Placeholders

| Placeholder | Valeur |
|---|---|
| `%shtitres_titre%` | titre du joueur, version fixe (chat, hover) |
| `%shtitres_titre_anime%` | titre du joueur, image courante de l'animation (nametag) |
| `%shtitres_titre_espace%`, `%shtitres_titre_anime_espace%` | les mêmes, suivis d'une espace s'il y a un titre |
| `%shtitres_id%`, `%shtitres_nom%` | identifiant et nom sans couleur du titre |
| `%shtitres_possedes%`, `%shtitres_total%` | titres possédés, titres existants |

Le texte rendu utilise les codes `&` et `&#RRGGBB`. Avec UnlimitedNametags, mettre `%shtitres_titre_anime%` à 2 ticks dans `performance.placeholderUpdateRates`.

## Configuration

Fichiers par défaut repris du serveur (secrets remplacés par `CHANGE_ME`). Cliquez sur un fichier pour l'ouvrir :

<pre>
plugins/SHTitres/
├── <a href="src/main/resources/config.yml">config.yml</a>
├── <a href="src/main/resources/titles.yml">titles.yml</a>
└── <a href="src/main/resources/messages.yml">messages.yml</a>
</pre>

## Compilation

Java 25 et Maven :

```bash
mvn clean package
```

Le jar est généré dans `target/`. `src/test/java/.../Export.java` rend tous les titres en JSON hors serveur (aperçu) et teste la table de reprise sur une liste de suffixes.

---

<div align="center">

<sub>Développé par <b>Cry_Legende</b> pour le serveur <b>SkyHand</b> · Tous droits réservés</sub>

</div>
