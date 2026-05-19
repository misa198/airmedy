const translations = {
    en: {
        nav: {
            features: "Features",
            screenshots: "Screenshots",
            faq: "FAQ",
            download: "Download"
        },
        hero: {
            title: "Your music, <br><span class=\"text-primary\">refined.</span>",
            subtitle: "All-in-one offline music player. High-performance, battery-efficient, and beautifully designed with glass-morphism.",
            cta: "Get Airmedy",
            github: "View on GitHub"
        },
        features: {
            title: "Everything you need",
            library: {
                title: "Your whole library",
                desc: "Add any folder and Airmedy scans it instantly, even with tens of thousands of tracks."
            },
            lyrics: {
                title: "Lyrics that follow",
                desc: "Synced lyrics scroll line-by-line as the song plays. Immersive listening at its best."
            },
            eq: {
                title: "10-band Equalizer",
                desc: "Tune the sound to your headphones. Native performance on macOS, Windows, and Linux."
            },
            search: {
                title: "Fast Search",
                desc: "Find any track, album, or artist in milliseconds with our lightning-fast indexing."
            },
            playlists: {
                title: "Playlists",
                desc: "Create and manage playlists, import and export them, and browse by genre or artist."
            },
            media: {
                title: "Media Keys & Tray",
                desc: "Control playback via keyboard, lock screen, or the system tray menu."
            },
            native: {
                title: "Native Performance",
                desc: "No Electron. No bloat. Lightweight experience."
            },
            lastfm: {
                title: "Last.fm Scrobbling",
                desc: "Sync your listening history and loved tracks automatically with Last.fm."
            },
            player: {
                title: "Mini & Fullscreen",
                desc: "Switch between a beautiful immersive fullscreen mode and a compact miniplayer that stays out of your way."
            }
        },
        screenshots: {
            title: "Designed for Focus",
            library: {
                title: "Library Explorer",
                desc: "Beautiful glass-morphic interface that highlights your album art and metadata."
            },
            lyrics: {
                title: "Synced Lyrics",
                desc: "Immersive fullscreen mode with lyrics that move with the music."
            },
            mini: { title: "Compact Player", desc: "A tiny, versatile mini-player that stays on top and out of your way." },
            artist: { title: "Artist Insights", desc: "Deep dive into your favorite artists with discography and bios." }
        },
        faq: {
            title: "Frequently Asked Questions",
            formats: {
                q: "What audio formats are supported?",
                a: "Airmedy supports a wide range of formats including MP3, AAC, M4A, FLAC, WAV, Ogg Vorbis, Opus, APE, and even DSD. We use native engines like SFBAudioEngine on macOS and FFmpeg-backed decoding on Windows and Linux."
            },
            free: {
                q: "Is Airmedy free?",
                a: "Yes! Airmedy is completely free and open-source under the MIT License. You can find the source code on GitHub."
            },
            ffmpeg: {
                q: "Do I need to install FFmpeg?",
                a: "No, the FFmpeg libraries are statically compiled and bundled inside Airmedy. You don't need to install anything else."
            },
            privacy: {
                q: "How is my data handled?",
                a: "Airmedy is built with privacy in mind. Your Last.fm session keys are stored securely in your system's native keychain (macOS Keychain, Windows Credential Manager, or Secret Service API on Linux). We never see your password, and no data ever leaves your machine except to scrobble tracks to Last.fm."
            }
        },
        download: {
            title: "Ready to listen?",
            subtitle: "Download Airmedy for your platform and start your journey.",
            windows: {
                label: "Windows",
                btn: "Coming soon"
            },
            linux: {
                label: "Linux",
                btn: "Coming soon"
            },
            macos: {
                label: "macOS",
                btn: "Download for macOS",
                silicon: "Apple Silicon",
                intel: "Intel Chip"
            },
            license: "Released under the"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "Report an issue"
        }
    },
    de: {
        nav: {
            features: "Funktionen",
            screenshots: "Screenshots",
            faq: "FAQ",
            download: "Download"
        },
        hero: {
            title: "Deine Musik, <br><span class=\"text-primary\">verfeinert.</span>",
            subtitle: "All-in-One-Offline-Musikplayer. Leistungsstark, batterieeffizient und wunderschön im Glass-Morphism-Design gestaltet.",
            cta: "Airmedy herunterladen",
            github: "Auf GitHub ansehen"
        },
        features: {
            title: "Alles, was du brauchst",
            library: {
                title: "Deine gesamte Bibliothek",
                desc: "Füge einen beliebigen Ordner hinzu und Airmedy scannt ihn sofort, selbst bei zehntausenden von Titeln."
            },
            lyrics: {
                title: "Mitlaufende Songtexte",
                desc: "Synchronisierte Songtexte scrollen Zeile für Zeile während der Wiedergabe. Immersives Hören vom Feinsten."
            },
            eq: {
                title: "10-Band-Equalizer",
                desc: "Stimme den Klang auf deine Kopfhörer ab. Native Leistung unter macOS, Windows und Linux."
            },
            search: {
                title: "Schnellsuche",
                desc: "Finde jeden Titel, jedes Album oder jeden Künstler in Millisekunden mit unserer blitzschnellen Indizierung."
            },
            playlists: {
                title: "Playlists",
                desc: "Erstelle und verwalte Playlists, importiere und exportiere sie und durchsuche sie nach Genre oder Künstler."
            },
            media: {
                title: "Medientasten & Tray",
                desc: "Steuere die Wiedergabe über die Tastatur, den Sperrbildschirm oder das System-Tray-Menü."
            },
            native: {
                title: "Native Leistung",
                desc: "Kein Electron. Kein Ballast. Leichtgewichtiges Erlebnis."
            },
            lastfm: {
                title: "Last.fm Scrobbling",
                desc: "Synchronisiere deinen Wiedergabeverlauf und deine Lieblingstitel automatisch mit Last.fm."
            },
            player: {
                title: "Mini & Vollbild",
                desc: "Wechsle zwischen einem wunderschönen, immersiven Vollbildmodus und einem kompakten Miniplayer."
            }
        },
        screenshots: {
            title: "Entwickelt für Fokus",
            library: {
                title: "Bibliotheks-Explorer",
                desc: "Wunderschöne Glass-Morphism-Oberfläche, die deine Albumcover und Metadaten hervorhebt."
            },
            lyrics: {
                title: "Synchronisierte Texte",
                desc: "Immersiver Vollbildmodus mit Texten, die sich mit der Musik bewegen."
            },
            mini: { title: "Kompakter Player", desc: "Ein winziger, vielseitiger Mini-Player, der im Vordergrund und aus dem Weg bleibt." },
            artist: { title: "Künstler-Einblicke", desc: "Tauche tief in deine Lieblingskünstler mit Diskografie und Biografien ein." }
        },
        faq: {
            title: "Häufig gestellte Fragen",
            formats: {
                q: "Welche Audioformate werden unterstützt?",
                a: "Airmedy unterstützt eine Vielzahl von Formaten, darunter MP3, AAC, M4A, FLAC, WAV, Ogg Vorbis, Opus, APE und sogar DSD. Wir verwenden native Engines unter macOS und FFmpeg-basierte Dekodierung unter Windows und Linux."
            },
            free: {
                q: "Ist Airmedy kostenlos?",
                a: "Ja! Airmedy ist völlig kostenlos und quelloffen unter der MIT-Lizenz. Den Quellcode findest du auf GitHub."
            },
            ffmpeg: {
                q: "Muss ich FFmpeg installieren?",
                a: "Nein, die FFmpeg-Bibliotheken sind statisch kompiliert und in Airmedy integriert. Du musst nichts weiter installieren."
            },
            privacy: {
                q: "Wie werden meine Daten behandelt?",
                a: "Airmedy wurde mit Fokus auf Datenschutz entwickelt. Deine Last.fm-Sitzungsschlüssel werden sicher im nativen Schlüsselbund deines Systems gespeichert (macOS Keychain, Windows Credential Manager oder Secret Service API unter Linux). Wir sehen dein Passwort nie, und außer zum Scrobbeln von Titeln an Last.fm verlassen keine Daten deinen Computer."
            }
        },
        download: {
            title: "Bereit zum Zuhören?",
            subtitle: "Lade Airmedy für deine Plattform herunter und beginne deine Reise.",
            windows: {
                label: "Windows",
                btn: "Demnächst"
            },
            linux: {
                label: "Linux",
                btn: "Demnächst"
            },
            macos: {
                label: "macOS",
                btn: "Für macOS herunterladen",
                silicon: "Apple Silicon",
                intel: "Intel-Chip"
            },
            license: "Veröffentlicht unter der"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "Ein Problem melden"
        }
    },
    es: {
        nav: {
            features: "Funciones",
            screenshots: "Capturas",
            faq: "Preguntas",
            download: "Descargar"
        },
        hero: {
            title: "Tu música, <br><span class=\"text-primary\">refinada.</span>",
            subtitle: "Reproductor de música offline todo en uno. Alto rendimiento, eficiente en batería y bellamente diseñado con glass-morphism.",
            cta: "Obtener Airmedy",
            github: "Ver en GitHub"
        },
        features: {
            title: "Todo lo que necesitas",
            library: {
                title: "Toda tu biblioteca",
                desc: "Añade cualquier carpeta y Airmedy la escanea al instante, incluso con decenas de miles de pistas."
            },
            lyrics: {
                title: "Letras que siguen el ritmo",
                desc: "Letras sincronizadas que se desplazan línea por línea mientras suena la canción. Escucha inmersiva en su máxima expresión."
            },
            eq: {
                title: "Ecualizador de 10 bandas",
                desc: "Ajusta el sonido a tus auriculares. Rendimiento nativo en macOS, Windows y Linux."
            },
            search: {
                title: "Búsqueda rápida",
                desc: "Encuentra cualquier pista, álbum o artista en milisegundos con nuestra indexación ultrarrápida."
            },
            playlists: {
                title: "Listas de reproducción",
                desc: "Crea y gestiona listas de reproducción, impórtalas y expórtalas, y navega por género o artista."
            },
            media: {
                title: "Teclas de medios y bandeja",
                desc: "Controla la reproducción mediante el teclado, la pantalla de bloqueo o el menú de la bandeja del sistema."
            },
            native: {
                title: "Rendimiento nativo",
                desc: "Sin Electron. Sin sobrecarga. Experiencia ligera."
            },
            lastfm: {
                title: "Scrobbling de Last.fm",
                desc: "Sincroniza tu historial de escucha y canciones favoritas automáticamente con Last.fm."
            },
            player: {
                title: "Mini y Pantalla completa",
                desc: "Cambia entre un hermoso modo de pantalla completa inmersivo y un mini reproductor compacto."
            }
        },
        screenshots: {
            title: "Diseñado para la concentración",
            library: {
                title: "Explorador de biblioteca",
                desc: "Hermosa interfaz glass-morphic que resalta las portadas de tus álbumes y metadatos."
            },
            lyrics: {
                title: "Letras sincronizadas",
                desc: "Modo de pantalla completa inmersivo con letras que se mueven con la música."
            },
            mini: { title: "Reproductor Compacto", desc: "Un mini reproductor diminuto y versátil que se mantiene en primer plano y no estorba." },
            artist: { title: "Información del Artista", desc: "Sumérgete en tus artistas favoritos con discografía y biografías." }
        },
        faq: {
            title: "Preguntas frecuentes",
            formats: {
                q: "¿Qué formatos de audio son compatibles?",
                a: "Airmedy admite una amplia gama de formatos, incluidos MP3, AAC, M4A, FLAC, WAV, Ogg Vorbis, Opus, APE e incluso DSD. Utilizamos motores nativos en macOS y decodificación basada en FFmpeg en Windows y Linux."
            },
            free: {
                q: "¿Es Airmedy gratuito?",
                a: "¡Sí! Airmedy es completamente gratuito y de código abierto bajo la Licencia MIT. Puedes encontrar el código fuente en GitHub."
            },
            ffmpeg: {
                q: "¿Necesito instalar FFmpeg?",
                a: "No, las bibliotecas FFmpeg están compiladas estáticamente e integradas en Airmedy. No necesitas instalar nada más."
            },
            privacy: {
                q: "¿Cómo se manejan mis datos?",
                a: "Airmedy está construido pensando en la privacidad. Tus claves de sesión de Last.fm se almacenan de forma segura en el llavero nativo de tu sistema (macOS Keychain, Windows Credential Manager o Secret Service API en Linux). Nunca vemos tu contraseña y ningún dato sale de tu máquina, excepto para hacer scrobbling de las pistas a Last.fm."
            }
        },
        download: {
            title: "¿Listo para escuchar?",
            subtitle: "Descarga Airmedy para tu plataforma y comienza tu viaje.",
            windows: {
                label: "Windows",
                btn: "Próximamente"
            },
            linux: {
                label: "Linux",
                btn: "Próximamente"
            },
            macos: {
                label: "macOS",
                btn: "Descargar para macOS",
                silicon: "Apple Silicon",
                intel: "Chip Intel"
            },
            license: "Lanzado bajo la"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "Informar de un problema"
        }
    },
    fr: {
        nav: {
            features: "Fonctions",
            screenshots: "Captures",
            faq: "FAQ",
            download: "Télécharger"
        },
        hero: {
            title: "Votre musique, <br><span class=\"text-primary\">raffinée.</span>",
            subtitle: "Lecteur de musique hors ligne tout-en-un. Haute performance, économe en batterie et magnifiquement conçu avec le glass-morphism.",
            cta: "Obtenir Airmedy",
            github: "Voir sur GitHub"
        },
        features: {
            title: "Tout ce dont vous avez besoin",
            library: {
                title: "Toute votre bibliothèque",
                desc: "Ajoutez n'importe quel dossier et Airmedy le scanne instantanément, même avec des dizaines de milliers de titres."
            },
            lyrics: {
                title: "Paroles qui suivent",
                desc: "Les paroles synchronisées défilent ligne par ligne pendant la lecture. L'écoute immersive à son meilleur."
            },
            eq: {
                title: "Égaliseur 10 bandes",
                desc: "Ajustez le son à vos écouteurs. Performance native sur macOS, Windows et Linux."
            },
            search: {
                title: "Recherche rapide",
                desc: "Trouvez n'importe quel titre, album ou artiste en quelques millisecondes grâce à notre indexation ultra-rapide."
            },
            playlists: {
                title: "Playlists",
                desc: "Créez et gérez des playlists, importez-les et exportez-les, et parcourez par genre ou par artiste."
            },
            media: {
                title: "Touches média & plateau",
                desc: "Contrôlez la lecture via le clavier, l'écran de verrouillage ou le menu du plateau système."
            },
            native: {
                title: "Performance native",
                desc: "Pas d'Electron. Pas de superflu. Expérience légère."
            },
            lastfm: {
                title: "Scrobbling Last.fm",
                desc: "Synchronisez automatiquement votre historique d'écoute et vos titres préférés avec Last.fm."
            },
            player: {
                title: "Mini & Plein écran",
                desc: "Basculez entre un magnifique mode plein écran immersif et un mini-lecteur compact."
            }
        },
        screenshots: {
            title: "Conçu pour la concentration",
            library: {
                title: "Explorateur de bibliothèque",
                desc: "Magnifique interface glass-morphic qui met en valeur vos pochettes d'albums et vos métadonnées."
            },
            lyrics: {
                title: "Paroles synchronisées",
                desc: "Mode plein écran immersif avec des paroles qui bougent avec la musique."
            },
            mini: { title: "Lecteur Compact", desc: "Un mini-lecteur minuscule et polyvalent qui reste au premier plan sans vous gêner." },
            artist: { title: "Aperçu de l'Artiste", desc: "Plongez dans vos artistes préférés avec leur discographie et leurs biographies." }
        },
        faq: {
            title: "Foire aux questions",
            formats: {
                q: "Quels sont les formats audio supportés ?",
                a: "Airmedy supporte une large gamme de formats, notamment MP3, AAC, M4A, FLAC, WAV, Ogg Vorbis, Opus, APE et même DSD. Nous utilisons des moteurs natifs sur macOS et le décodage basé sur FFmpeg sur Windows et Linux."
            },
            free: {
                q: "Airmedy est-il gratuit ?",
                a: "Oui ! Airmedy est complètement gratuit et open-source sous licence MIT. Vous pouvez trouver le code source sur GitHub."
            },
            ffmpeg: {
                q: "Dois-je installer FFmpeg ?",
                a: "No, les bibliothèques FFmpeg sont compilées statiquement et intégrées à Airmedy. Vous n'avez rien d'autre à installer."
            },
            privacy: {
                q: "Comment mes données sont-elles traitées ?",
                a: "Airmedy est conçu avec le respect de la vie privée à l'esprit. Vos clés de session Last.fm sont stockées en toute sécurité dans le trousseau natif de votre système (macOS Keychain, Windows Credential Manager ou Secret Service API sur Linux). Nous ne voyons jamais votre mot de passe et aucune donnée ne quitte votre machine, sauf pour scrobbler des pistes sur Last.fm."
            }
        },
        download: {
            title: "Prêt à écouter ?",
            subtitle: "Téléchargez Airmedy pour votre plateforme et commencez votre voyage.",
            windows: {
                label: "Windows",
                btn: "Bientôt disponible"
            },
            linux: {
                label: "Linux",
                btn: "Bientôt disponible"
            },
            macos: {
                label: "macOS",
                btn: "Télécharger pour macOS",
                silicon: "Apple Silicon",
                intel: "Puce Intel"
            },
            license: "Publié sous la"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "Signaler un problème"
        }
    },
    it: {
        nav: {
            features: "Funzioni",
            screenshots: "Screenshot",
            faq: "FAQ",
            download: "Scarica"
        },
        hero: {
            title: "La tua musica, <br><span class=\"text-primary\">raffinata.</span>",
            subtitle: "Lettore musicale offline tutto in uno. Alte prestazioni, efficienza della batteria e splendido design glass-morphism.",
            cta: "Ottieni Airmedy",
            github: "Vedi su GitHub"
        },
        features: {
            title: "Tutto ciò di cui hai bisogno",
            library: {
                title: "Tutta la tua libreria",
                desc: "Aggiungi qualsiasi cartella e Airmedy la scansiona istantaneamente, anche con decine di migliaia di brani."
            },
            lyrics: {
                title: "Testi che seguono",
                desc: "I testi sincronizzati scorrono riga per riga mentre il brano è in riproduzione. L'ascolto immersivo al suo meglio."
            },
            eq: {
                title: "Equalizzatore a 10 bande",
                desc: "Sintonizza il suono per le tue cuffie. Prestazioni native su macOS, Windows e Linux."
            },
            search: {
                title: "Ricerca rapida",
                desc: "Trova qualsiasi brano, album o artista in millisecondi con la nostra indicizzazione fulminea."
            },
            playlists: {
                title: "Playlist",
                desc: "Crea e gestisci playlist, importale ed esportale, e naviga per genere o artista."
            },
            media: {
                title: "Tasti multimediali e tray",
                desc: "Controlla la riproduzione tramite tastiera, schermata di blocco o menu della tray di sistema."
            },
            native: {
                title: "Prestazioni native",
                desc: "Niente Electron. Niente bloatware. Esperienza leggera."
            },
            lastfm: {
                title: "Scrobbling Last.fm",
                desc: "Sincronizza automaticamente la tua cronologia di ascolto e i brani preferiti con Last.fm."
            },
            player: {
                title: "Mini e Schermo intero",
                desc: "Passa da una splendida modalità a schermo intero immersiva a un mini lettore compatto."
            }
        },
        screenshots: {
            title: "Progettato per la concentrazione",
            library: {
                title: "Esplora libreria",
                desc: "Splendida interfaccia glass-morphic che mette in risalto le copertine degli album e i metadati."
            },
            lyrics: {
                title: "Testi sincronizzati",
                desc: "Modalità a schermo intero immersiva con testi che si muovono con la musica."
            },
            mini: { title: "Lettore Compatto", desc: "Un mini lettore minuscolo e versatile che rimane in primo piano senza intralciare." },
            artist: { title: "Approfondimenti sull'Artista", desc: "Immergiti nei tuoi artisti preferiti con discografia e biografie." }
        },
        faq: {
            title: "Domande frequenti",
            formats: {
                q: "Quali formati audio sono supportati?",
                a: "Airmedy supporta una vasta gamma di formati tra cui MP3, AAC, M4A, FLAC, WAV, Ogg Vorbis, Opus, APE e persino DSD. Utilizziamo motori nativi su macOS e decodifica basata su FFmpeg su Windows e Linux."
            },
            free: {
                q: "Airmedy è gratuito?",
                a: "Sì! Airmedy è completamente gratuito e open source sotto licenza MIT. Puoi trovare il codice sorgente su GitHub."
            },
            ffmpeg: {
                q: "Devo installare FFmpeg?",
                a: "No, le librerie FFmpeg sono compilate staticamente e incluse in Airmedy. Non è necessario installare altro."
            },
            privacy: {
                q: "Come vengono gestiti i miei dati?",
                a: "Airmedy è costruito pensando alla privacy. Le tue chiavi di sessione di Last.fm sono archiviate in modo sicuro nel portachiavi nativo del tuo sistema (macOS Keychain, Windows Credential Manager o Secret Service API su Linux). Non vediamo mai la tua password e nessun dato lascia mai la tua macchina se non per fare scrobbling delle tracce su Last.fm."
            }
        },
        download: {
            title: "Pronto ad ascoltare?",
            subtitle: "Scarica Airmedy per la tua piattaforma e inizia il tuo viaggio.",
            windows: {
                label: "Windows",
                btn: "In arrivo"
            },
            linux: {
                label: "Linux",
                btn: "In arrivo"
            },
            macos: {
                label: "macOS",
                btn: "Scarica per macOS",
                silicon: "Apple Silicon",
                intel: "Chip Intel"
            },
            license: "Rilasciato sotto la"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "Segnala un problema"
        }
    },
    ja: {
        nav: {
            features: "機能",
            screenshots: "スクリーンショット",
            faq: "よくある質問",
            download: "ダウンロード"
        },
        hero: {
            title: "あなたの音楽を、<br><span class=\"text-primary\">洗練されたものに。</span>",
            subtitle: "オールインワンのオフライン音楽プレイヤー。高性能、省電力、そしてグラスモーフィズムによる美しいデザイン。",
            cta: "Airmedyを入手",
            github: "GitHubで表示"
        },
        features: {
            title: "必要なものすべて",
            library: {
                title: "ライブラリ全体",
                desc: "任意のフォルダを追加すると、数万曲あってもAirmedyが瞬時にスキャンします。"
            },
            lyrics: {
                title: "流れる歌詞",
                desc: "曲の再生に合わせて同期された歌詞が一行ずつスクロールします。最高の没入型リスニング体験。"
            },
            eq: {
                title: "10バンドイコライザー",
                desc: "ヘッドフォンに合わせてサウンドを調整。macOS、Windows、Linuxでネイティブなパフォーマンスを実現。"
            },
            search: {
                title: "高速検索",
                desc: "超高速なインデックス作成により、曲、アルバム、アーティストを数ミリ秒で見つけることができます。"
            },
            playlists: {
                title: "プレイリスト",
                desc: "プレイリストの作成と管理、インポートとエクスポート、ジャンルやアーティスト別の閲覧が可能です。"
            },
            media: {
                title: "メディアキーとトレイ",
                desc: "キーボード、ロック画面、またはシステムトレイメニューから再生を制御できます。"
            },
            native: {
                title: "ネイティブパフォーマンス",
                desc: "Electron不使用。無駄を削ぎ落とし、軽量な体験。"
            },
            lastfm: {
                title: "Last.fmスクロブリング",
                desc: "再生履歴とお気に入りのトラックをLast.fmと自動的に同期します。"
            },
            player: {
                title: "ミニ＆フルスクリーン",
                desc: "美しい没入型のフルスクリーンモードと、邪魔にならないコンパクトなミニプレイヤーを切り替えられます。"
            }
        },
        screenshots: {
            title: "集中するためのデザイン",
            library: {
                title: "ライブラリエクスプローラー",
                desc: "アルバムアートとメタデータを引き立てる美しいグラスモーフィズムインターフェース。"
            },
            lyrics: {
                title: "同期された歌詞",
                desc: "音楽に合わせて歌詞が動く没入型のフルスクリーンモード。"
            },
            mini: { title: "コンパクトプレイヤー", desc: "邪魔にならずに常に手前に表示される、小型で多機能なミニプレイヤー。" },
            artist: { title: "アーティストインサイト", desc: "ディスコグラフィーやバイオグラフィーで、お気に入りのアーティストを深く掘り下げます。" }
        },
        faq: {
            title: "よくある質問",
            formats: {
                q: "どのオーディオ形式がサポートされていますか？",
                a: "MP3、AAC、M4A、FLAC、WAV、Ogg Vorbis、Opus、APE、さらにDSDを含む幅広い形式をサポートしています。macOSではネイティブエンジン、WindowsとLinuxではFFmpegベースのデコードを使用します。"
            },
            free: {
                q: "Airmedyは無料ですか？",
                a: "はい！Airmedyは完全に無料で、MITライセンスの下でオープンソースとして公開されています。ソースコードはGitHubで確認できます。"
            },
            ffmpeg: {
                q: "FFmpegをインストールする必要がありますか？",
                a: "いいえ、FFmpegライブラリは静的にコンパイルされ、Airmedyに同梱されています。他に何もインストールする必要はありません。"
            },
            privacy: {
                q: "データはどのように扱われますか？",
                a: "Airmedyはプライバシーを念頭に置いて構築されています。Last.fmのセッションキーは、システムのネイティブキーチェーン（macOS Keychain、Windows Credential Manager、またはLinuxのSecret Service API）に安全に保存されます。私たちがあなたのパスワードを見ることは決してなく、トラックをLast.fmにスクロブルする以外にデータがあなたのマシンを離れることはありません。"
            }
        },
        download: {
            title: "聴く準備はできましたか？",
            subtitle: "プラットフォームに合わせてAirmedyをダウンロードし、旅を始めましょう。",
            windows: {
                label: "Windows",
                btn: "近日公開"
            },
            linux: {
                label: "Linux",
                btn: "近日公開"
            },
            macos: {
                label: "macOS",
                btn: "macOS版をダウンロード",
                silicon: "Appleシリコン",
                intel: "Intelチップ"
            },
            license: "ライセンス:"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "問題を報告する"
        }
    },
    ko: {
        nav: {
            features: "기능",
            screenshots: "스크린샷",
            faq: "자주 묻는 질문",
            download: "다운로드"
        },
        hero: {
            title: "당신의 음악을 <br><span class=\"text-primary\">더 세련되게.</span>",
            subtitle: "올인원 오프라인 음악 플레이어. 고성능, 배터리 효율성, 그리고 글래스모피즘 기반의 아름다운 디자인.",
            cta: "Airmedy 다운로드",
            github: "GitHub에서 보기"
        },
        features: {
            title: "당신에게 필요한 모든 것",
            library: {
                title: "전체 라이브러리",
                desc: "어떤 폴더든 추가하면 수만 곡이 있더라도 Airmedy가 즉시 스캔합니다."
            },
            lyrics: {
                title: "가사 따라보기",
                desc: "음악이 재생되는 동안 동기화된 가사가 한 줄씩 스크롤됩니다. 최고의 몰입형 감상 경험."
            },
            eq: {
                title: "10밴드 이퀄라이저",
                desc: "헤드폰에 맞게 사운드를 조정하세요. macOS, Windows, Linux에서 네이티브 성능을 제공합니다."
            },
            search: {
                title: "빠른 검색",
                desc: "초고속 인덱싱으로 단 몇 밀리초 만에 모든 트랙, 앨범, 아티스트를 찾을 수 있습니다."
            },
            playlists: {
                title: "플레이리스트",
                desc: "플레이리스트를 생성 및 관리하고, 가져오기/내보내기 및 장르/아티스트별 탐색이 가능합니다."
            },
            media: {
                title: "미디어 키 및 트레이",
                desc: "키보드, 잠금 화면 또는 시스템 트레이 메뉴에서 재생을 제어하세요."
            },
            native: {
                title: "네이티브 성능",
                desc: "Electron 없음. 불필요한 요소 제거. 가벼운 경험."
            },
            lastfm: {
                title: "Last.fm 스크로블링",
                desc: "감상 기록과 즐겨찾는 트랙을 Last.fm과 자동으로 동기화하세요."
            },
            player: {
                title: "미니 및 전체 화면",
                desc: "아름다운 몰입형 전체 화면 모드와邪魔되지 않는 컴팩트한 미니 플레이어를 전환하세요."
            }
        },
        screenshots: {
            title: "집중을 위한 디자인",
            library: {
                title: "라이브러리 탐색기",
                desc: "앨범 아트와 메타데이터를 돋보이게 하는 아름다운 글래스모피즘 인터페이스."
            },
            lyrics: {
                title: "동기화된 가사",
                desc: "음악과 함께 움직이는 가사가 있는 몰입형 전체 화면 모드."
            },
            mini: { title: "컴팩트 플레이어", desc: "항상 위로 유지되면서 방해가 되지 않는 작고 다재다능한 미니 플레이어." },
            artist: { title: "아티스트 인사이트", desc: "디스코그래피와 약력으로 좋아하는 아티스트에 대해 깊이 알아보세요." }
        },
        faq: {
            title: "자주 묻는 질문",
            formats: {
                q: "어떤 오디오 형식이 지원되나요?",
                a: "MP3, AAC, M4A, FLAC, WAV, Ogg Vorbis, Opus, APE 및 DSD를 포함한 광범위한 형식을 지원합니다. macOS에서는 네이티브 엔진을, Windows와 Linux에서는 FFmpeg 기반 디코딩을 사용합니다."
            },
            free: {
                q: "Airmedy는 무료인가요?",
                a: "네! Airmedy는 MIT 라이선스 하에 완전 무료이며 오픈 소스입니다. 소스 코드는 GitHub에서 찾을 수 있습니다."
            },
            ffmpeg: {
                q: "FFmpeg를 설치해야 하나요?",
                a: "아니요, FFmpeg 라이브러리는 정적으로 컴파일되어 Airmedy에 포함되어 있습니다. 다른 것을 설치할 필요가 없습니다."
            },
            privacy: {
                q: "내 데이터는 어떻게 처리되나요?",
                a: "Airmedy는 개인정보 보호를 염두에 두고 제작되었습니다. Last.fm 세션 키는 시스템의 네이티브 키체인(macOS 키체인, Windows 자격 증명 관리자 또는 Linux의 Secret Service API)에 안전하게 저장됩니다. 저희는 귀하의 비밀번호를 절대 볼 수 없으며, 트랙을 Last.fm으로 스크로블링하는 것 외에는 어떠한 데이터도 귀하의 기기를 벗어나지 않습니다."
            }
        },
        download: {
            title: "음악을 들을 준비가 되셨나요?",
            subtitle: "사용 중인 플랫폼에 맞는 Airmedy를 다운로드하고 시작하세요.",
            windows: {
                label: "Windows",
                btn: "출시 예정"
            },
            linux: {
                label: "Linux",
                btn: "출시 예정"
            },
            macos: {
                label: "macOS",
                btn: "macOS용 다운로드",
                silicon: "Apple 실리콘",
                intel: "Intel 칩"
            },
            license: "라이선스:"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "문제 보고"
        }
    },
    pt: {
        nav: {
            features: "Recursos",
            screenshots: "Capturas",
            faq: "FAQ",
            download: "Baixar"
        },
        hero: {
            title: "Sua música, <br><span class=\"text-primary\">refinada.</span>",
            subtitle: "Reprodutor de música offline tudo-em-um. Alto desempenho, economia de bateria e design elegante com glass-morphism.",
            cta: "Obter Airmedy",
            github: "Ver no GitHub"
        },
        features: {
            title: "Tudo o que você precisa",
            library: {
                title: "Toda a sua biblioteca",
                desc: "Adicione qualquer pasta e o Airmedy a digitaliza instantaneamente, mesmo com dezenas de milhares de faixas."
            },
            lyrics: {
                title: "Letras que acompanham",
                desc: "Letras sincronizadas rolam linha por linha enquanto a música toca. O melhor da audição imersiva."
            },
            eq: {
                title: "Equalizador de 10 bandas",
                desc: "Ajuste o som para seus fones de ouvido. Desempenho nativo no macOS, Windows e Linux."
            },
            search: {
                title: "Busca Rápida",
                desc: "Encontre qualquer faixa, álbum ou artista em milissegundos com nossa indexação ultrarrápida."
            },
            playlists: {
                title: "Playlists",
                desc: "Crie e gerencie playlists, importe e exporte-as, e navegue por gênero ou artista."
            },
            media: {
                title: "Teclas de Mídia e Tray",
                desc: "Controle a reprodução pelo teclado, tela de bloqueio ou menu da bandeja do sistema."
            },
            native: {
                title: "Desempenho Nativo",
                desc: "Sem Electron. Sem excessos. Experiência leve."
            },
            lastfm: {
                title: "Scrobbling Last.fm",
                desc: "Sincronize seu histórico de audição e faixas favoritas automaticamente com o Last.fm."
            },
            player: {
                title: "Mini e Tela Cheia",
                desc: "Alterne entre um belo modo de tela cheia imersivo e um mini reprodutor compacto."
            }
        },
        screenshots: {
            title: "Projetado para o Foco",
            library: {
                title: "Explorador de Biblioteca",
                desc: "Bela interface glass-morphic que destaca suas capas de álbuns e metadados."
            },
            lyrics: {
                title: "Letras Sincronizadas",
                desc: "Modo de tela cheia imersivo with letras que se movem com a música."
            },
            mini: { title: "Reprodutor Compacto", desc: "Um mini reprodutor minúsculo e versátil que fica em primeiro plano sem atrapalhar." },
            artist: { title: "Visão do Artista", desc: "Mergulhe fundo em seus artistas favoritos com discografia e biografias." }
        },
        faq: {
            title: "Perguntas Frequentes",
            formats: {
                q: "Quais formatos de áudio são suportados?",
                a: "O Airmedy suporta uma ampla variedade de formatos, incluindo MP3, AAC, M4A, FLAC, WAV, Ogg Vorbis, Opus, APE e até DSD. Usamos motores nativos no macOS e decodificação baseada em FFmpeg no Windows e Linux."
            },
            free: {
                q: "O Airmedy é gratuito?",
                a: "Sim! O Airmedy é totalmente gratuito e de código aberto sob a Licença MIT. Você pode encontrar o código-fonte no GitHub."
            },
            ffmpeg: {
                q: "Preciso instalar o FFmpeg?",
                a: "Não, as bibliotecas FFmpeg são compiladas estaticamente e incluídas no Airmedy. Você não precisa instalar mais nada."
            },
            privacy: {
                q: "Como meus dados são tratados?",
                a: "O Airmedy é construído com a privacidade em mente. Suas chaves de sessão do Last.fm são armazenadas com segurança nas chaves nativas do seu sistema (macOS Keychain, Windows Credential Manager ou Secret Service API no Linux). Nunca vemos sua senha, e nenhum dado sai da sua máquina, exceto para fazer scrobble de faixas para o Last.fm."
            }
        },
        download: {
            title: "Pronto para ouvir?",
            subtitle: "Baixe o Airmedy para sua plataforma e comece sua jornada.",
            windows: {
                label: "Windows",
                btn: "Em breve"
            },
            linux: {
                label: "Linux",
                btn: "Em breve"
            },
            macos: {
                label: "macOS",
                btn: "Baixar para macOS",
                silicon: "Apple Silicon",
                intel: "Chip Intel"
            },
            license: "Lançado sob a"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "Reportar um problema"
        }
    },
    ru: {
        nav: {
            features: "Функции",
            screenshots: "Скриншоты",
            faq: "Чаво",
            download: "Скачать"
        },
        hero: {
            title: "Ваша музыка, <br><span class=\"text-primary\">в новом свете.</span>",
            subtitle: "Универсальный офлайн-музыкальный плеер. Высокая производительность, энергоэффективность и прекрасный дизайн в стиле glass-morphism.",
            cta: "Скачать Airmedy",
            github: "Посмотреть на GitHub"
        },
        features: {
            title: "Все, что вам нужно",
            library: {
                title: "Вся ваша библиотека",
                desc: "Добавьте любую папку, и Airmedy мгновенно просканирует ее, даже если в ней десятки тысяч треков."
            },
            lyrics: {
                title: "Синхронные тексты",
                desc: "Синхронизированные тексты песен прокручиваются строка за строкой во время воспроизведения. Полное погружение в музыку."
            },
            eq: {
                title: "10-полосный эквалайзер",
                desc: "Настройте звук под свои наушники. Нативная производительность на macOS, Windows и Linux."
            },
            search: {
                title: "Быстрый поиск",
                desc: "Найдите любой трек, альбом или исполнителя за миллисекунды благодаря нашей сверхбыстрой индексации."
            },
            playlists: {
                title: "Плейлисты",
                desc: "Создавайте и управляйте плейлистами, импортируйте и экспортируйте их, просматривайте по жанрам или исполнителям."
            },
            media: {
                title: "Медиаклавиши и трей",
                desc: "Управляйте воспроизведением с помощью клавиатуры, экрана блокировки или меню в системном трее."
            },
            native: {
                title: "Нативная производительность",
                desc: "Никакого Electron. Ничего лишнего. Максимальная легкость."
            },
            lastfm: {
                title: "Скробблинг Last.fm",
                desc: "Автоматически синхронизируйте историю прослушиваний и любимые треки с Last.fm."
            },
            player: {
                title: "Мини и Полноэкранный",
                desc: "Переключайтесь между красивым иммерсивным полноэкранным режимом и компактным мини-плеером."
            }
        },
        screenshots: {
            title: "Создан для концентрации",
            library: {
                title: "Проводник библиотеки",
                desc: "Прекрасный glass-morphic интерфейс, который подчеркивает обложки альбомов и метаданные."
            },
            lyrics: {
                title: "Синхронизированные тексты",
                desc: "Иммерсивный полноэкранный режим с текстами, движущимися вместе с музыкой."
            },
            mini: { title: "Компактный плеер", desc: "Крошечный универсальный мини-плеер, который остается поверх всех окон и не мешает." },
            artist: { title: "Информация об артисте", desc: "Погрузитесь в творчество любимых артистов с дискографией и биографиями." }
        },
        faq: {
            title: "Часто задаваемые вопросы",
            formats: {
                q: "Какие аудиоформаты поддерживаются?",
                a: "Airmedy поддерживает широкий спектр форматов, включая MP3, AAC, M4A, FLAC, WAV, Ogg Vorbis, Opus, APE и даже DSD. Мы используем нативные движки на macOS и декодирование на базе FFmpeg на Windows и Linux."
            },
            free: {
                q: "Airmedy бесплатен?",
                a: "Да! Airmedy полностью бесплатен и имеет открытый исходный код под лицензией MIT. Исходный код можно найти на GitHub."
            },
            ffmpeg: {
                q: "Нужно ли устанавливать FFmpeg?",
                a: "Нет, библиотеки FFmpeg статически скомпилированы и включены в состав Airmedy. Вам не нужно ничего устанавливать дополнительно."
            },
            privacy: {
                q: "Как обрабатываются мои данные?",
                a: "Airmedy создан с учетом требований конфиденциальности. Ваши сеансовые ключи Last.fm надежно хранятся в нативной системе ключей (macOS Keychain, диспетчере учетных данных Windows или Secret Service API в Linux). Мы никогда не видим ваш пароль, и никакие данные не покидают ваш компьютер, за исключением скробблинга треков на Last.fm."
            }
        },
        download: {
            title: "Готовы слушать?",
            subtitle: "Загрузите Airmedy для своей платформы и начните свое путешествие.",
            windows: {
                label: "Windows",
                btn: "Скоро"
            },
            linux: {
                label: "Linux",
                btn: "Скоро"
            },
            macos: {
                label: "macOS",
                btn: "Скачать для macOS",
                silicon: "Apple Silicon",
                intel: "Процессор Intel"
            },
            license: "Выпущено под"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "Сообщить о проблеме"
        }
    },
    th: {
        nav: {
            features: "คุณสมบัติ",
            screenshots: "ภาพหน้าจอ",
            faq: "คำถามที่พบบ่อย",
            download: "ดาวน์โหลด"
        },
        hero: {
            title: "ดนตรีของคุณ <br><span class=\"text-primary\">ในแบบที่เหนือกว่า</span>",
            subtitle: "เครื่องเล่นเพลงออฟไลน์แบบครบวงจร ประสิทธิภาพสูง ประหยัดแบตเตอรี่ และออกแบบมาอย่างสวยงามด้วยสไตล์ Glass-morphism",
            cta: "รับ Airmedy",
            github: "ดูบน GitHub"
        },
        features: {
            title: "ทุกสิ่งที่คุณต้องการ",
            library: {
                title: "คลังเพลงทั้งหมดของคุณ",
                desc: "เพิ่มโฟลเดอร์ใดก็ได้ แล้ว Airmedy จะสแกนทันที แม้จะมีเพลงหลายหมื่นเพลงก็ตาม"
            },
            lyrics: {
                title: "เนื้อเพลงที่เลื่อนตามเพลง",
                desc: "เนื้อเพลงที่ซิงค์จะเลื่อนบรรทัดต่อบรรทัดขณะเล่นเพลง มอบประสบการณ์การฟังที่ดื่มด่ำที่สุด"
            },
            eq: {
                title: "อีควอไลเซอร์ 10 แบนด์",
                desc: "ปรับแต่งเสียงให้เข้ากับหูฟังของคุณ ประสิทธิภาพระดับเนทีฟบน macOS, Windows และ Linux"
            },
            search: {
                title: "ค้นหาอย่างรวดเร็ว",
                desc: "ค้นหาเพลง อัลบั้ม หรือศิลปินได้ในเสี้ยววินาทีด้วยการทำดัชนีที่รวดเร็วปานสายฟ้า"
            },
            playlists: {
                title: "เพลย์ลิสต์",
                desc: "สร้างและจัดการเพลย์ลิสต์ นำเข้าและส่งออก และเลือกดูตามประเภทเพลงหรือศิลปิน"
            },
            media: {
                title: "ปุ่มสื่อและถาดระบบ",
                desc: "ควบคุมการเล่นผ่านคีย์บอร์ด หน้าจอล็อก หรือเมนูถาดระบบ"
            },
            native: {
                title: "ประสิทธิภาพระดับเนทีฟ",
                desc: "ไม่มี Electron ไม่มีส่วนเกิน เพื่อประสบการณ์ที่เบาสบาย"
            },
            lastfm: {
                title: "การบันทึกประวัติ Last.fm",
                desc: "ซิงค์ประวัติการฟังและเพลงที่ชื่นชอบกับ Last.fm โดยอัตโนมัติ"
            },
            player: {
                title: "มินิเพลเยอร์และเต็มจอ",
                desc: "สลับระหว่างโหมดเต็มจอที่สวยงามดื่มด่ำ และมินิเพลเยอร์ขนาดกะทัดรัดที่ไม่รบกวนการทำงาน"
            }
        },
        screenshots: {
            title: "ออกแบบมาเพื่อการจดจ่อ",
            library: {
                title: "ตัวสำรวจคลังเพลง",
                desc: "อินเทอร์เฟซสไตล์ Glass-morphism ที่สวยงาม ซึ่งเน้นภาพหน้าปกอัลบั้มและข้อมูลเมตาของคุณ"
            },
            lyrics: {
                title: "เนื้อเพลงที่ซิงค์",
                desc: "โหมดเต็มจอที่ดื่มด่ำพร้อมเนื้อเพลงที่เคลื่อนไหวไปตามเสียงเพลง"
            },
            mini: { title: "เครื่องเล่นขนาดกะทัดรัด", desc: "มินิเพลเยอร์ขนาดเล็กอเนกประสงค์ที่อยู่บนสุดและไม่รบกวนคุณ" },
            artist: { title: "ข้อมูลเชิงลึกของศิลปิน", desc: "เจาะลึกศิลปินที่คุณชื่นชอบด้วยผลงานเพลงและประวัติ" }
        },
        faq: {
            title: "คำถามที่พบบ่อย",
            formats: {
                q: "รองรับไฟล์เสียงรูปแบบใดบ้าง?",
                a: "Airmedy รองรับรูปแบบที่หลากหลายรวมถึง MP3, AAC, M4A, FLAC, WAV, Ogg Vorbis, Opus, APE และแม้แต่ DSD เราใช้เอ็นจิ้นเนทีฟบน macOS และการถอดรหัสผ่าน FFmpeg บน Windows และ Linux"
            },
            free: {
                q: "Airmedy ฟรีหรือไม่?",
                a: "ใช่! Airmedy ฟรีทั้งหมดและเป็นโอเพนซอร์ซภายใต้ใบอนุญาต MIT คุณสามารถดูซอร์สโค้ดได้บน GitHub"
            },
            ffmpeg: {
                q: "ฉันจำเป็นต้องติดตั้ง FFmpeg หรือไม่?",
                a: "ไม่จำเป็น ไลบรารี FFmpeg ถูกคอมไพล์แบบสแตติกและรวมไว้ใน Airmedy แล้ว คุณไม่จำเป็นต้องติดตั้งอะไรเพิ่มเติม"
            },
            privacy: {
                q: "ข้อมูลของฉันได้รับการจัดการอย่างไร?",
                a: "Airmedy สร้างขึ้นโดยคำนึงถึงความเป็นส่วนตัว คีย์เซสชัน Last.fm ของคุณจะถูกจัดเก็บอย่างปลอดภัยในพวงกุญแจดั้งเดิมของระบบ (macOS Keychain, Windows Credential Manager หรือ Secret Service API บน Linux) เราไม่เคยเห็นรหัสผ่านของคุณ และไม่มีข้อมูลใดออกจากเครื่องของคุณยกเว้นเพื่อสคร็อบเบิลเพลงไปยัง Last.fm"
            }
        },
        download: {
            title: "พร้อมที่จะฟังหรือยัง?",
            subtitle: "ดาวน์โหลด Airmedy สำหรับแพลตฟอร์มของคุณและเริ่มการเดินทางของคุณ",
            windows: {
                label: "Windows",
                btn: "เร็วๆ นี้"
            },
            linux: {
                label: "Linux",
                btn: "เร็วๆ นี้"
            },
            macos: {
                label: "macOS",
                btn: "ดาวน์โหลดสำหรับ macOS",
                silicon: "Apple Silicon",
                intel: "ชิป Intel"
            },
            license: "เผยแพร่ภายใต้"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "รายงานปัญหา"
        }
    },
    vi: {
        nav: {
            features: "Tính năng",
            screenshots: "Ảnh chụp",
            faq: "Hỏi đáp",
            download: "Tải về"
        },
        hero: {
            title: "Âm nhạc của bạn, <br><span class=\"text-primary\">tinh tế hơn.</span>",
            subtitle: "Trình phát nhạc ngoại tuyến tất cả trong một. Hiệu suất cao, tiết kiệm pin và thiết kế glass-morphism đẹp mắt.",
            cta: "Tải Airmedy",
            github: "Xem trên GitHub"
        },
        features: {
            title: "Mọi thứ bạn cần",
            library: {
                title: "Toàn bộ thư viện của bạn",
                desc: "Thêm bất kỳ thư mục nào và Airmedy sẽ quét ngay lập tức, ngay cả với hàng chục nghìn bài hát."
            },
            lyrics: {
                title: "Lời bài hát chạy theo nhạc",
                desc: "Lời bài hát được đồng bộ sẽ cuộn từng dòng khi bài hát phát. Trải nghiệm nghe nhạc đắm chìm nhất."
            },
            eq: {
                title: "Bộ cân bằng 10 băng tần",
                desc: "Điều chỉnh âm thanh cho tai nghe của bạn. Hiệu suất gốc trên macOS, Windows và Linux."
            },
            search: {
                title: "Tìm kiếm nhanh",
                desc: "Tìm bất kỳ bài hát, album hoặc nghệ sĩ nào trong tích tắc với hệ thống lập chỉ mục cực nhanh."
            },
            playlists: {
                title: "Danh sách phát",
                desc: "Tạo và quản lý danh sách phát, nhập và xuất, và duyệt theo thể loại hoặc nghệ sĩ."
            },
            media: {
                title: "Phím media & Khay hệ thống",
                desc: "Điều khiển phát nhạc qua bàn phím, màn hình khóa hoặc menu khay hệ thống."
            },
            native: {
                title: "Hiệu suất gốc",
                desc: "Không Electron. Không cồng kềnh. Trải nghiệm nhẹ nhàng."
            },
            lastfm: {
                title: "Scrobbling Last.fm",
                desc: "Tự động đồng bộ lịch sử nghe nhạc và các bài hát yêu thích của bạn với Last.fm."
            },
            player: {
                title: "Mini & Toàn màn hình",
                desc: "Chuyển đổi giữa chế độ toàn màn hình đẹp mắt và trình phát mini nhỏ gọn không gây gián đoạn."
            }
        },
        screenshots: {
            title: "Thiết kế cho sự tập trung",
            library: {
                title: "Khám phá thư viện",
                desc: "Giao diện glass-morphism đẹp mắt làm nổi bật ảnh bìa album và siêu dữ liệu của bạn."
            },
            lyrics: {
                title: "Lời bài hát đồng bộ",
                desc: "Chế độ toàn màn hình đắm chìm với lời bài hát chuyển động theo nhạc."
            },
            mini: { title: "Trình phát nhỏ gọn", desc: "Một trình phát mini đa năng, nhỏ bé luôn ở trên cùng và không cản trở bạn." },
            artist: { title: "Thông tin nghệ sĩ", desc: "Tìm hiểu sâu về các nghệ sĩ yêu thích của bạn với danh sách đĩa nhạc và tiểu sử." }
        },
        faq: {
            title: "Câu hỏi thường gặp",
            formats: {
                q: "Những định dạng âm thanh nào được hỗ trợ?",
                a: "Airmedy hỗ trợ nhiều định dạng bao gồm MP3, AAC, M4A, FLAC, WAV, Ogg Vorbis, Opus, APE và cả DSD. Chúng tôi sử dụng các công cụ gốc trên macOS và giải mã dựa trên FFmpeg trên Windows và Linux."
            },
            free: {
                q: "Airmedy có miễn phí không?",
                a: "Có! Airmedy hoàn toàn miễn phí và mã nguồn mở theo Giấy phép MIT. Bạn có thể tìm thấy mã nguồn trên GitHub."
            },
            ffmpeg: {
                q: "Tôi có cần cài đặt FFmpeg không?",
                a: "Không, các thư viện FFmpeg được biên dịch tĩnh và đi kèm bên trong Airmedy. Bạn không cần cài đặt thêm bất cứ thứ gì."
            },
            privacy: {
                q: "Dữ liệu của tôi được xử lý như thế nào?",
                a: "Airmedy được xây dựng với ưu tiên về quyền riêng tư. Khóa phiên Last.fm của bạn được lưu trữ an toàn trong chuỗi khóa gốc của hệ thống (macOS Keychain, Windows Credential Manager hoặc Secret Service API trên Linux). Chúng tôi không bao giờ thấy mật khẩu của bạn và không có dữ liệu nào rời khỏi máy của bạn ngoại trừ việc scrobble các bài hát lên Last.fm."
            }
        },
        download: {
            title: "Sẵn sàng để thưởng thức?",
            subtitle: "Tải xuống Airmedy cho nền tảng của bạn và bắt đầu hành trình.",
            windows: {
                label: "Windows",
                btn: "Sắp ra mắt"
            },
            linux: {
                label: "Linux",
                btn: "Sắp ra mắt"
            },
            macos: {
                label: "macOS",
                btn: "Tải xuống cho macOS",
                silicon: "Apple Silicon",
                intel: "Chip Intel"
            },
            license: "Phát hành theo"
        },
        footer: {
            copy: "&copy; 2026 misa198",
            github: "GitHub",
            issues: "Báo lỗi"
        }
    },
    zh: {
        nav: {
            features: "功能",
            screenshots: "截图",
            faq: "常见问题",
            download: "下载"
        },
        hero: {
            title: "您的音乐，<br><span class=\"text-primary\">更加精致。</span>",
            subtitle: "全能离线音乐播放器。高性能、省电，并采用精美的玻璃拟态设计。",
            cta: "获取 Airmedy",
            github: "在 GitHub 上查看"
        },
        features: {
            title: "您所需的一切",
            library: {
                title: "您的整个音乐库",
                desc: "添加任何文件夹，Airmedy 都会立即扫描，即使有数万条曲目也不在话下。"
            },
            lyrics: {
                title: "同步歌词",
                desc: "同步歌词随音乐逐行滚动。为您提供极致的沉浸式听歌体验。"
            },
            eq: {
                title: "10 段均衡器",
                desc: "为您的耳机调节音质。在 macOS、Windows 和 Linux 上均拥有原生性能。"
            },
            search: {
                title: "快速搜索",
                desc: "利用我们闪电般的索引技术，在毫秒内找到任何曲目、专辑或艺术家。"
            },
            playlists: {
                title: "播放列表",
                desc: "创建和管理播放列表，支持导入和导出，并可按流派或艺术家浏览。"
            },
            media: {
                title: "媒体键与托盘",
                desc: "通过键盘、锁定屏幕或系统托盘菜单控制播放。"
            },
            native: {
                title: "原生性能",
                desc: "无 Electron。无臃肿。轻量级体验。"
            },
            lastfm: {
                title: "Last.fm 记录",
                desc: "自动将您的收听历史和喜欢的曲目同步到 Last.fm。"
            },
            player: {
                title: "迷你与全屏",
                desc: "在精美的沉浸式全屏模式和不占空间的迷你播放器之间自由切换。"
            }
        },
        screenshots: {
            title: "为专注而设计",
            library: {
                title: "音乐库浏览器",
                desc: "精美的玻璃拟态界面，凸显您的专辑封面和元数据。"
            },
            lyrics: {
                title: "同步歌词",
                desc: "沉浸式全屏模式，歌词随音乐律动。"
            },
            mini: { title: "紧凑型播放器", desc: "小巧多功能的迷你播放器，置于顶层且不碍事。" },
            artist: { title: "艺术家洞察", desc: "通过作品集和传记深入了解您最喜爱的艺术家。" }
        },
        faq: {
            title: "常见问题",
            formats: {
                q: "支持哪些音频格式？",
                a: "Airmedy 支持广泛的格式，包括 MP3、AAC、M4A、FLAC, WAV, Ogg Vorbis, Opus, APE 甚至 DSD。我们在 macOS 上使用原生引擎，在 Windows 和 Linux 上使用基于 FFmpeg 的解码。"
            },
            free: {
                q: "Airmedy 免费吗？",
                a: "是的！Airmedy 完全免费，并根据 MIT 许可证开源。您可以在 GitHub 上找到源代码。"
            },
            ffmpeg: {
                q: "我需要安装 FFmpeg 吗？",
                a: "不需要，FFmpeg 库已静态编译并打包在 Airmedy 中。您无需安装任何其他软件。"
            },
            privacy: {
                q: "我的数据如何处理？",
                a: "Airmedy 的构建考虑到了隐私。您的 Last.fm 会话密钥安全地存储在您系统的原生钥匙串中（macOS 的钥匙串，Windows 的凭据管理器，或 Linux 的 Secret Service API）。我们永远不会看到您的密码，除了向 Last.fm 记录（scrobble）曲目外，没有数据会离开您的机器。"
            }
        },
        download: {
            title: "准备好听音乐了吗？",
            subtitle: "下载适用于您平台的 Airmedy，开启您的音乐之旅。",
            windows: {
                label: "Windows",
                btn: "敬请期待"
            },
            linux: {
                label: "Linux",
                btn: "敬请期待"
            },
            macos: {
                label: "macOS",
                btn: "下载 macOS 版",
                silicon: "Apple Silicon",
                intel: "Intel 芯片"
            },
            license: "发布协议"
        },
        footer: {
            copy: "&copy; 2026 misa198。",
            github: "GitHub",
            issues: "报告问题"
        }
    }
};
