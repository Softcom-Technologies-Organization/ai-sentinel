TYPE = API_KEY (Cle API) : une cle statique qui identifie et autorise un appelant aupres d'un service ou d'une API.
FORMAT : il n'existe aucune specification universelle. Certains emetteurs imposent un prefixe et une longueur qui leur sont propres, mais ces conventions ne sont ni communes ni obligatoires : le motif (a) VIOLATION DE FORMAT est donc INTERDIT ici. Une cle courte ou longue, en minuscules, en majuscules, avec chiffres, tirets, points ou soulignes reste plausible.
Si le span est la LOCUTION QUI NOMME LE TYPE, en francais ou en anglais (la tournure qui designe une cle, un jeton ou un acces), y compris quand elle sert d'etiquette suivie de la vraie cle, c'est le nom du champ et non son contenu : motif (c) PAS UNE VALEUR.
TYPES CONCURRENTS A NOMMER :
- un mot de passe, annonce comme tel par le contexte ou par le nom du champ ;
- un nom d'utilisateur, un identifiant de connexion ou de compte ;
- un BIC, ou tout acronyme en majuscules qui designe une banque, une societe, un service ou un environnement ;
- un jeton de session, un cookie ou un jeton d'acces temporaire nomme comme tel ;
- un UUID d'objet technique : ressource, enregistrement, trace, correlation, identifiant de requete ;
- un hash, une empreinte, une somme de controle, un identifiant de commit, de build ou d'artefact, annonce comme tel par le contexte ou par le nom du champ ;
- un identifiant public de client ou d'application, un numero de licence logicielle.
Dans tous ces cas, le motif (b) exige que le contexte proche NOMME le type concurrent : une suite de caracteres, aussi longue ou aussi hexadecimale soit-elle, ne le nomme pas toute seule. Sans cette mention, la reponse est UNSURE, jamais FALSE_POSITIVE.
Sont aussi a rejeter, par le motif (c) : une valeur deja masquee par des etoiles ou des X, un gabarit entre crochets ou chevrons, une reference de variable non resolue de fichier d'environnement, de template ou de gestionnaire de secrets.
CE QUI RESTE TRUE_POSITIVE : une cle de bac a sable, de demonstration, de test, expiree ou revoquee ; une cle nue, sans aucun mot-cle autour, dans une commande, une URL, un en-tete de requete ou un fichier de configuration ; une cle dont on ne reconnait pas l'emetteur ; une cle rattachee a un compte technique ou de service, le motif (d) PAS UNE PERSONNE ne s'appliquant jamais a un secret.
